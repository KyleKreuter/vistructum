import argparse
import json
import subprocess
import sys
import time
from collections import Counter
from multiprocessing import Pool
from pathlib import Path

import numpy as np

from .rng import rng_for, sample_seed, split_offset
from .scenes import MAKE_SAMPLE

GENERATOR_VERSION = "gen-1"

SPLITS = ("train", "val", "test", "holdout")
DEFAULT_COUNTS = {"train": 60000, "val": 12500, "test": 12500, "holdout": 12500}
POSITIVE_RATIO = {"train": 0.5, "val": 0.2, "test": 0.2, "holdout": 0.2}


def _labels_for(n, ratio, seed, split):
    n_pos = round(n * ratio)
    labels = np.zeros(n, dtype=np.int64)
    labels[:n_pos] = 1
    label_tag = 0x1ABE15
    perm_rng = np.random.default_rng(np.random.SeedSequence((int(seed), split_offset(split), label_tag)))
    perm_rng.shuffle(labels)
    return labels


def _worker(args):
    kind, seed, split, index, label, holdout = args
    rng = rng_for(seed, split, index)
    x, y, subtype, biome, _vis = MAKE_SAMPLE[kind](rng, label, holdout=holdout)
    return x, y, subtype, biome, sample_seed(seed, split, index)


def _git_commit():
    try:
        out = subprocess.run(["git", "rev-parse", "HEAD"], cwd=Path(__file__).resolve().parent,
                              capture_output=True, text=True, check=True)
        return out.stdout.strip()
    except (OSError, subprocess.CalledProcessError):
        return None


def build_split(kind, seed, split, n, workers):
    ratio = POSITIVE_RATIO[split]
    labels = _labels_for(n, ratio, seed, split)
    holdout = split == "holdout"
    tasks = [(kind, seed, split, i, int(labels[i]), holdout) for i in range(n)]

    t0 = time.time()
    if workers > 1:
        with Pool(workers) as pool:
            results = pool.map(_worker, tasks, chunksize=max(1, n // (workers * 8) or 1))
    else:
        results = [_worker(t) for t in tasks]
    elapsed = time.time() - t0

    xs = np.stack([r[0] for r in results]).astype(np.uint8)
    ys = np.array([r[1] for r in results], dtype=np.int64)
    subtypes = np.array([r[2] for r in results])
    seeds = np.array([r[4] for r in results], dtype=np.int64)

    counter = Counter(subtypes.tolist())
    rate = n / elapsed if elapsed > 0 else float("inf")
    return {"x": xs, "y": ys, "subtype": subtypes, "seed": seeds}, {
        "count": n,
        "positives": int((ys == 1).sum()),
        "negatives": int((ys == 0).sum()),
        "subtypes": dict(sorted(counter.items())),
        "seconds": elapsed,
        "samples_per_second": rate,
    }


def main():
    parser = argparse.ArgumentParser(description="build block-level scene datasets for mask/fullscan models")
    parser.add_argument("--kind", choices=("mask", "fullscan"), required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--train", type=int, default=DEFAULT_COUNTS["train"])
    parser.add_argument("--val", type=int, default=DEFAULT_COUNTS["val"])
    parser.add_argument("--test", type=int, default=DEFAULT_COUNTS["test"])
    parser.add_argument("--holdout", type=int, default=DEFAULT_COUNTS["holdout"])
    parser.add_argument("--workers", type=int, default=4)
    parser.add_argument("--seed", type=int, default=0)
    args = parser.parse_args()

    counts = {"train": args.train, "val": args.val, "test": args.test, "holdout": args.holdout}
    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)

    manifest = {
        "kind": args.kind,
        "feature_spec": "fs-1",
        "generator_version": GENERATOR_VERSION,
        "git_commit": _git_commit(),
        "cli_args": vars(args),
        "holdout_only_hard_negative": "windmill-3",
        "splits": {},
    }

    for split in SPLITS:
        n = counts[split]
        print(f"building {split}: n={n} kind={args.kind}", file=sys.stderr)
        data, stats = build_split(args.kind, args.seed, split, n, args.workers)
        np.savez_compressed(out_dir / f"{split}.npz", x=data["x"], y=data["y"],
                             subtype=data["subtype"], seed=data["seed"])
        manifest["splits"][split] = stats
        print(f"  {n} samples in {stats['seconds']:.1f}s ({stats['samples_per_second']:.1f}/s)", file=sys.stderr)

    with open(out_dir / "manifest.json", "w") as f:
        json.dump(manifest, f, indent=2)


if __name__ == "__main__":
    main()
