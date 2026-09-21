import argparse
import json
import shutil
import subprocess
import sys
from pathlib import Path

import numpy as np

MODEL_VERSION = "bf-bin-1"
PRECISION_TARGET = 0.95
DATA_SPLITS = ("train", "val", "test")
DATA_DIRS = ("data", "data-exp-c")
DEFAULT_SUITE = "r5"

ADAMW_FULL = ["--optimizer", "adamw", "--lr", "0.002", "--weight-decay", "0.01",
              "--label-smoothing", "0.1", "--no-focal", "--warmup", "5",
              "--ema", "0.999", "--batch", "64"]
ADAMW_MILD = ["--optimizer", "adamw", "--lr", "0.001", "--weight-decay", "0.001",
              "--label-smoothing", "0.05", "--no-focal", "--warmup", "5",
              "--ema", "0.999", "--batch", "64"]

EXPERIMENTS = [
    ("a", "data", "models-exp-a", "150", ["--arch", "v2"]),
    ("b", "data", "models-exp-b", "200", ["--arch", "v1"] + ADAMW_FULL),
    ("d", "data", "models-exp-d", "200", ["--arch", "v2"] + ADAMW_FULL),
    ("e", "data-exp-c", "models-exp-e", "200", ["--arch", "v2"] + ADAMW_FULL),
    ("f", "data-exp-c", "models-exp-f", "200", ["--arch", "v1"] + ADAMW_FULL),
    ("g", "data-exp-c", "models-exp-g", "150", ["--arch", "v2"]),
    ("h", "data", "models-exp-h", "300", ["--arch", "v1"]),
    ("i", "data", "models-exp-i", "150", ["--arch", "v2", "--lr", "0.003"]),
    ("j", "data", "models-exp-j", "200", ["--arch", "v1"] + ADAMW_MILD),
]

R2_EXPERIMENTS = [
    ("r2a", "data-r2", "models-r2-a", "200", ["--arch", "v2"]),
    ("r2b", "data-r2", "models-r2-b", "200", ["--arch", "v2", "--hk-weight", "2.0"]),
    ("r2c", "data-r2", "models-r2-c", "200", ["--arch", "v2"] + ADAMW_FULL),
    ("r2d", "data-r2", "models-r2-d", "200", ["--arch", "v2", "--hk-weight", "2.0"] + ADAMW_FULL),
    ("r2e", "data-r2", "models-r2-e", "200", ["--arch", "v2", "--hk-weight", "3.0"] + ADAMW_FULL),
]

R3_EXPERIMENTS = [
    ("r3a", "data-r3", "models-r3-a", "200", ["--arch", "v2"]),
    ("r3b", "data-r3", "models-r3-b", "200", ["--arch", "v2", "--hk-weight", "2.0"]),
    ("r3c", "data-r3", "models-r3-c", "200", ["--arch", "v2"] + ADAMW_FULL),
    ("r3d", "data-r3", "models-r3-d", "200", ["--arch", "v2", "--hk-weight", "2.0"] + ADAMW_FULL),
    ("r3e", "data-r3", "models-r3-e", "200", ["--arch", "v2", "--hk-weight", "3.0"] + ADAMW_FULL),
]

R4_EXPERIMENTS = [
    ("r4a", "data-r4", "models-r4-a", "200", ["--arch", "v3"]),
    ("r4b", "data-r4", "models-r4-b", "200", ["--arch", "v3", "--hk-weight", "2.0"]),
    ("r4c", "data-r4", "models-r4-c", "200", ["--arch", "v3"] + ADAMW_FULL),
    ("r4d", "data-r4", "models-r4-d", "200", ["--arch", "v3", "--hk-weight", "2.0"] + ADAMW_FULL),
    ("r4e", "data-r4", "models-r4-e", "200", ["--arch", "v3", "--hk-weight", "3.0"] + ADAMW_FULL),
]

R5_EXPERIMENTS = [
    ("r5a", "data-r5", "models-r5-a", "200", ["--arch", "v3"]),
    ("r5b", "data-r5", "models-r5-b", "200", ["--arch", "v3", "--lr", "0.003"]),
    ("r5c", "data-r5", "models-r5-c", "200", ["--arch", "v3"] + ADAMW_FULL),
    ("r5d", "data-r5", "models-r5-d", "200", ["--arch", "v3"] + ADAMW_MILD),
    ("r5e", "data-r5", "models-r5-e", "200", ["--arch", "v3", "--no-focal"]),
]

