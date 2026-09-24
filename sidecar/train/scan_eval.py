import argparse
import json
import sys
import time
from multiprocessing import Pool
from pathlib import Path

import numpy as np
import onnx
import onnxruntime as ort
from generator.areas import make_area
from generator.rng import rng_for

from vistructum_ml import features, scoring
from vistructum_ml.contract import GRID, META_MIN_VOTES, META_PREFILTER, META_THRESHOLD
from vistructum_ml.detect import clusters
from vistructum_ml.gates import SCAN_GATES, scan_verdict
from vistructum_ml.metadata import read_session_metadata, validate

AREA_SIZE = {"mask": 128, "fullscan": 256}
THRESHOLDS = tuple(float(t) for t in np.round(np.concatenate([
    np.arange(0.30, 0.90, 0.05), np.arange(0.90, 0.99, 0.01), np.arange(0.99, 0.9999, 0.001)]), 4))
VOTES = (1, 2, 3)
META_SCAN = "vistructum.scan_calibration"
META_PREFILTER_SCAN = "vistructum.prefilter_calibration"
CALIBRATION_MARGIN = 0.6
PREFILTERS = tuple(float(p) for p in np.round(np.arange(0.02, 0.99, 0.02), 2))
PREFILTER_MAX_RECALL_LOSS = 0.005

_session = None
_meta = None


def _init(model_path):
    global _session, _meta
    options = ort.SessionOptions()
    options.intra_op_num_threads = 1
    options.inter_op_num_threads = 1
    _session = ort.InferenceSession(model_path, options, providers=["CPUExecutionProvider"])
    _meta = validate(read_session_metadata(_session))


def _scan(task):
    """full: every window gets the single-view score and, with tta, the 8-view score (for calibrating the cascade);
    otherwise the sidecar's scoring with the model's own prefilter"""
    kind, seed, split, index, n_symbols, holdout, size, full = task
    scene, truth, biome = make_area(rng_for(seed, split, index), kind, size, n_symbols, holdout)
    positions, batch = features.windows(kind, features.extract(kind, scene))
    area = {"positions": positions, "truth": truth, "biome": biome}
    if full and _meta["tta"]:
        area["single"] = scoring.single_view(_session, batch).astype(np.float32)
        area["scores"] = scoring.all_views(_session, batch).astype(np.float32)
        area["refined"] = len(batch)
    else:
        prefilter = None if full else _meta["prefilter"]
        scores, area["refined"] = scoring.score(_session, batch, _meta["tta"], prefilter)
        area["scores"] = scores.astype(np.float32)
    return area


def collect(model_path, kind, seed, split, negatives, positives, workers, holdout=False, size=None, full=False):
    size = size or AREA_SIZE[kind]
    tasks = [(kind, seed, split, i, 0, holdout, size, full) for i in range(negatives)]
    tasks += [(kind, seed, split, negatives + i, 1 + i % 3, holdout, size, full) for i in range(positives)]
    with Pool(workers, initializer=_init, initargs=(str(model_path),)) as pool:
        return pool.map(_scan, tasks, chunksize=4)


def _covers(window, box, min_cover=0.5):
    top, left = window
    rows = max(0, min(top + GRID, box[2]) - max(top, box[0]))
    cols = max(0, min(left + GRID, box[3]) - max(left, box[1]))
    area = (box[2] - box[0]) * (box[3] - box[1])
    return area > 0 and rows * cols / area >= min_cover


def _touches(cluster, box):
    return not (cluster["bottom"] <= box[0] or box[2] <= cluster["top"]
                or cluster["right"] <= box[1] or box[3] <= cluster["left"])


def score_at(results, threshold, min_votes, area_clusters=None):
    windows = false_flags = true_flags = partial_flags = symbols = detected = 0
    subtypes = {}
    if area_clusters is None:
        area_clusters = [clusters(area["positions"], area["scores"], threshold) for area in results]
    for area, found_clusters in zip(results, area_clusters):
        windows += len(area["positions"])
        found = set()
        for cluster in found_clusters:
            if cluster["votes"] < min_votes:
                continue
            matched = {j for j, truth in enumerate(area["truth"])
                       if any(_covers(w, truth["box"]) for w in cluster["windows"])}
            if matched:
                true_flags += 1
                found |= matched
            elif any(_touches(cluster, truth["box"]) for truth in area["truth"]):
                partial_flags += 1
            else:
                false_flags += 1
        for j, truth in enumerate(area["truth"]):
            symbols += 1
            detected += j in found
            family = truth["subtype"].split("-sloppy")[0]
            entry = subtypes.setdefault(family, [0, 0])
            entry[0] += 1
            entry[1] += j in found
    flags = true_flags + partial_flags + false_flags
    return {
        "threshold": threshold,
        "min_votes": min_votes,
        "windows": windows,
        "flags": flags,
        "false_flags": false_flags,
        "false_flags_per_window": false_flags / windows if windows else 0.0,
        "precision": (true_flags + partial_flags) / flags if flags else 1.0,
        "recall": detected / symbols if symbols else 0.0,
        "symbols": symbols,
        "subtype_recall": {k: round(v[1] / v[0], 4) for k, v in sorted(subtypes.items())},
    }


def sweep(results):
    table = []
    for threshold in THRESHOLDS:
        area_clusters = [clusters(area["positions"], area["scores"], threshold) for area in results]
        table += [score_at(results, threshold, votes, area_clusters) for votes in VOTES]
    return table


