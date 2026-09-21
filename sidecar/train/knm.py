import json
import sys
from pathlib import Path

import numpy as np
import onnxruntime as ort

from dataset import GRID
from fusion import cam_heatmap, cam_session, predict_single, predict_window
from tilemap import cut_windows, generate_ground_tilemap, load_ground_pool

MODEL = Path(__file__).resolve().parent / "models-r6m-mc" / "bf-bin-1.onnx"
HK = 1


def norm(image):
    flat = image.astype(np.float64)
    std = flat.std()
    return ((flat - flat.mean()) / max(std, 1e-3)).astype(np.float32)[None]


def centroid(cam):
    total = cam.sum()
    if total <= 0:
        return cam.shape[0] / 2 - 0.5, cam.shape[1] / 2 - 0.5
    rows = (cam.sum(axis=1) * np.arange(cam.shape[0])).sum() / total
    cols = (cam.sum(axis=0) * np.arange(cam.shape[1])).sum() / total
    return rows, cols


def measure(model, maps=6, size=512, stride=32, seed=99):
    if not model.is_file():
        raise SystemExit(f"model not found: {model}")
    pool = load_ground_pool(Path(__file__).resolve().parent / "data-r3")
    if not pool:
        raise SystemExit("empty ground pool")
    session = ort.InferenceSession(str(model), providers=["CPUExecutionProvider"])
    cam_sess, head = cam_session(model)
    rng = np.random.default_rng(seed)
    rows = []
    for _ in range(maps):
        tilemap, bboxes = generate_ground_tilemap(rng, pool, size, 8)
        for window in cut_windows(tilemap, bboxes, stride):
            partial = [o for o in window["overlaps"] if 0.15 <= o["visible"] < 1.0]
            if len(partial) != 1:
                continue
            image = window["image"]
            prepared = norm(image)
            single = float(predict_single(session, prepared)[HK])
            cam, _ = cam_heatmap(cam_sess, head, prepared, HK)
            rows_c, cols_c = centroid(cam)
            factor = GRID / cam.shape[0]
            map_r = window["top"] + (rows_c + 0.5) * factor
            map_c = window["left"] + (cols_c + 0.5) * factor
            new_top = int(np.clip(round(map_r - GRID / 2), 0, size - GRID))
            new_left = int(np.clip(round(map_c - GRID / 2), 0, size - GRID))
            recut = norm(tilemap[new_top:new_top + GRID, new_left:new_left + GRID])
            second = float(predict_single(session, recut)[HK])
            rows.append({"single": single, "recut": second,
                         "tta_single": float(predict_window(session, prepared)[HK]),
                         "tta_recut": float(predict_window(session, recut)[HK]),
                         "visible": partial[0]["visible"]})
    return rows


def self_check():
    assert abs(norm(np.full((GRID, GRID), 7, np.uint8)).mean()) < 1e-6
    rows, cols = centroid(np.zeros((8, 8)))
    assert (rows, cols) == (3.5, 3.5)


def main():
    self_check()
    model = Path(sys.argv[1]) if len(sys.argv) > 1 else MODEL
    rows = measure(model)
    scores = np.array([[r["single"], r["recut"], r["tta_single"], r["tta_recut"]]
                       for r in rows])
    print(json.dumps({"n": len(rows),
                      "mean_single": round(float(scores[:, 0].mean()), 4),
                      "mean_recut": round(float(scores[:, 1].mean()), 4),
                      "mean_tta_single": round(float(scores[:, 2].mean()), 4),
                      "mean_tta_recut": round(float(scores[:, 3].mean()), 4),
                      "above_085_single": int((scores[:, 0] >= 0.85).sum()),
                      "above_085_recut": int((scores[:, 1] >= 0.85).sum())}))


if __name__ == "__main__":
    main()
