import json
import sys
from pathlib import Path

import numpy as np
import onnxruntime as ort

GRID = 64
MODEL = Path(__file__).resolve().parent / "models-r5-d" / "bf-bin-1.onnx"
POOL = Path(__file__).resolve().parent / "data-r3" / "backgrounds-ground"
TOP = int(sys.argv[1]) if len(sys.argv) > 1 else 3000
TARGET = Path(sys.argv[2]) if len(sys.argv) > 2 else (
    Path(__file__).resolve().parent / "data-r6m" / "mined")


def standardize(images):
    flat = images.reshape(images.shape[0], -1)
    mean = flat.mean(axis=1).reshape(-1, 1, 1, 1)
    std = np.clip(flat.std(axis=1).reshape(-1, 1, 1, 1), 1e-3, None)
    return ((images[:, None, :, :] - mean) / std).astype(np.float32)


def main():
    session = ort.InferenceSession(str(MODEL), providers=["CPUExecutionProvider"])
    output = session.get_outputs()[0].name
    entry = session.get_inputs()[0].name
    candidates = []
    for path in sorted(POOL.glob("*.npy")):
        if path.stem.endswith("-h"):
            continue
        patch = np.load(path).astype(np.float32) / 255.0
        if patch.shape != (GRID, GRID):
            continue
        for variant in range(4):
            candidates.append(np.rot90(patch, variant).copy())
    batch = standardize(np.stack(candidates))
    scores = []
    for image in batch:
        logits = session.run([output], {entry: image[None]})[0][0]
        shifted = np.exp(logits - logits.max())
        scores.append(float(shifted[1] / shifted.sum()))
    order = np.argsort(scores)[::-1][:TOP]
    TARGET.mkdir(parents=True, exist_ok=True)
    for rank, position in enumerate(order):
        np.save(TARGET / f"mined-{rank:04d}.npy",
                (candidates[position] * 255).astype(np.uint8))
    report = {"candidates": len(candidates), "kept": len(order),
              "min_score": float(scores[order[-1]]), "max_score": float(scores[order[0]])}
    (TARGET / "mining.json").write_text(json.dumps(report, indent=2))
    print(json.dumps(report))


if __name__ == "__main__":
    sys.exit(main())
