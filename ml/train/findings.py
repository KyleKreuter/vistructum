import argparse
import base64
import json
import sys
from pathlib import Path

import numpy as np

from vistructum_ml import features
from vistructum_ml.contract import GRID, KINDS, LABELS, POSITIVE
from vistructum_ml.scene import Scene

VERDICT_LABELS = {"CONFIRMED": POSITIVE, "FALSE_ALARM": LABELS.index("ok")}
CHANNELS = (("blocks", "<i2"), ("heights", "<i2"), ("luminance", np.uint8), ("modified", np.uint8))


def read_lines(paths):
    for path in paths:
        with open(path, encoding="utf-8") as handle:
            for line in handle:
                if line.strip():
                    yield json.loads(line)


def decode_scene(line):
    width, height = int(line["width"]), int(line["height"])
    arrays = {}
    for name, dtype in CHANNELS:
        raw = np.frombuffer(base64.b64decode(line[name]), dtype=dtype)
        if raw.size != width * height:
            raise ValueError(f"finding {line['finding']}: {name} has {raw.size} values, expected {width * height}")
        arrays[name] = raw.reshape(height, width)
    return Scene(**arrays)


def window_origin(length, start, end):
    return int(np.clip((start + end) // 2 - GRID // 2, 0, length - GRID))


def sample(line):
    kind = line["kind"]
    padded = features.pad_to_grid(kind, features.extract(kind, decode_scene(line)))
    _, rows, cols = padded.shape
    window = line["window"]
    top = window_origin(rows, window["top"], window["bottom"])
    left = window_origin(cols, window["left"], window["right"])
    return padded[:, top:top + GRID, left:left + GRID].astype(np.uint8)


def convert(paths, out_dir, split):
    lines = list(read_lines(paths))
    if not lines:
        raise ValueError("no findings in " + ", ".join(str(path) for path in paths))
    kinds = {line["kind"] for line in lines}
    if len(kinds) != 1:
        raise ValueError(f"findings of several kinds {sorted(kinds)}, convert one kind per split")
    kind = kinds.pop()
    if kind not in KINDS:
        raise ValueError(f"unknown kind {kind}")
    x = np.stack([sample(line) for line in lines])
    y = np.array([VERDICT_LABELS[line["verdict"]] for line in lines], dtype=np.int64)
    subtype = np.array([f"finding-{line['source']}-{line['verdict'].lower().replace('_', '-')}" for line in lines])
    finding = np.array([int(line["finding"]) for line in lines], dtype=np.int64)
    out = Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    target = out / f"{split}.npz"
    np.savez_compressed(target, x=x, y=y, subtype=subtype, finding=finding)
    return target, kind, int((y == POSITIVE).sum()), int((y != POSITIVE).sum())


def main():
    parser = argparse.ArgumentParser(description="convert reviewed findings from /vis export into a training split")
    parser.add_argument("inputs", nargs="+", help="<kind>.jsonl files of one model kind")
    parser.add_argument("--out", required=True)
    parser.add_argument("--split", default="findings")
    args = parser.parse_args()
    target, kind, positives, negatives = convert(args.inputs, args.out, args.split)
    print(f"{target}: kind {kind}, {positives} confirmed, {negatives} false alarms", file=sys.stderr)


if __name__ == "__main__":
    main()
