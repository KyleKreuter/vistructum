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
from pathlib import Path

from bootstrap import collect

TERMINAL = ("complete", "error", "cancel")
MAX_UNKNOWN = 5
QUEUE_TIMEOUT = 3
SCAN_BRANCH = "scan-runs"
RESULTS_BRANCH = "training-results"
PUBLISH_PATTERNS = ("*.log", "report.md", "release/*.json", "release/*.onnx", "runs/*.log", "runs/*/*.log",
                    "runs/*/manifest.json", "runs/*/calib.json", "runs/*/scan.json", "runs/*/scan-holdout.json")
SPLITS = ("test", "holdout", "findings")
SCAN_SPLITS = ("calib", "scan", "scan-holdout")


def log(message):
    print(f"[{time.strftime('%H:%M:%S')}] {message}", flush=True)


def kaggle(cmd, *args):
    return subprocess.run(shlex.split(cmd) + list(args), capture_output=True, text=True, check=False)


def status(cmd, slug):
    result = kaggle(cmd, "kernels", "status", slug)
    text = (result.stdout + result.stderr).strip()
    match = re.search(r'has status "(?:KernelWorkerStatus\.)?(queued|running|complete|error|cancel\w*)"', text, re.IGNORECASE)
    return (match.group(1).lower() if match else "unknown"), text


def wait(cmd, slug, interval, max_hours, queue_minutes=None):
    started = time.time()
    last = None
    unknown = 0
    while True:
        state, text = status(cmd, slug)
        if state != last:
            log(f"{slug}: {state} ({text[-160:]})")
            last = state
        unknown = unknown + 1 if state == "unknown" else 0
        if unknown >= MAX_UNKNOWN:
            log(f"status unreadable {MAX_UNKNOWN} times in a row (credentials? slug?): {text[-300:]}")
            return "unknown"
        if state in TERMINAL or state.startswith("cancel"):
            return state
        if queue_minutes is not None and state == "queued" and time.time() - started > queue_minutes * 60:
            log(f"still queued after {queue_minutes} min, giving up on Kaggle")
            return "queue-timeout"
        if time.time() - started > max_hours * 3600:
            log(f"still {state} after {max_hours} h, giving up")
            return "timeout"
        time.sleep(interval)


def download(cmd, slug, out_dir):
    out_dir.mkdir(parents=True, exist_ok=True)
    result = kaggle(cmd, "kernels", "output", slug, "-p", str(out_dir), "--force")
    if result.returncode != 0:
        log(f"download failed: {(result.stdout + result.stderr).strip()[-500:]}")
    return result.returncode == 0


def pending_runs(out_dir):
    return [path.parent for path in sorted(out_dir.glob("runs/*/manifest.json"))
            if json.loads(path.read_text()).get("scan_pending")]


def push_files(branch, files, message):
    repo = Path(subprocess.run(["git", "rev-parse", "--show-toplevel"], capture_output=True, text=True,
                               check=True).stdout.strip())
    exists = subprocess.run(["git", "ls-remote", "--exit-code", "origin", branch], cwd=repo,
                            capture_output=True, check=False).returncode == 0
    work = Path(tempfile.mkdtemp(prefix="vistructum-branch-"))
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


def github_output(**values):
    target = os.environ.get("GITHUB_OUTPUT")
    lines = [f"{key}={value}" for key, value in values.items()]
    if target:
        with open(target, "a") as output:
            output.write("\n".join(lines) + "\n")
    print("\n".join(lines), flush=True)


def label_for(out_dir):
    summary_path = out_dir / "release" / "summary.json"
    ref = json.loads(summary_path.read_text()).get("ref", "")[:8] if summary_path.is_file() else "failed"
    return f"{time.strftime('%Y%m%d-%H%M%S')}-{ref}"


def handoff(out_dir, state):
    label = label_for(out_dir)
    pending = pending_runs(out_dir)
    if pending:
        files = {f"{label}/{run_dir.name}/{path.name}": path for run_dir in pending
                 for path in [run_dir / "manifest.json", *run_dir.glob("*.onnx")]}
        push_files(SCAN_BRANCH, files, f"scan input {label}")
    github_output(label=label, state=state, runs=json.dumps([f"{label}/{run_dir.name}" for run_dir in pending]))
    return 0 if state == "complete" else 1


def run_watch(args):
    state = wait(args.kaggle_cmd, args.slug, args.interval, args.max_hours, args.queue_minutes)
    if state == "queue-timeout":
        return QUEUE_TIMEOUT
    if state in ("unknown", "timeout"):
        return 2
    download(args.kaggle_cmd, args.slug, args.out)
    return handoff(args.out, state)


def run_handoff(args):
    return handoff(args.out, "complete" if (args.out / "release" / "summary.json").is_file() else "error")


def number(value):
    return "–" if value is None else f"{value:.4g}" if isinstance(value, float) else str(value)


