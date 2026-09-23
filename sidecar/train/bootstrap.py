import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

REPO_URL = os.environ.get("VISTRUCTUM_REPO", "https://github.com/KyleKreuter/vistructum.git")
REPO_REF = os.environ.get("VISTRUCTUM_REF", "__VISTRUCTUM_REF__")
CONFIGS = os.environ.get("VISTRUCTUM_CONFIGS", "__VISTRUCTUM_CONFIGS__")
SCRATCH = Path(os.environ.get("VISTRUCTUM_SCRATCH", "/tmp/vistructum"))
OUTPUT = Path(os.environ.get("VISTRUCTUM_OUTPUT", "/kaggle/working"))
SPLITS = ("train", "val", "test", "holdout")
SEED = int(os.environ.get("VISTRUCTUM_SEED", "0"))
DEVICE = os.environ.get("VISTRUCTUM_DEVICE", "cuda")

SHA_RE = re.compile(r"^[0-9a-f]{40}$")


def run(command, cwd=None, env=None):
    print("+ " + " ".join(command), flush=True)
    subprocess.run(command, cwd=cwd, env=env, check=True)


def checkout(repo_dir):
    if not (repo_dir / ".git").is_dir():
        run(["git", "clone", REPO_URL, str(repo_dir)])
    run(["git", "fetch", "origin", REPO_REF], cwd=repo_dir)
    run(["git", "checkout", "--detach", REPO_REF], cwd=repo_dir)


def install(repo_dir):
    run([sys.executable, "-m", "pip", "install", "--quiet", "-r", str(repo_dir / "sidecar/train/requirements.txt")])
    run([sys.executable, "-m", "pip", "install", "--quiet", "--no-deps", "-e", str(repo_dir / "sidecar")])


def gpu_count():
    out = subprocess.run([sys.executable, "-c", "import torch; print(torch.cuda.device_count())"],
                         capture_output=True, text=True, check=True)
    return int(out.stdout.strip())


def read_config(path):
    import yaml

    return yaml.safe_load(path.read_text())


def generate(train_dir, cfg, data_dir, seed, env):
    command = [sys.executable, "-m", "generator.build", "--kind", cfg["kind"], "--out", str(data_dir),
               "--seed", str(seed), "--workers", str(os.cpu_count() or 4)]
    for split in SPLITS:
        size = os.environ.get(f"VISTRUCTUM_{split.upper()}_N") or cfg.get(f"gen_{split}")
        if size:
            command += [f"--{split}", str(size)]
    run(command, cwd=train_dir, env=env)


def launch(train_dir, config, data_dir, runs_dir, gpu, threads, env):
    job_env = dict(env, CUDA_VISIBLE_DEVICES=str(gpu))
    command = [sys.executable, "run.py", "--config", config, "--runs-dir", str(runs_dir),
               "--set", f"data_dir={data_dir}", "--set", f"device={DEVICE}", "--set", f"num_threads={threads}", "--set", f"scan_workers={threads}"]
    print(f"+ [gpu {gpu}] " + " ".join(command), flush=True)
    with open(runs_dir / f"{Path(config).stem}.log", "w") as log:
        return subprocess.Popen(command, cwd=train_dir, env=job_env, stdout=log, stderr=subprocess.STDOUT)


def collect(runs_dir, release_dir):
    summary = {}
    for manifest_path in sorted(runs_dir.glob("*/manifest.json")):
        manifest = json.loads(manifest_path.read_text())
        kind = manifest["kind"]
        shutil.copy(manifest_path.parent / f"{kind}.onnx", release_dir / f"{kind}.onnx")
        shutil.copy(manifest_path, release_dir / f"{kind}-manifest.json")
        summary[kind] = {split: {key: report.get(key) for key in ("verdict", "failures")}
                         | {key: report["metrics"].get(key) for key in ("precision", "recall", "fp_rate", "threshold")}
                         for split, report in manifest["metrics"]["splits"].items()}
    return summary


def main():
    if not SHA_RE.match(REPO_REF):
        raise SystemExit(f"VISTRUCTUM_REF must be a full 40-char commit sha, got {REPO_REF!r}")
    repo_dir = SCRATCH / "repo"
    SCRATCH.mkdir(parents=True, exist_ok=True)
    checkout(repo_dir)
    install(repo_dir)
    train_dir = repo_dir / "sidecar" / "train"
    env = dict(os.environ, PYTHONPATH=f"{repo_dir / 'sidecar'}:{train_dir}")
    configs = [c.strip() for c in CONFIGS.split(",") if c.strip()]
    gpus = gpu_count() if DEVICE == "cuda" else 1
    print(json.dumps({"ref": REPO_REF, "configs": configs, "gpus": gpus, "cpus": os.cpu_count()}), flush=True)
    if gpus == 0:
        raise SystemExit("no CUDA device visible; select the GPU T4 x2 accelerator")
    runs_dir = OUTPUT / "runs"
    release_dir = OUTPUT / "release"
    runs_dir.mkdir(parents=True, exist_ok=True)
    release_dir.mkdir(parents=True, exist_ok=True)
    jobs = []
    datasets = {}
    for config in configs:
        cfg = read_config(train_dir / config)
        # configs that ask for the same data share one generated set, so an A/B of two models costs one generation
        key = (cfg["kind"],) + tuple(os.environ.get(f"VISTRUCTUM_{split.upper()}_N") or cfg.get(f"gen_{split}")
                                     for split in SPLITS)
        if key not in datasets:
            data_dir = SCRATCH / "data" / Path(config).stem
            generate(train_dir, cfg, data_dir, SEED + 1000 * len(datasets), env)
            datasets[key] = data_dir
        jobs.append((config, datasets[key]))
    threads = max(1, (os.cpu_count() or 4) // min(len(jobs), gpus))
    exit_codes = {}
    for start in range(0, len(jobs), gpus):
        batch = jobs[start:start + gpus]
        procs = [(config, launch(train_dir, config, data_dir, runs_dir, gpu, threads, env))
                 for gpu, (config, data_dir) in enumerate(batch)]
        for config, proc in procs:
            exit_codes[config] = proc.wait()
    summary = {"ref": REPO_REF, "exit_codes": exit_codes, "models": collect(runs_dir, release_dir)}
    (release_dir / "summary.json").write_text(json.dumps(summary, indent=2))
    print(json.dumps(summary, indent=2), flush=True)
    for config, code in exit_codes.items():
        if code != 0:
            print(f"!! {config} exited {code}, see runs/{Path(config).stem}.log", flush=True)


if __name__ == "__main__":
    main()
