import argparse
import json
import sys
from pathlib import Path

import numpy as np

from dataset import GRID, draw_hakenkreuz

SKY_VALUE = 235


def load_ground_pool(root):
    pool = []
    for path in sorted((root / "backgrounds-ground").glob("real-*.npy")):
        if path.stem.endswith("-h"):
            continue
        patch = np.load(path)
        if patch.shape == (GRID, GRID):
            pool.append(patch.astype(np.uint8))
    return pool


def tiled_background(rng, backgrounds, size):
    per_row = size // GRID + 1
    tiles = [backgrounds[i] for i in rng.integers(len(backgrounds), size=per_row * per_row)]
    rows = []
    for r in range(per_row):
        rows.append(np.concatenate(tiles[r * per_row:(r + 1) * per_row], axis=1)[:, :size])
    return np.concatenate(rows, axis=0)[:size, :size]


def symbol_value(rng, tilemap, top, left, size, low_contrast=False):
    local = tilemap[top:top + size, left:left + size]
    mean = float(local.mean()) if local.size else 128.0
    if low_contrast:
        offset = float(rng.integers(0, 29))
    else:
        offset = float(rng.integers(60, 151))
    value = mean + offset if mean < 128 else mean - offset
    return int(np.clip(value, 0, 255))


def place_symbols(rng, tilemap, count, size_min=26, size_max=52, low_contrast_share=0.25):
    bboxes = []
    placed = []
    for _ in range(count):
        size = int(rng.integers(size_min, size_max + 1))
        top = int(rng.integers(0, tilemap.shape[0] - size))
        left = int(rng.integers(0, tilemap.shape[1] - size))
        if any(top < p_top + p_size and top + size > p_top and
               left < p_left + p_size and left + size > p_left
               for p_top, p_left, p_size in placed):
            continue
        low = rng.random() < low_contrast_share
        value = symbol_value(rng, tilemap, top, left, size, low)
        draw_hakenkreuz(tilemap, top, left, size, max(2, size // 10),
                        value, bool(rng.integers(2)))
        placed.append((top, left, size))
        bboxes.append({"top": top, "left": left, "size": size, "label": "hakenkreuz"})
    return bboxes


def generate_ground_tilemap(rng, backgrounds, size=512, n_symbols=6):
    tilemap = tiled_background(rng, backgrounds, size).astype(np.int16)
    bboxes = place_symbols(rng, tilemap, n_symbols)
    return np.clip(tilemap, 0, 255).astype(np.uint8), bboxes


def generate_wall_tilemap(rng, backgrounds, size=512, n_symbols=6):
    tilemap = np.full((size, size), SKY_VALUE, dtype=np.int16)
    heights = [size // 3 + int(rng.integers(-40, 41))]
    for _ in range(1, size):
        heights.append(int(np.clip(heights[-1] + rng.integers(-3, 4), size // 6, size * 2 // 3)))
    earth = tiled_background(rng, backgrounds, size).astype(np.int16)
    for x in range(size):
        tilemap[heights[x]:, x] = earth[heights[x]:, x]
    bboxes = []
    placed = []
    for _ in range(n_symbols * 3):
        if len(bboxes) >= n_symbols:
            break
        sym_size = int(rng.integers(26, 53))
        left = int(rng.integers(0, size - sym_size))
        surface = heights[left + sym_size // 2]
        top = int(surface + rng.integers(2, 20))
        if top + sym_size >= size:
            continue
        if any(top < p[0] + p[2] and top + sym_size > p[0] and
               left < p[1] + p[2] and left + sym_size > p[1] for p in placed):
            continue
        value = symbol_value(rng, tilemap, top, left, sym_size)
        draw_hakenkreuz(tilemap, top, left, sym_size, max(2, sym_size // 10),
                        value, bool(rng.integers(2)))
        placed.append((top, left, sym_size))
        bboxes.append({"top": top, "left": left, "size": sym_size, "label": "hakenkreuz"})
    return np.clip(tilemap, 0, 255).astype(np.uint8), bboxes


def cut_windows(tilemap, bboxes, stride=64):
    windows = []
    for top in range(0, tilemap.shape[0] - GRID + 1, stride):
        for left in range(0, tilemap.shape[1] - GRID + 1, stride):
            overlaps = []
            for index, box in enumerate(bboxes):
                inter_top = max(top, box["top"])
                inter_left = max(left, box["left"])
                inter_bottom = min(top + GRID, box["top"] + box["size"])
                inter_right = min(left + GRID, box["left"] + box["size"])
                if inter_bottom > inter_top and inter_right > inter_left:
                    area = (inter_bottom - inter_top) * (inter_right - inter_left)
                    overlaps.append({"bbox": index,
                                     "visible": area / (box["size"] * box["size"])})
            windows.append({"top": top, "left": left,
                            "image": tilemap[top:top + GRID, left:left + GRID],
                            "overlaps": overlaps})
    return windows


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("out")
    parser.add_argument("--maps", type=int, default=4)
    parser.add_argument("--size", type=int, default=512)
    parser.add_argument("--symbols", type=int, default=6)
    parser.add_argument("--wall-share", type=float, default=0.5)
    parser.add_argument("--seed", type=int, default=7)
    args = parser.parse_args()
    root = Path("data-r3")
    patches = load_ground_pool(root)
    if not patches:
        raise SystemExit(f"no backgrounds in {root}")
    rng = np.random.default_rng(args.seed)
    out = Path(args.out)
    (out / "maps").mkdir(parents=True, exist_ok=True)
    index = []
    for number in range(args.maps):
        upright = rng.random() < args.wall_share
        if upright:
            tilemap, bboxes = generate_wall_tilemap(rng, patches, args.size, args.symbols)
        else:
            tilemap, bboxes = generate_ground_tilemap(rng, patches, args.size, args.symbols)
        np.save(out / "maps" / f"tile-{number:03d}.npy", tilemap)
        windows = [{"top": w["top"], "left": w["left"], "overlaps": w["overlaps"]}
                   for w in cut_windows(tilemap, bboxes)]
        clipped = sum(1 for w in windows
                      for o in w["overlaps"] if 0 < o["visible"] < 1.0)
        index.append({"map": f"tile-{number:03d}.npy", "upright": upright,
                      "symbols": len(bboxes), "windows": len(windows),
                      "clipped_windows": clipped})
    (out / "tilemap-index.json").write_text(json.dumps(index, indent=2))
    print(json.dumps({"maps": len(index),
                      "symbols": sum(e["symbols"] for e in index),
                      "clipped_windows": sum(e["clipped_windows"] for e in index)}))


if __name__ == "__main__":
    sys.exit(main())
