import argparse
import json
import sys
from pathlib import Path

import numpy as np
import onnxruntime as ort

sys.path.insert(0, str(Path(__file__).resolve().parent))
from kaggle_run import fused_hk_metrics

LABELS = ("ok", "hakenkreuz")
GATE_PRECISION = 0.95
GATE_RECALL = 0.95


def load_test(test_npz):
    raw = np.load(test_npz)
    images = raw["images"].astype(np.float32) / 255.0
    flat = images.reshape(images.shape[0], -1)
    mean = flat.mean(axis=1).reshape(-1, 1, 1, 1)
    std = np.clip(flat.std(axis=1).reshape(-1, 1, 1, 1), 1e-3, None)
    images = ((images[:, None, :, :] - mean) / std).astype(np.float32)
    labels = raw["labels"].astype(np.int64)
    subtypes = [str(s) for s in raw["subtypes"]] if "subtypes" in raw else None
    return images, labels, subtypes


def single_predict(onnx_path, images):
    session = ort.InferenceSession(str(onnx_path), providers=["CPUExecutionProvider"])
    output = session.get_outputs()[0].name
    entry = session.get_inputs()[0].name
    preds = np.zeros(len(images), dtype=np.int64)
    for index, image in enumerate(images):
        preds[index] = int(session.run([output], {entry: image[None]})[0].argmax())
    return preds


def precision_recall(labels, preds, positive=1):
    true_positives = int(((preds == positive) & (labels == positive)).sum())
    actual = int((labels == positive).sum())
    flagged = int((preds == positive).sum())
    precision = true_positives / flagged if flagged else (1.0 if not actual else 0.0)
    recall = true_positives / actual if actual else None
    return precision, recall


def evaluate(onnx_path, test_npz):
    images, labels, subtypes = load_test(test_npz)
    preds = single_predict(onnx_path, images)
    single_precision, single_recall = precision_recall(labels, preds)
    fused_recall, fused_precision = fused_hk_metrics(onnx_path, test_npz)
    per_subtype = {}
    if subtypes:
        for subtype in sorted(set(subtypes)):
            mask = np.array([s == subtype for s in subtypes])
            precision, recall = precision_recall(labels[mask], preds[mask])
            per_subtype[subtype] = {"n": int(mask.sum()), "precision": precision,
                                    "recall": recall}
    return {"single": {"precision": single_precision, "recall": single_recall},
            "fused": {"precision": fused_precision, "recall": fused_recall},
            "subtypes": per_subtype, "n": len(images)}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("test_npz")
    parser.add_argument("models", nargs="+")
    parser.add_argument("--out", default="leaderboard.json")
    args = parser.parse_args()
    board = {}
    for spec in args.models:
        name, path = spec.split("=", 1)
        entry = evaluate(Path(path), args.test_npz)
        fused = entry["fused"]
        entry["gate"] = ("PASS" if fused["precision"] >= GATE_PRECISION
                         and (fused["recall"] or 0.0) >= GATE_RECALL else "FAIL")
        board[name] = entry
        fmt = lambda v: f"{v:.3f}" if v is not None else "n/a"
        print(f"{name} | single P/R {fmt(entry['single']['precision'])}/"
              f"{fmt(entry['single']['recall'])} | fused P/R {fmt(fused['precision'])}/"
              f"{fmt(fused['recall'])} | {entry['gate']}", flush=True)
    Path(args.out).write_text(json.dumps(board, indent=2))
    print(f"leaderboard written to {args.out}", flush=True)


if __name__ == "__main__":
    sys.exit(main())