def report(out_dir, label):
    lines = [f"## Training {label}", ""]
    summary_path = out_dir / "release" / "summary.json"
    if not summary_path.is_file():
        lines.append("No `release/summary.json`: training failed before the end, see its log.")
    else:
        summary = json.loads(summary_path.read_text())
        lines += [f"Commit `{summary['ref']}`, exit codes `{json.dumps(summary['exit_codes'])}`", "",
                  "| Model | Split | Verdict | Threshold | Precision | Recall | FP rate | Failures |",
                  "|---|---|---|---|---|---|---|---|"]
        for name, splits in summary.get("models", {}).items():
            for split in SPLITS:
                if split in splits:
                    row = splits[split]
                    lines.append(f"| {name} | {split} | {row['verdict']} | {number(row['threshold'])} | "
                                 f"{number(row['precision'])} | {number(row['recall'])} | {number(row['fp_rate'])} | "
                                 f"{'; '.join(row['failures'] or [])} |")
    manifests = sorted(out_dir.glob("runs/*/manifest.json"))
    if manifests:
        lines += ["", "| Run | Scan split | Verdict | Threshold | Votes | Recall | False flags | Failures |",
                  "|---|---|---|---|---|---|---|---|"]
    for manifest_path in manifests:
        manifest = json.loads(manifest_path.read_text())
        if manifest.get("scan_pending"):
            lines.append(f"| {manifest_path.parent.name} | – | scan stage missing | | | | | |")
            continue
        for name in SCAN_SPLITS:
            entry = manifest.get("scan", {}).get(name, {})
            row = entry.get("calibrated") or entry.get("at_model_threshold") or {}
            lines.append(f"| {manifest_path.parent.name} | {name} | {entry.get('verdict') or 'calibrated'} | "
                         f"{number(row.get('threshold'))} | {number(row.get('min_votes'))} | "
                         f"{number(row.get('recall'))} | {row.get('false_flags')}/{row.get('windows')} | "
                         f"{'; '.join(entry.get('failures') or [])} |")
    for log_path in sorted(out_dir.glob("runs/*.log")) + sorted(out_dir.glob("runs/*/*.log")) + sorted(out_dir.glob("*.log")):
        text = log_path.read_text(errors="replace")
        if "Traceback" in text:
            lines += ["", f"Traceback in `{log_path.relative_to(out_dir)}`:", "```",
                      *text[text.rindex("Traceback"):].splitlines()[-15:], "```"]
    return "\n".join(lines) + "\n"


def run_report(args):
    for result in sorted(args.scan_results.glob("*")) if args.scan_results else []:
        run_dir = args.out / "runs" / result.name.removeprefix("result-")
        if run_dir.is_dir():
            for path in result.iterdir():
                shutil.copy(path, run_dir / path.name)
    summary_path = args.out / "release" / "summary.json"
    if summary_path.is_file():
        summary = json.loads(summary_path.read_text())
        summary["models"] = collect(args.out / "runs", args.out / "release")
        summary_path.write_text(json.dumps(summary, indent=2))
    text = report(args.out, args.label)
    (args.out / "report.md").write_text(text)
    target = os.environ.get("GITHUB_STEP_SUMMARY")
    if target:
        with open(target, "a") as step_summary:
            step_summary.write(text)
    print(text, flush=True)
    if args.publish:
        files = {f"{args.label}/{path.relative_to(args.out)}": path for pattern in PUBLISH_PATTERNS
                 for path in args.out.glob(pattern)}
        push_files(RESULTS_BRANCH, files, f"training results {args.label}")
    return 0


def main():
    parser = argparse.ArgumentParser(description="the training half of the training workflow: wait for the Kaggle "
                                                 "kernel or take the Modal output, hand pending runs to the scan "
                                                 "stage, report")
    commands = parser.add_subparsers(dest="command", required=True)
    watch = commands.add_parser("watch", help="wait for the kernel, download its output and push runs that still "
                                              f"need the scan stage to the {SCAN_BRANCH} branch")
    watch.add_argument("slug", help="owner/kernel-slug, e.g. kylekreuter/vistructum-train")
    watch.add_argument("--interval", type=int, default=120, help="seconds between status polls")
    watch.add_argument("--max-hours", type=float, default=5.5, help="give up after this long")
    watch.add_argument("--kaggle-cmd", default=f"{sys.executable} -m kaggle")
    watch.add_argument("--queue-minutes", type=float, default=None,
                       help=f"exit {QUEUE_TIMEOUT} if the kernel is still queued after this long")
    handover = commands.add_parser("handoff", help="push runs of an output that is already on disk (Modal) that still "
                                                   f"need the scan stage to the {SCAN_BRANCH} branch")
    summary = commands.add_parser("report", help="merge scan stage results into the output, write report.md and "
                                                 f"publish it to the {RESULTS_BRANCH} branch")
    summary.add_argument("--label", required=True)
    summary.add_argument("--scan-results", type=Path, default=None,
                         help="directory with one completed run directory per scanned run")
    summary.add_argument("--publish", action="store_true")
    for command in (watch, handover, summary):
        command.add_argument("--out", type=Path, required=True, help="directory for the training output")
    args = parser.parse_args()
    return {"watch": run_watch, "handoff": run_handoff, "report": run_report}[args.command](args)


if __name__ == "__main__":
    sys.exit(main())
