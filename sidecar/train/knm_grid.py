import argparse
import json
import sys
from pathlib import Path

import numpy as np
import onnxruntime as ort

sys.path.insert(0, str(Path(__file__).resolve().parent))
from dataset import GRID
from fusion import batched_session, cam_session, predict_batch, predict_knm_grid, softmax
from tilemap import cut_windows, generate_ground_tilemap, load_ground_pool

THRESHOLD = 0.85


def prep(image):
    flat = image.astype(np.float64)
    std = flat.std()
    return ((flat - flat.mean()) / max(std, 1e-3)).astype(np.float32)[None]


def batch_hk(session, prepped):
    return predict_batch(session, prepped)[:, 1]


def symbol_center(box):
    return box["top"] + box["size"] / 2.0, box["left"] + box["size"] / 2.0


def containing(windows, center):
    cy, cx = center
    return [i for i, w in enumerate(windows)
            if w["top"] <= cy < w["top"] + GRID and w["left"] <= cx < w["left"] + GRID]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("model")
    parser.add_argument("--pool", default="data-r3")
    parser.add_argument("--maps", type=int, default=6)
    parser.add_argument("--symbols", type=int, default=6)
    parser.add_argument("--seed", type=int, default=7)
    args = parser.parse_args()
    pool = load_ground_pool(Path(args.pool))
    session = batched_session(str(args.model))
    feat_session, head = cam_session(args.model)
    rng = np.random.default_rng(args.seed)
    scores = {"single64": [], "nf9": [], "knm": []}
    windows64_total = windows32_total = substituted = 0
    for _ in range(args.maps):
        tilemap, bboxes = generate_ground_tilemap(rng, pool, n_symbols=args.symbols)
        windows64 = cut_windows(tilemap, bboxes, stride=64)
        windows32 = cut_windows(tilemap, bboxes, stride=32)
        windows64_total += len(windows64)
        windows32_total += len(windows32)
        prepped64 = [prep(w["image"]) for w in windows64]
        prepped32 = [prep(w["image"]) for w in windows32]
        scores64 = batch_hk(session, prepped64)
        scores32 = batch_hk(session, prepped32)
        for box in bboxes:
            center = symbol_center(box)
            hits64 = containing(windows64, center)
            hits32 = containing(windows32, center)
            scores["single64"].append(max(float(scores64[i]) for i in hits64))
            scores["nf9"].append(max(float(scores32[i]) for i in hits32))
            images = [prepped32[i] for i in hits32]
            positions = [(windows32[i]["top"], windows32[i]["left"]) for i in hits32]
            original = [float(scores32[i]) for i in hits32]
            _, _, adjusted = predict_knm_grid(feat_session, head, images, positions,
                                              (0.0, THRESHOLD))
            adjusted_hk = [float(p[1]) for p in adjusted]
            scores["knm"].append(max(adjusted_hk))
            substituted += sum(1 for a, o in zip(adjusted_hk, original)
                               if abs(a - o) > 1e-9)
    print("strategy | n_symbols | mean_hk | rate@0.85 | windows_total")
    total = len(scores["single64"])
    for name in ("single64", "nf9", "knm"):
        values = np.array(scores[name])
        windows = windows64_total if name == "single64" else windows32_total
        print(f"{name} | {total} | {values.mean():.3f} | "
              f"{float(np.mean(values >= THRESHOLD)):.3f} | {windows}")
    print(f"knm_substituted: {substituted}/{total} symbols affected")
    print(json.dumps({name: [round(float(v), 4) for v in values]
                      for name, values in scores.items()}))


if __name__ == "__main__":
    main()
