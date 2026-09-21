import argparse
import json
import sys
from pathlib import Path

import numpy as np
import onnxruntime as ort
from PIL import Image, ImageDraw

from fusion import cam_session
from stitch import HK, norm, scan_tilemap, target_tiles
from tilemap import generate_ground_tilemap, generate_wall_tilemap, load_ground_pool

GRID = 64


def box_hit(truth, top, left):
    cy, cx = top + GRID / 2, left + GRID / 2
    for box in truth:
        if box["top"] <= cy < box["top"] + box["size"] and \
           box["left"] <= cx < box["left"] + box["size"]:
            return True
    return False


def symbol_triggered(truth_box, session, tilemap, trigger, stride):
    cy = truth_box["top"] + truth_box["size"] / 2
    cx = truth_box["left"] + truth_box["size"] / 2
    from fusion import predict_single
    hits = 0
    for top, left in target_tiles(tilemap, stride):
        if not (top <= cy < top + GRID and left <= cx < left + GRID):
            continue
        image = norm(tilemap[top:top + GRID, left:left + GRID])
        if float(predict_single(session, image)[HK]) >= trigger:
            hits += 1
    return hits > 0


def iou(first, second):
    top = max(first[0], second[0])
    left = max(first[1], second[1])
    bottom = min(first[0] + GRID, second[0] + GRID)
    right = min(first[1] + GRID, second[1] + GRID)
    inter = max(0, bottom - top) * max(0, right - left)
    return inter / (2 * GRID * GRID - inter)


def suppress(detections, iou_limit=0.3):
    kept = []
    for candidate in sorted(detections, key=lambda row: row[2], reverse=True):
        if all(iou(candidate, done) <= iou_limit for done in kept):
            kept.append(candidate)
    return kept


def draw_boxes(tilemap, detections, truth, scale):
    canvas = Image.fromarray(tilemap).convert("RGB").resize(
        (tilemap.shape[1] * scale, tilemap.shape[0] * scale), Image.NEAREST)
    painter = ImageDraw.Draw(canvas)
    for box in truth:
        top, left, size = (int(value * scale) for value in
                           (box["top"], box["left"], box["size"]))
        painter.rectangle([left, top, left + size, top + size],
                          outline=(0, 255, 0), width=2)
    for top, left, score, _ in detections:
        top, left = top * scale, left * scale
        edge = GRID * scale
        painter.rectangle([left, top, left + edge, top + edge],
                          outline=(255, 0, 0), width=2)
        painter.text((left + 3, max(0, top - 12)), f"{score * 100:.0f}%",
                     fill=(255, 0, 0))
    return canvas


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", default="models-r6m-mc/bf-bin-1.onnx")
    parser.add_argument("--map", default=None)
    parser.add_argument("--generate", action="store_true")
    parser.add_argument("--wall", action="store_true")
    parser.add_argument("--size", type=int, default=512)
    parser.add_argument("--symbols", type=int, default=6)
    parser.add_argument("--seed", type=int, default=7)
    parser.add_argument("--maps", type=int, default=6)
    parser.add_argument("--trigger", type=float, default=0.3)
    parser.add_argument("--decide", type=float, default=0.85)
    parser.add_argument("--stride", type=int, default=32)
    parser.add_argument("--scale", type=int, default=2)
    parser.add_argument("--out", default="detections.png")
    args = parser.parse_args()
    root = Path(__file__).resolve().parent
    session = ort.InferenceSession(str(root / args.model),
                                   providers=["CPUExecutionProvider"])
    cam, head = cam_session(str(root / args.model))
    pool = None
    if args.map is None:
        pool = load_ground_pool(root / "data-r3")
        if not pool:
            raise SystemExit("no backgrounds in data-r3")
    rng = np.random.default_rng(args.seed)
    generate = generate_wall_tilemap if args.wall else generate_ground_tilemap
    detected = triggered = total = false_positives = 0
    maps = args.maps if args.map is None else 1
    for index in range(maps):
        if args.map:
            tilemap = np.load(args.map)
            truth = []
        else:
            tilemap, truth = generate(rng, pool, args.size, args.symbols)
        detections, _ = scan_tilemap(tilemap, session, cam, head,
                                     args.trigger, args.decide, args.stride)
        detections = suppress(detections)
        for top, left, score, via_stitch in detections:
            kind = "stitch" if via_stitch else "direct"
            print(f"hakenkreuz {score * 100:.1f}% @ ({top}, {left}) [{kind}]")
            if box_hit(truth, top, left):
                detected += 1
            else:
                false_positives += 1
        for box in truth:
            total += 1
            if symbol_triggered(box, session, tilemap, args.trigger,
                                 args.stride):
                triggered += 1
        if index == 0:
            draw_boxes(tilemap, detections, truth,
                       args.scale).save(args.out)
    print(f"trigger_recall {triggered}/{total} = {triggered / total:.3f}")
    print(f"detection_rate {detected}/{total} = {detected / total:.3f}")
    print(f"fp_per_map {false_positives / maps:.2f} -> {args.out}")


if __name__ == "__main__":
    sys.exit(main())
