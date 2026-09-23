import os
import re
import subprocess
import sys

REPO_URL = os.environ.get("VISTRUCTUM_REPO", "https://github.com/KyleKreuter/vistructum.git")
REPO_REF = os.environ.get("VISTRUCTUM_REF", "__VISTRUCTUM_REF__")
CONFIG = os.environ.get("VISTRUCTUM_CONFIG", "__VISTRUCTUM_CONFIG__")
WORK = os.environ.get("VISTRUCTUM_WORK", "/kaggle/working/vistructum")
TRAIN_N = os.environ.get("VISTRUCTUM_TRAIN_N", "20000")
VAL_N = os.environ.get("VISTRUCTUM_VAL_N", "10000")
TEST_N = os.environ.get("VISTRUCTUM_TEST_N", "10000")
HOLDOUT_N = os.environ.get("VISTRUCTUM_HOLDOUT_N", "10000")
SEED = os.environ.get("VISTRUCTUM_SEED", "0")

SHA_RE = re.compile(r"^[0-9a-f]{40}$")


def run(command, cwd=None):
    print("+ " + " ".join(command), flush=True)
    subprocess.run(command, cwd=cwd, check=True)


def config_kind(path):
    import yaml

    return yaml.safe_load(open(path))["kind"]


def main():
    if not SHA_RE.match(REPO_REF):
        raise SystemExit(f"VISTRUCTUM_REF must be a full 40-char commit sha, got {REPO_REF!r}")
    work = os.path.abspath(WORK)
    os.makedirs(work, exist_ok=True)
    repo_dir = os.path.join(work, "repo")
    if not os.path.isdir(os.path.join(repo_dir, ".git")):
        run(["git", "clone", REPO_URL, repo_dir])
    run(["git", "fetch", "origin", REPO_REF], cwd=repo_dir)
    run(["git", "checkout", "--detach", REPO_REF], cwd=repo_dir)
    train_dir = os.path.join(repo_dir, "sidecar", "train")
    sidecar_dir = os.path.join(repo_dir, "sidecar")
    run([sys.executable, "-m", "pip", "install", "--quiet", "-r", os.path.join(train_dir, "requirements.txt")])
    run([sys.executable, "-m", "pip", "install", "--quiet", "-e", sidecar_dir])
    if _cuda():
        run([sys.executable, "-m", "pip", "install", "--quiet", "--force-reinstall", "onnxruntime-gpu==1.20.1"])
    kind = config_kind(os.path.join(train_dir, CONFIG))
    data_dir = os.path.join(work, "data")
    run(
        [
            sys.executable, "-m", "generator.build",
            "--kind", kind,
            "--out", data_dir,
            "--train", TRAIN_N,
            "--val", VAL_N,
            "--test", TEST_N,
            "--holdout", HOLDOUT_N,
            "--seed", SEED,
        ],
        cwd=train_dir,
    )
    run(
        [
            sys.executable, "run.py",
            "--config", CONFIG,
            "--set", f"data_dir={data_dir}",
            "--set", "device=cuda",
            "--runs-dir", os.path.join(work, "runs"),
        ],
        cwd=train_dir,
    )


def _cuda():
    try:
        import torch

        return torch.cuda.is_available()
    except ImportError:
        return False


if __name__ == "__main__":
    main()
