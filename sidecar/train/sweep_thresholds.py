import json
import sys
from pathlib import Path

import numpy as np
import onnxruntime as ort

LABELS = ("ok", "hakenkreuz")
SUITE = sys.argv[1] if len(sys.argv) > 1 else "r3"
NAMES = sys.argv[2].split(",") if len(sys.argv) > 2 else ("a", "b", "c", "d", "e")
SPLIT = sys.argv[3] if len(sys.argv) > 3 else "val"


def load_split(root, split):
    raw = np.load(root / f"data-{SUITE}" / f"{split}.npz")
    images = raw["images"].astype(np.float32) / 255.0
    flat = images.reshape(images.shape[0], -1)
    mean = flat.mean(axis=1).reshape(-1, 1, 1, 1)
    std = np.clip(flat.std(axis=1).reshape(-1, 1, 1, 1), 1e-3, None)
    return ((images[:, None, :, :] - mean) / std).astype(np.float32), raw["labels"].astype(np.int64)


def softmax(logits):
    shifted = logits - logits.max(axis=1, keepdims=True)
    over = np.exp(shifted)
    return over / over.sum(axis=1, keepdims=True)


def sweep(scores, labels, index):
    thresholds = np.arange(0.05, 1.0, 0.05)
    rows = []
    for threshold in thresholds:
        predicted = scores.argmax(axis=1).copy()
        confident = scores[:, index] >= threshold
        predicted[confident] = index
        true_positives = int(((predicted == index) & (labels == index)).sum())
        actual = int((labels == index).sum())
        flagged = int((predicted == index).sum())
        rows.append({
            "threshold": round(float(threshold), 2),
            "recall": true_positives / actual if actual else 0.0,
            "precision": true_positives / flagged if flagged else 0.0,
            "flagged": flagged,
        })
    return rows


def main():
    root = Path(__file__).resolve().parent
    images, labels = load_split(root, SPLIT)
    report = {}
    for name in NAMES:
        model = root / f"models-{SUITE}-{name}" / "bf-bin-1.onnx"
        session = ort.InferenceSession(str(model), providers=["CPUExecutionProvider"])
        output = session.get_outputs()[0].name
        entry = session.get_inputs()[0].name
        logits = np.concatenate(
            [session.run([output], {entry: image[None]})[0] for image in images])
        scores = softmax(logits)
        report[name] = {label: sweep(scores, labels, index)
                        for index, label in enumerate(LABELS)}
    (root / f"threshold-report-{SUITE}.json").write_text(json.dumps(report, indent=2))
    print("modell | klasse | beste recall bei P>=0.95 (t) | beste P bei R>=0.95 (t) | beide >=0.95")
    for name in NAMES:
        for label in LABELS:
            rows = report[name][label]
            recall_ok = [r for r in rows if r["precision"] >= 0.95]
            prec_ok = [r for r in rows if r["recall"] >= 0.95]
            both = [r for r in rows if r["precision"] >= 0.95 and r["recall"] >= 0.95]
            best_r = max(recall_ok, key=lambda r: r["recall"]) if recall_ok else None
            best_p = max(prec_ok, key=lambda r: r["precision"]) if prec_ok else None
            fmt = lambda r: f"{r['recall']:.3f}/{r['precision']:.3f}@{r['threshold']}" if r else "-"
            print(f"{name} | {label} | {fmt(best_r)} | {fmt(best_p)} | "
                  f"{'JA ' + fmt(both[0]) if both else 'nein'}")


if __name__ == "__main__":
    sys.exit(main())
