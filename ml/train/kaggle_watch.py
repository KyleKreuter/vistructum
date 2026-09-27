import argparse
import json
import os
import re
import shlex
import shutil
import subprocess
import sys
import tempfile
import time
from datetime import datetime
from pathlib import Path

TERMINAL = ("complete", "error", "cancel")
PUBLISH_PATTERNS = ("*.log", "watch-report.txt", "release/*.json", "release/*.onnx", "runs/*.log", "runs/*/*.log",
                    "runs/*/manifest.json", "runs/*/calib.json", "runs/*/scan.json", "runs/*/scan-holdout.json")
MAX_UNKNOWN = 5
SCAN_BRANCH = "scan-runs"
SCAN_WORKFLOW = "scan-stage.yml"


def log(message):
    print(f"[{time.strftime('%H:%M:%S')}] {message}", flush=True)


def kaggle(cmd, *args):
    return subprocess.run(shlex.split(cmd) + list(args), capture_output=True, text=True, check=False)


def status(cmd, slug):
    result = kaggle(cmd, "kernels", "status", slug)
    text = (result.stdout + result.stderr).strip()
    match = re.search(r'has status "(?:KernelWorkerStatus\.)?(queued|running|complete|error|cancel\w*)"', text, re.IGNORECASE)
    return (match.group(1).lower() if match else "unknown"), text


def download(cmd, slug, out_dir):
    out_dir.mkdir(parents=True, exist_ok=True)
    result = kaggle(cmd, "kernels", "output", slug, "-p", str(out_dir), "--force")
    if result.returncode != 0:
        log(f"download failed: {(result.stdout + result.stderr).strip()[-500:]}")
    return result.returncode == 0


def summarize(out_dir):
    summary_path = out_dir / "release" / "summary.json"
    lines = []
    if not summary_path.is_file():
        lines.append("no release/summary.json - the kernel failed before the end, see the kernel log")
    else:
        summary = json.loads(summary_path.read_text())
        lines.append(f"ref {summary['ref']}  exit codes {summary['exit_codes']}")
    for manifest_path in sorted((out_dir / "release").glob("*-manifest.json")):
        manifest = json.loads(manifest_path.read_text())
        for name, entry in manifest.get("scan", {}).items():
            row = entry.get("calibrated") or entry.get("at_model_threshold") or {}
            lines.append(f"{manifest['config']:10s} {name:13s} {entry.get('verdict') or 'calibrated':10s} "
                         f"t={row.get('threshold')} votes={row.get('min_votes')} recall={row.get('recall')} "
                         f"false_flags={row.get('false_flags')}/{row.get('windows')} {entry.get('failures') or ''}")
    for log_path in sorted(out_dir.glob("runs/*.log")) + sorted(out_dir.glob("runs/*/*.log")) + sorted(out_dir.glob("*.log")):
        text = log_path.read_text(errors="replace")
        if "Traceback" in text:
            lines.append(f"TRACEBACK in {log_path.relative_to(out_dir)}:")
            lines += ["  " + line for line in text[text.rindex("Traceback"):].splitlines()[-15:]]
    return "\n".join(lines)


def repo_root():
    return Path(subprocess.run(["git", "rev-parse", "--show-toplevel"], capture_output=True, text=True,
                               check=True).stdout.strip())


def pending_runs(out_dir):
    return [path.parent for path in sorted(out_dir.glob("runs/*/manifest.json"))
            if json.loads(path.read_text()).get("scan_pending")]