R6_EXPERIMENTS = [
    ("r6ma", "data-r6m", "models-r6-ma", "100", ["--arch", "v3"]),
    ("r6mb", "data-r6m", "models-r6-mb", "100", ["--arch", "v3"] + ADAMW_FULL),
    ("r6mc", "data-r6m", "models-r6-mc", "100", ["--arch", "v3"] + ADAMW_MILD),
    ("r6sa", "data-r6s", "models-r6-sa", "100", ["--arch", "v3"]),
    ("r6sb", "data-r6s", "models-r6-sb", "100", ["--arch", "v3"] + ADAMW_FULL),
    ("r6sc", "data-r6s", "models-r6-sc", "100", ["--arch", "v3", "--no-focal"]),
]

R7_EXPERIMENTS = [
    ("r7a", "data-r7", "models-r7-a", "100", ["--arch", "v3"]),
    ("r7b", "data-r7", "models-r7-b", "100", ["--arch", "v3"] + ADAMW_FULL),
    ("r7c", "data-r7", "models-r7-c", "100", ["--arch", "v3"] + ADAMW_MILD),
    ("r7d", "data-r7", "models-r7-d", "100", ["--arch", "v3", "--no-focal"]),
]

SUITES = {
    "exp9": {"experiments": EXPERIMENTS, "data_dirs": ("data", "data-exp-c"),
             "work": "/kaggle/working/exp9", "input": "/kaggle/input/vistructum-exp9-data"},
    "r2": {"experiments": R2_EXPERIMENTS, "data_dirs": ("data-r2",),
           "work": "/kaggle/working/r2", "input": "/kaggle/input/vistructum-r2-data"},
    "r3": {"experiments": R3_EXPERIMENTS, "data_dirs": ("data-r3",),
           "work": "/kaggle/working/r3", "input": "/kaggle/input/vistructum-r3-data"},
    "r4": {"experiments": R4_EXPERIMENTS, "data_dirs": ("data-r4",),
           "work": "/kaggle/working/r4", "input": "/kaggle/input/vistructum-r4-data"},
    "r5": {"experiments": R5_EXPERIMENTS, "data_dirs": ("data-r5",),
           "work": "/kaggle/working/r5", "input": "/kaggle/input/vistructum-r5-data"},
    "r6": {"experiments": R6_EXPERIMENTS, "data_dirs": ("data-r6m", "data-r6s"),
           "work": "/kaggle/working/r6", "input": "/kaggle/input/vistructum-r6-data"},
    "r7": {"experiments": R7_EXPERIMENTS, "data_dirs": ("data-r7",),
           "work": "/kaggle/working/r7", "input": "/kaggle/input/vistructum-r7-data"},
}


def require_cuda():
    import torch

    if not torch.cuda.is_available():
        raise SystemExit("no CUDA device visible; enable Kaggle GPU accelerator (T4) and retry")
    print(f"cuda device: {torch.cuda.get_device_name(0)}", flush=True)


def require_onnxruntime():
    try:
        import onnxruntime  # noqa: F401
        return
    except ImportError:
        pass
    print("onnxruntime missing, installing via pip ...", flush=True)
    completed = subprocess.run([sys.executable, "-m", "pip", "install", "-q", "onnxruntime"])
    if completed.returncode != 0:
        raise SystemExit("pip install onnxruntime failed; enable Kaggle internet and retry")
    try:
        import onnxruntime  # noqa: F401
    except ImportError:
        raise SystemExit("onnxruntime still not importable after pip install")


def require_dataset(input_dir, data_dirs):
    missing = []
    if not (input_dir / "train.py").is_file():
        missing.append("train.py")
    for data_dir in data_dirs:
        for split in DATA_SPLITS:
            if not (input_dir / data_dir / f"{split}.npz").is_file():
                missing.append(f"{data_dir}/{split}.npz")
    if missing:
        raise SystemExit(f"incomplete dataset, missing: {', '.join(missing)}")


def run_experiment(python, script, input_dir, work_dir, name, data, target, rounds, extra):
    command = [python, str(script), str(input_dir / data), str(work_dir / target),
               rounds, "--device", "cuda", "--log-every", "10"] + extra
    print(f"=== experiment {name}: {' '.join(command)} ===", flush=True)
    completed = subprocess.run(command)
    print(f"=== experiment {name} exit {completed.returncode} ===", flush=True)
    return completed.returncode