def calibrate(table, gate, margin=CALIBRATION_MARGIN):
    # the gate allows only a handful of false flags per split, so picking right at the limit fails on the next split
    # by Poisson noise alone; calibrate against a tighter limit
    allowed = [row for row in table if row["false_flags_per_window"] <= gate.max_false_flags_per_window * margin]
    if not allowed:
        return None
    return max(allowed, key=lambda row: (row["recall"], row["threshold"], -row["min_votes"]))


def cascade(results, prefilter):
    """the scores the sidecar would give with this prefilter, from areas collected with full=True"""
    return [{**area, "scores": np.where(area["single"] >= prefilter, area["scores"], area["single"])}
            for area in results]


def refined_fraction(results, prefilter=None, negatives_only=False):
    areas = [area for area in results if not (negatives_only and area["truth"])]
    windows = sum(len(area["positions"]) for area in areas)
    if prefilter is None:
        refined = sum(area["refined"] for area in areas)
    else:
        refined = sum(int((area["single"] >= prefilter).sum()) for area in areas)
    return refined / windows if windows else 0.0


def calibrate_prefilter(results, row, max_loss=PREFILTER_MAX_RECALL_LOSS):
    """the highest prefilter whose cascade keeps the calibrated row's recall within max_loss without adding a false
    flag. Raising the prefilter only drops flags, so recall falls monotonically and the sweep stops at the first miss"""
    best = None
    for prefilter in PREFILTERS:
        if prefilter > row["threshold"]:
            break
        at = score_at(cascade(results, prefilter), row["threshold"], row["min_votes"])
        if at["recall"] < row["recall"] - max_loss or at["false_flags"] > row["false_flags"]:
            break
        best = {"prefilter": prefilter, "recall": at["recall"], "false_flags": at["false_flags"],
                "subtype_recall": at["subtype_recall"], "refined_fraction": refined_fraction(results, prefilter),
                "refined_fraction_negatives": refined_fraction(results, prefilter, negatives_only=True)}
    return best


def write_calibration(model_path, row, split, seed, prefilter=None):
    model = onnx.load(str(model_path))
    props = {p.key: p.value for p in model.metadata_props}
    props[META_THRESHOLD] = f"{row['threshold']:.4f}"
    props[META_MIN_VOTES] = str(row["min_votes"])
    props[META_SCAN] = json.dumps({"split": split, "seed": seed, "windows": row["windows"],
                                   "false_flags": row["false_flags"], "recall": row["recall"]})
    props.pop(META_PREFILTER, None)
    props.pop(META_PREFILTER_SCAN, None)
    if prefilter is not None:
        props[META_PREFILTER] = f"{prefilter['prefilter']:.4f}"
        props[META_PREFILTER_SCAN] = json.dumps({k: prefilter[k] for k in ("recall", "false_flags", "refined_fraction")})
    del model.metadata_props[:]
    for key, value in props.items():
        entry = model.metadata_props.add()
        entry.key = key
        entry.value = value
    onnx.save(model, str(model_path))


def model_kind(model_path):
    session = ort.InferenceSession(str(model_path), providers=["CPUExecutionProvider"])
    return validate(read_session_metadata(session))


def run(model_path, split, negatives, positives, seed, workers, holdout=False, write=False):
    info = model_kind(model_path)
    kind = info["kind"]
    gate = SCAN_GATES[kind]
    started = time.time()
    # calibrating scores every window fully; evaluating runs exactly what the sidecar runs, cascade included
    results = collect(model_path, kind, seed, split, negatives, positives, workers, holdout, full=write)
    table = sweep(results)
    report = {"kind": kind, "split": split, "seed": seed, "holdout": holdout, "negative_areas": negatives,
              "positive_areas": positives, "area_size": AREA_SIZE[kind], "seconds": round(time.time() - started, 1),
              "refined_fraction": refined_fraction(results)}
    if write:
        best = calibrate(table, gate)
        report["calibrated"] = best
        prefilter = calibrate_prefilter(results, best) if best is not None and info["tta"] else None
        report["prefilter"] = prefilter
        if best is not None:
            write_calibration(model_path, best, split, seed, prefilter)
    else:
        current = score_at(results, info["threshold"], info["min_votes"])
        verdict, failures = scan_verdict(current, gate)
        report |= {"at_model_threshold": current, "verdict": verdict, "failures": failures}
    report["sweep"] = [{k: row[k] for k in ("threshold", "min_votes", "false_flags", "false_flags_per_window",
                                            "precision", "recall")} for row in table]
    return report


def main():
    parser = argparse.ArgumentParser(description="scan-level evaluation on generated areas, using the sidecar's "
                                                 "window/cluster logic; --calibrate writes threshold+min_votes")
    parser.add_argument("model")
    parser.add_argument("--split", choices=("calib", "scan"), default="scan")
    parser.add_argument("--negatives", type=int, default=2000, help="areas without symbols")
    parser.add_argument("--positives", type=int, default=600, help="areas with 1-3 symbols")
    parser.add_argument("--seed", type=int, default=0)
    parser.add_argument("--workers", type=int, default=4)
    parser.add_argument("--holdout", action="store_true", help="use the shifted holdout distribution")
    parser.add_argument("--calibrate", action="store_true", help="pick threshold/min_votes and write them into MODEL")
    parser.add_argument("--out", default=None)
    args = parser.parse_args()
    split = "calib" if args.calibrate else args.split
    report = run(Path(args.model), split, args.negatives, args.positives, args.seed, args.workers,
                 args.holdout, args.calibrate)
    text = json.dumps(report, indent=2)
    if args.out:
        Path(args.out).write_text(text)
    summary = {k: v for k, v in report.items() if k != "sweep"}
    print(json.dumps(summary, indent=2))
    if args.calibrate:
        return 0 if report["calibrated"] is not None else 1
    return 0 if report["verdict"] == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
