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


def held_out(labels, fraction, seed):
    rng = np.random.default_rng(seed)
    mask = np.zeros(len(labels), dtype=bool)
    for label in np.unique(labels):
        index = np.flatnonzero(labels == label)
        rng.shuffle(index)
        mask[index[:round(len(index) * fraction)]] = True
    return mask


def save(out, name, x, y, subtype, finding):
    target = out / f"{name}.npz"
    np.savez_compressed(target, x=x, y=y, subtype=subtype, finding=finding)
    return target


def convert(paths, out_dir, split, val_fraction=0.0, seed=0):
    if not 0.0 <= val_fraction < 1.0:
        raise ValueError(f"val_fraction must be in [0, 1), got {val_fraction}")
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
    if val_fraction == 0.0:
        target = save(out, split, x, y, subtype, finding)
        return target, kind, int((y == POSITIVE).sum()), int((y != POSITIVE).sum())
    val = held_out(y, val_fraction, seed)
    save(out, f"{split}-val", x[val], y[val], subtype[val], finding[val])
    target = save(out, f"{split}-train", x[~val], y[~val], subtype[~val], finding[~val])
    return target, kind, int((y == POSITIVE).sum()), int((y != POSITIVE).sum())


def main():
    parser = argparse.ArgumentParser(description="convert reviewed findings from /vis export into a training split")
    parser.add_argument("inputs", nargs="+", help="<kind>.jsonl files of one model kind")
    parser.add_argument("--out", required=True)
    parser.add_argument("--split", default="findings")
    parser.add_argument("--val-fraction", type=float, default=0.0,
                        help="hold out this share of each label as <split>-val, the rest becomes <split>-train")
    parser.add_argument("--seed", type=int, default=0)
    args = parser.parse_args()
    target, kind, positives, negatives = convert(args.inputs, args.out, args.split, args.val_fraction, args.seed)
    print(f"{target}: kind {kind}, {positives} confirmed, {negatives} false alarms", file=sys.stderr)


if __name__ == "__main__":
    main()
