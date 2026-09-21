import json
import sys
from pathlib import Path

import numpy as np
import onnxruntime as ort

LABELS = ("ok", "hakenkreuz")
SUITE = sys.argv[1] if len(sys.argv) > 1 else "r3"
NAMES = sys.argv[2].split(",") if len(sys.argv) > 2 else ("a", "b", "c", "d", "e")


def subtype_table(onnx_path, test_npz):
    raw = np.load(test_npz)
    images = raw["images"].astype(np.float32) / 255.0
    flat = images.reshape(images.shape[0], -1)
    mean = flat.mean(axis=1).reshape(-1, 1, 1, 1)
    std = np.clip(flat.std(axis=1).reshape(-1, 1, 1, 1), 1e-3, None)
    images = ((images[:, None, :, :] - mean) / std).astype(np.float32)
    labels = raw["labels"].astype(np.int64)
    subtypes = [str(s) for s in raw["subtypes"]]
    session = ort.InferenceSession(str(onnx_path), providers=["CPUExecutionProvider"])
    output = session.get_outputs()[0].name
    entry = session.get_inputs()[0].name
    preds = np.array([int(session.run([output], {entry: image[None]})[0].argmax())
                      for image in images])
    per_subtype = {}
    for subtype in sorted(set(subtypes)):
        mask = np.array([s == subtype for s in subtypes])
        sub_labels, sub_preds = labels[mask], preds[mask]
        cells = {}
        for index, label in enumerate(LABELS):
            true_positives = int(((sub_preds == index) & (sub_labels == index)).sum())
            actual = int((sub_labels == index).sum())
            flagged = int((sub_preds == index).sum())
            cells[label] = {
                "n": int(mask.sum()),
                "recall": true_positives / actual if actual else None,
                "precision": true_positives / flagged if flagged else None,
            }
        per_subtype[subtype] = cells
    return per_subtype


def main():
    root = Path(__file__).resolve().parent
    raw = np.load(root / f"data-{SUITE}" / "test.npz")
    subtypes = [str(s) for s in raw["subtypes"]]
    report = {}
    for name in NAMES:
        model = root / f"models-{SUITE}-{name}" / "bf-bin-1.onnx"
        report[name] = subtype_table(model, root / f"data-{SUITE}" / "test.npz")
    (root / f"subtype-report-{SUITE}.json").write_text(json.dumps(report, indent=2))
    print("modell | subtyp | n | hk-R/P | ok-R/P")
    for name in NAMES:
        for subtype, cells in report[name].items():
            fmt = lambda c: f"{c['recall']:.2f}/{c['precision']:.2f}" if c["recall"] is not None else "-"
            print(f"{name} | {subtype} | {cells['ok']['n']} | {fmt(cells['hakenkreuz'])} | "
                  f"{fmt(cells['ok'])}")


if __name__ == "__main__":
    sys.exit(main())
