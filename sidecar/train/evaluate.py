import argparse
import json
import sys
import time
from pathlib import Path

import numpy as np
import onnxruntime as ort
from generator.scenes import POS_BASE_MODES
from generator.shapes import HARD_NEGATIVE_FAMILIES

from vistructum_ml import scoring
from vistructum_ml.contract import OUTPUT_NAME
from vistructum_ml.gates import GATES, metrics_at, verdict
from vistructum_ml.metadata import read_session_metadata, validate

BATCH = 512
LATENCY_RUNS = 20


def load_session(model_path):
    session = ort.InferenceSession(str(model_path), providers=["CPUExecutionProvider"])
    meta = validate(read_session_metadata(session))
    return session, meta


def predict(session, meta, x, batch=BATCH):
    return scoring.score(session, x, meta["tta"], meta["prefilter"], batch)[0]


def latency_per_window(session, x, batch, runs=LATENCY_RUNS):
    name = session.get_inputs()[0].name
    sample = x[:batch]
    if len(sample) < batch:
        reps = -(-batch // len(sample))
        sample = np.concatenate([sample] * reps)[:batch]
    session.run([OUTPUT_NAME], {name: sample})
    start = time.perf_counter()
    for _ in range(runs):
        session.run([OUTPUT_NAME], {name: sample})
    return (time.perf_counter() - start) / runs / batch


MODES = sorted(set(POS_BASE_MODES) | {"raised-diff"}, key=len, reverse=True)
FAMILIES = sorted(HARD_NEGATIVE_FAMILIES, key=len, reverse=True)


def _longest_prefix(text, options):
    return next((option for option in options if text == option or text.startswith(option + "-")), None)


def subtype_groups(name):
    if name.startswith("pos-"):
        rest = name[4:]
        return "pos", _longest_prefix(rest, MODES) or rest
    if name.startswith("neg-hard-"):
        rest = name[9:]
        family = _longest_prefix(rest, FAMILIES) or rest
        remainder = rest[len(family) + 1:]
        return f"neg-hard-{family}", _longest_prefix(remainder, MODES) or "plain"
    return name, "plain"


def _rates(flagged, positive, mask):
    n = int(mask.sum())
    if positive[mask].any():
        return {"n": n, "recall": round(float(flagged[mask & positive].mean()), 4)}
    return {"n": n, "fp_rate": round(float(flagged[mask].mean()), 5)}


def subtype_breakdown(scores, y, subtype, threshold):
    flagged = scores >= threshold
    positive = y == 1
    groups = [subtype_groups(str(name)) for name in subtype.tolist()]
    shapes = np.array([g[0] for g in groups])
    modes = np.array([f"{'pos' if g[0] == 'pos' else 'neg'}:{g[1]}" for g in groups])
    return {
        "by_shape": {name: _rates(flagged, positive, shapes == name) for name in sorted(set(shapes.tolist()))},
        "by_mode": {name: _rates(flagged, positive, modes == name) for name in sorted(set(modes.tolist()))},
    }


def evaluate_split(session, meta, data_dir, split):
    raw = np.load(Path(data_dir) / f"{split}.npz")
    x, y = raw["x"], raw["y"]
    scores = predict(session, meta, x)
    metrics = metrics_at(scores, y, meta["threshold"])
    gate = GATES[meta["kind"]]
    status, failures = verdict(metrics, gate)
    result = {"metrics": metrics, "verdict": status, "failures": failures}
    if "subtype" in raw:
        result["subtypes"] = subtype_breakdown(scores, y, raw["subtype"], meta["threshold"])
    return result


def build_report(session, meta, data_dir, splits):
    first_x = np.load(Path(data_dir) / f"{splits[0]}.npz")["x"]
    report = {
        "kind": meta["kind"],
        "version": meta["version"],
        "threshold": meta["threshold"],
        "latency_seconds_per_window_b1": latency_per_window(session, first_x, 1),
        "latency_seconds_per_window_b32": latency_per_window(session, first_x, 32),
        "splits": {},
    }
    for split in splits:
        report["splits"][split] = evaluate_split(session, meta, data_dir, split)
    return report


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("model")
    parser.add_argument("data_dir")
    parser.add_argument("--splits", nargs="+", default=["test", "holdout"])
    parser.add_argument("--out", default=None)
    args = parser.parse_args()
    session, meta = load_session(args.model)
    report = build_report(session, meta, args.data_dir, args.splits)
    report["model_bytes"] = Path(args.model).stat().st_size
    text = json.dumps(report, indent=2)
    if args.out:
        Path(args.out).write_text(text)
    print(text)
    test_verdict = report["splits"].get("test", {}).get("verdict")
    return 1 if test_verdict == "FAIL" else 0


if __name__ == "__main__":
    sys.exit(main())