def tta_mean_probs(session, output, name, image):
    probs = []
    for turns in range(4):
        rotated = np.rot90(image, turns).copy()
        for view in (rotated, np.fliplr(rotated).copy()):
            logits = session.run([output], {name: view[None][None].astype(np.float32)})[0]
            shifted = logits - logits.max(axis=-1, keepdims=True)
            exponential = np.exp(shifted)
            probs.append((exponential / exponential.sum(axis=-1, keepdims=True))[0])
    return np.mean(probs, axis=0)


def fused_hk_metrics(onnx_path, test_npz):
    import onnxruntime as ort
    raw = np.load(test_npz)
    images = raw["images"].astype(np.float64) / 255.0
    flat = images.reshape(len(images), -1)
    mean = flat.mean(axis=1).reshape(-1, 1, 1)
    std = np.maximum(flat.std(axis=1).reshape(-1, 1, 1), 1e-3)
    images = ((images - mean) / std).astype(np.float32)
    labels = raw["labels"].astype(int)
    session = ort.InferenceSession(str(onnx_path), providers=["CPUExecutionProvider"])
    output = session.get_outputs()[0].name
    name = session.get_inputs()[0].name
    preds = [int(tta_mean_probs(session, output, name, image).argmax()) for image in images]
    true_positives = sum(1 for t, p in zip(labels, preds) if t == 1 and p == 1)
    actual = sum(1 for t in labels if t == 1)
    predicted = sum(1 for p in preds if p == 1)
    recall = true_positives / actual if actual else 0.0
    precision = true_positives / predicted if predicted else 0.0
    return round(recall, 4), round(precision, 4)


def summarize(work_dir, outcomes):
    rows = []
    for name, target, returncode, fused in outcomes:
        metrics_path = work_dir / target / f"{MODEL_VERSION}-metrics.json"
        if metrics_path.is_file():
            metrics = json.loads(metrics_path.read_text())
            precisions = metrics["test_precision"]
            verdict = "PASS" if min(precisions.values()) >= PRECISION_TARGET else "FAIL"
            rows.append((name, returncode, precisions, metrics["model_bytes"],
                         metrics["infer_seconds_mean"], fused, verdict))
        else:
            rows.append((name, returncode, None, None, None, fused, "NO METRICS"))
    print("experiment | exit | precision ok/hk | bytes | infer_s | tta-hk-R/P | gate", flush=True)
    for row in rows:
        print(" | ".join(str(field) for field in row), flush=True)
    results_dir = work_dir / "results"
    results_dir.mkdir(exist_ok=True)
    for _, target, _, _ in outcomes:
        for artifact in (work_dir / target).glob(f"{MODEL_VERSION}*"):
            if artifact.suffix in (".json", ".onnx"):
                shutil.copy(artifact, results_dir / f"{target}-{artifact.name}")
    print(f"artifacts collected in {results_dir}", flush=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", default="")
    parser.add_argument("--work", default="")
    parser.add_argument("--script", default="")
    parser.add_argument("--suite", choices=tuple(SUITES), default=DEFAULT_SUITE)
    args = parser.parse_args()
    suite = SUITES[args.suite]
    require_cuda()
    require_onnxruntime()
    input_dir = Path(args.input) if args.input else Path(suite["input"])
    work_dir = Path(args.work) if args.work else Path(suite["work"])
    work_dir.mkdir(parents=True, exist_ok=True)
    require_dataset(input_dir, suite["data_dirs"])
    script = Path(args.script).resolve() if args.script else input_dir / "train.py"
    if not script.is_file():
        raise SystemExit(f"training script not found: {script}")
    outcomes = []
    for name, data, target, rounds, extra in suite["experiments"]:
        returncode = run_experiment(sys.executable, script, input_dir, work_dir,
                                    name, data, target, rounds, extra)
        fused = None
        onnx_path = work_dir / target / f"{MODEL_VERSION}.onnx"
        test_npz = input_dir / data / "test.npz"
        if onnx_path.is_file() and test_npz.is_file():
            print(f"=== fused tta eval {name} ===", flush=True)
            fused = fused_hk_metrics(onnx_path, test_npz)
            print(f"=== fused tta eval {name}: hk-R/P {fused} ===", flush=True)
        outcomes.append((name, target, returncode, fused))
    summarize(work_dir, outcomes)


if __name__ == "__main__":
    main()
