import os
import subprocess
import sys

REPO_URL = os.environ.get("VISTRUCTUM_REPO", "https://github.com/KyleKreuter/vistructum.git")
REPO_REF = os.environ.get("VISTRUCTUM_REF", "main")
POOL_DATASET = os.environ.get(
    "VISTRUCTUM_POOLS", "/kaggle/input/vistructum-pools")
SUITE = os.environ.get("VISTRUCTUM_SUITE", "r8")
WORK = os.environ.get("VISTRUCTUM_WORK", "/kaggle/working/vistructum")


def run(command, cwd=None):
    print("+ " + " ".join(command), flush=True)
    subprocess.run(command, cwd=cwd, check=True)


def main():
    work = os.path.abspath(WORK)
    os.makedirs(work, exist_ok=True)
    repo_dir = os.path.join(work, "repo")
    if not os.path.isdir(os.path.join(repo_dir, ".git")):
        run(["git", "clone", "--depth", "1", "--branch", REPO_REF, REPO_URL, repo_dir])
    else:
        run(["git", "fetch", "origin", REPO_REF], cwd=repo_dir)
        run(["git", "reset", "--hard", f"origin/{REPO_REF}"], cwd=repo_dir)
    train_dir = os.path.join(repo_dir, "sidecar", "train")
    requirements = os.path.join(train_dir, "requirements.txt")
    if os.path.isfile(requirements):
        run([sys.executable, "-m", "pip", "install", "--quiet", "-r", requirements])
    run([sys.executable, "-m", "pip", "install", "--quiet",
         "onnxruntime-gpu" if _cuda() else "onnxruntime"])
    sys.path.insert(0, train_dir)
    from kaggle_workflow import run_suite
    report = run_suite(train_dir, POOL_DATASET, os.path.join(work, "runs"), SUITE)
    print("leaderboard written: " + report, flush=True)


def _cuda():
    try:
        import torch
        return torch.cuda.is_available()
    except ImportError:
        return False


if __name__ == "__main__":
    main()