def scan_locally(out_dir, pending, ref):
    repo = repo_root()
    work = Path(tempfile.mkdtemp(prefix="vistructum-scan-"))
    shutil.rmtree(work)
    subprocess.run(["git", "fetch", "-q", "origin"], cwd=repo, check=False)
    subprocess.run(["git", "worktree", "add", "-q", "--detach", str(work), ref], cwd=repo, check=True)
    try:
        train_dir = work / "ml" / "train"
        env = dict(os.environ, PYTHONPATH=f"{work / 'ml'}:{train_dir}")
        for run_dir in pending:
            log(f"scan stage for {run_dir.name} at {ref[:8]}")
            with open(run_dir / "scan-stage.log", "w") as stage_log:
                code = subprocess.run([sys.executable, "scan_stage.py", "all", str(run_dir.resolve())], cwd=train_dir,
                                      env=env, stdout=stage_log, stderr=subprocess.STDOUT, check=False).returncode
            log(f"scan stage for {run_dir.name} exited {code}")
    finally:
        subprocess.run(["git", "worktree", "remove", "--force", str(work)], cwd=repo, check=False)


def gh(*args):
    return subprocess.run(["gh", *args], capture_output=True, text=True, check=True).stdout.strip()


def dispatch(run_path, shards, ref):
    started = time.time()
    gh("workflow", "run", SCAN_WORKFLOW, "--ref", ref, "-f", f"run={run_path}", "-f", f"shards={shards}")
    title = f"scan stage {run_path}"
    for _ in range(30):
        time.sleep(10)
        runs = json.loads(gh("run", "list", "--workflow", SCAN_WORKFLOW, "--limit", "20", "--json",
                             "databaseId,displayTitle,createdAt"))
        for run in runs:
            created = datetime.fromisoformat(run["createdAt"]).timestamp()
            if run["displayTitle"] == title and created >= started - 60:
                return run["databaseId"]
    raise RuntimeError(f"no {SCAN_WORKFLOW} run showed up for {run_path}")


def wait_for_run(run_id, interval, deadline):
    while True:
        state = json.loads(gh("run", "view", str(run_id), "--json", "status,conclusion"))
        if state["status"] == "completed":
            return state["conclusion"]
        if time.time() > deadline:
            return "timed_out"
        time.sleep(interval)


def scan_on_actions(out_dir, pending, label, shards, ref, interval, deadline):
    files = {f"{label}/{run_dir.name}/{path.name}": path for run_dir in pending
             for path in [run_dir / "manifest.json", *run_dir.glob("*.onnx")]}
    push_files(SCAN_BRANCH, files, f"scan input {label}")
    for run_dir in pending:
        run_id = dispatch(f"{label}/{run_dir.name}", shards, ref)
        log(f"scan stage for {run_dir.name} on GitHub Actions run {run_id}")
        conclusion = wait_for_run(run_id, interval, deadline)
        log(f"scan stage for {run_dir.name} ended {conclusion}")
        if conclusion != "success":
            continue
        result = Path(tempfile.mkdtemp(prefix="vistructum-scan-result-"))
        gh("run", "download", str(run_id), "-n", "scan-result", "-D", str(result))
        for path in result.iterdir():
            shutil.copy(path, run_dir / path.name)
        shutil.rmtree(result)


def run_scan_stage(out_dir, where, label, shards, ref, interval, deadline):
    summary_path = out_dir / "release" / "summary.json"
    pending = pending_runs(out_dir)
    if not pending or not summary_path.is_file():
        return
    summary = json.loads(summary_path.read_text())
    if where == "local":
        scan_locally(out_dir, pending, ref or summary["ref"])
    else:
        scan_on_actions(out_dir, pending, label, shards, ref or "main", interval, deadline)
    from bootstrap import collect

    summary["models"] = collect(out_dir / "runs", out_dir / "release")
    summary_path.write_text(json.dumps(summary, indent=2))


