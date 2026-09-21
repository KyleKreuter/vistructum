import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
from dataset import GRID
from fusion import cam_centroid, cam_heatmap, predict_single, predict_window

HK = 1


def norm(image):
    flat = image.astype(np.float64)
    std = flat.std()
    return ((flat - flat.mean()) / max(std, 1e-3)).astype(np.float32)[None]


def target_tiles(tilemap, stride=GRID):
    positions = []
    for top in range(0, tilemap.shape[0] - GRID + 1, stride):
        for left in range(0, tilemap.shape[1] - GRID + 1, stride):
            positions.append((top, left))
    return positions


def recentered_crop(tilemap, top, left, rows, cols, cam_size):
    factor = GRID / cam_size
    map_r = top + (rows + 0.5) * factor
    map_c = left + (cols + 0.5) * factor
    size = tilemap.shape[0]
    new_top = int(np.clip(round(map_r - GRID / 2), 0, size - GRID))
    new_left = int(np.clip(round(map_c - GRID / 2), 0, size - GRID))
    return (new_top, new_left), tilemap[new_top:new_top + GRID,
                                        new_left:new_left + GRID]


def scan_tilemap(tilemap, session, cam_session, head,
                 trigger=0.3, decide=0.85, stride=GRID):
    detections = []
    triggered = direct = stitched = 0
    for top, left in target_tiles(tilemap, stride):
        image = norm(tilemap[top:top + GRID, left:left + GRID])
        score = float(predict_single(session, image)[HK])
        if score < trigger:
            continue
        if score >= decide:
            final = float(predict_window(session, image)[HK])
            if final >= decide:
                direct += 1
                detections.append((top, left, final, False))
            continue
        triggered += 1
        cam, _ = cam_heatmap(cam_session, head, image, HK)
        rows, cols = cam_centroid(cam)
        (new_top, new_left), crop = recentered_crop(
            tilemap, top, left, rows, cols, cam.shape[0])
        final = float(predict_window(session, norm(crop))[HK])
        if final >= decide:
            stitched += 1
            detections.append((new_top, new_left, final, True))
    return detections, {"triggered": triggered, "direct": direct,
                        "stitched": stitched}
