import argparse
import json
import shutil
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent
sys.path.insert(0, str(REPO))

from benchmark import evaluate
from kaggle_run import (SUITES, fused_hk_metrics, require_cuda,
                        require_onnxruntime, run_experiment, summarize)

GEN_RECIPES = {
    "r7": {"target": "data-r7", "count": "50000",
           "args": ["--adversarial-fraction", "0.4", "--wall-share", "0.4",
                    "--clipped-share", "0.6", "--translate", "12", "--cutout", "0.5",
                    "--zoom-min", "0.6", "--zoom-max", "1.4", "--inversion", "0.05",
                    "--low-contrast", "0.4", "--hard-ok-share", "0.5",
                    "--mined-ok-share", "0.5", "--slop-share", "0.5"]},
}

POOL_DIRS = ("backgrounds-ground", "backgrounds-sky", "mined")
MODEL_VERSION = "bf-bin-1"


def repo_sha():
    try:
        return subprocess.run(["git", "rev-parse", "--short", "HEAD"], cwd=REPO,
                              capture_output=True, text=True).stdout.strip()
    except OSError:
        return "unknown"


def generate(suite_name, pools_dir, work_dir):
    recipe = GEN_RECIPES[suite_name]
    target = work_dir / recipe["target"]
    for pool in POOL_DIRS:
        source = pools_dir / pool
        if source.is_dir():
            shutil.copytree(source, target / pool, dirs_exist_ok=True)
    missing = [pool for pool in POOL_DIRS if not (target / pool).is_dir()]
    if missing:
        raise SystemExit(f"pools missing in input: {', '.join(missing)}")
    command = [sys.executable, str(REPO / "dataset.py"), str(target), recipe["count"]]
    command += recipe["args"]
    print(f"=== generate: {' '.join(command)} ===", flush=True)
    completed = subprocess.run(command)
    if completed.returncode != 0:
        raise SystemExit(f"dataset generation failed: {completed.returncode}")
    manifest = json.loads((target / "manifest.json").read_text())
    print(f"=== generated: {manifest['counts']} ===", flush=True)
    return target


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--suite", choices=tuple(SUITES), default="r7")
    parser.add_argument("--input", default="/kaggle/input/vistructum-pools")
    parser.add_argument("--work", default="")
    args = parser.parse_args()
    suite = SUITES[args.suite]
    sha = repo_sha()
    print(f"=== workflow suite={args.suite} repo={sha} ===", flush=True)
    require_cuda()
    require_onnxruntime()
    work_dir = Path(args.work) if args.work else Path(suite["work"])
    work_dir.mkdir(parents=True, exist_ok=True)
    data_dir = generate(args.suite, Path(args.input), work_dir)
    script = REPO / "train.py"
    outcomes = []
    for name, _, target, rounds, extra in suite["experiments"]:
        returncode = run_experiment(sys.executable, script, work_dir, work_dir,
                                    name, data_dir.name, target, rounds, extra)
        fused = None
        onnx_path = work_dir / target / f"{MODEL_VERSION}.onnx"
        test_npz = data_dir / "test.npz"
        if onnx_path.is_file() and test_npz.is_file():
            print(f"=== fused tta eval {name} ===", flush=True)
            fused = fused_hk_metrics(onnx_path, test_npz)
            print(f"=== fused tta eval {name}: hk-R/P {fused} ===", flush=True)
        outcomes.append((name, target, returncode, fused))
    summarize(work_dir, outcomes)
    board = {}
    for name, target, returncode, _ in outcomes:
        onnx_path = work_dir / target / f"{MODEL_VERSION}.onnx"
        if returncode == 0 and onnx_path.is_file():
            entry = evaluate(onnx_path, data_dir / "test.npz")
            fused = entry["fused"]
            entry["gate"] = ("PASS" if fused["precision"] >= 0.95
                             and fused["recall"] >= 0.95 else "FAIL")
            board[name] = entry
    results_dir = work_dir / "results"
    board_path = results_dir / "leaderboard.json"
    board_path.write_text(json.dumps({"repo": sha, "suite": args.suite,
                                      "models": board}, indent=2))
    print(f"=== leaderboard ({len(board)} models, repo={sha}) ===", flush=True)
    for name, entry in board.items():
        fused = entry["fused"]
        print(f"{name} | fused P/R {fused['precision']:.3f}/{fused['recall']:.3f} | "
              f"{entry['gate']}", flush=True)


if __name__ == "__main__":
    main()