def push_files(branch, files, message):
    repo = repo_root()
    exists = subprocess.run(["git", "ls-remote", "--exit-code", "origin", branch], cwd=repo,
                            capture_output=True, check=False).returncode == 0
    work = Path(tempfile.mkdtemp(prefix="vistructum-results-"))
    shutil.rmtree(work)
    if exists:
        subprocess.run(["git", "fetch", "-q", "origin", branch], cwd=repo, check=True)
        subprocess.run(["git", "worktree", "add", "-q", "-B", branch, str(work), f"origin/{branch}"], cwd=repo,
                       check=True)
    else:
        subprocess.run(["git", "worktree", "add", "-q", "--detach", str(work)], cwd=repo, check=True)
        subprocess.run(["git", "checkout", "-q", "--orphan", branch], cwd=work, check=True)
        subprocess.run(["git", "rm", "-rq", "--cached", "."], cwd=work, check=True)
        subprocess.run(["git", "clean", "-fdxq"], cwd=work, check=True)
    try:
        for name, path in files.items():
            destination = work / name
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy(path, destination)
        subprocess.run(["git", "add", "-f", *files], cwd=work, check=True)
        subprocess.run(["git", "commit", "-qm", message], cwd=work, check=True)
        subprocess.run(["git", "push", "-q", "origin", f"{branch}:{branch}"], cwd=work, check=True)
        log(f"pushed {len(files)} files to origin/{branch}")
    finally:
        subprocess.run(["git", "worktree", "remove", "--force", str(work)], cwd=repo, check=False)


def publish(out_dir, branch, label):
    files = {f"{label}/{path.relative_to(out_dir)}": path for pattern in PUBLISH_PATTERNS
             for path in out_dir.glob(pattern)}
    push_files(branch, files, f"kaggle results {label}")


def main():
    parser = argparse.ArgumentParser(description="watch a Kaggle kernel, pull its output and logs when it ends")
    parser.add_argument("slug", help="owner/kernel-slug, e.g. kylekreuter/vistructum-train")
    parser.add_argument("--out", default="results", help="local directory for downloaded output")
    parser.add_argument("--interval", type=int, default=120, help="seconds between status polls")
    parser.add_argument("--max-hours", type=float, default=8.0, help="give up after this long")
    parser.add_argument("--publish", default="", help="push logs/manifests/onnx to this git branch, e.g. "
                                                      "chore/kaggle-results")
    parser.add_argument("--kaggle-cmd", default=f"{sys.executable} -m kaggle")
    parser.add_argument("--scan", choices=("actions", "local"), default=None,
                        help="run the scan stage for runs the kernel left pending: sharded on GitHub Actions "
                             f"({SCAN_WORKFLOW}, input pushed to the {SCAN_BRANCH} branch) or here")
    parser.add_argument("--scan-shards", type=int, default=16, help="parallel GitHub Actions jobs per scan phase")
    parser.add_argument("--scan-ref", default="", help="git ref whose scan code runs; default main on GitHub Actions, "
                                                       "the training commit locally")
    args = parser.parse_args()
    started = time.time()
    last = None
    unknown = 0
    while True:
        state, text = status(args.kaggle_cmd, args.slug)
        if state != last:
            log(f"{args.slug}: {state} ({text[-160:]})")
            last = state
        unknown = unknown + 1 if state == "unknown" else 0
        if unknown >= MAX_UNKNOWN:
            log(f"status unreadable {MAX_UNKNOWN} times in a row (credentials? slug?): {text[-300:]}")
            return 3
        if state in TERMINAL or state.startswith("cancel"):
            break
        if time.time() - started > args.max_hours * 3600:
            log(f"still {state} after {args.max_hours} h, giving up")
            return 2
        time.sleep(args.interval)
    label = time.strftime("%Y%m%d-%H%M%S")
    out_dir = Path(args.out) / label
    download(args.kaggle_cmd, args.slug, out_dir)
    summary_path = out_dir / "release" / "summary.json"
    if summary_path.is_file():
        ref = json.loads(summary_path.read_text()).get("ref", "")[:8]
        renamed = out_dir.with_name(f"{label}-{ref}")
        out_dir.rename(renamed)
        out_dir, label = renamed, renamed.name
    if args.scan:
        run_scan_stage(out_dir, args.scan, label, args.scan_shards, args.scan_ref, args.interval,
                       time.time() + args.max_hours * 3600)
    report = summarize(out_dir)
    (out_dir / "watch-report.txt").write_text(report + "\n")
    print(report, flush=True)
    if args.publish:
        publish(out_dir, args.publish, label)
    return 0 if state == "complete" else 1


if __name__ == "__main__":
    sys.exit(main())
