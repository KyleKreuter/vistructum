import argparse
import json
import sys
import time
from pathlib import Path

import numpy as np
import onnxruntime as ort

from vistructum_ml.contract import OUTPUT_NAME
from vistructum_ml.gates import GATES, metrics_at, verdict
from vistructum_ml.metadata import read_session_metadata, validate

BATCH = 512
LATENCY_RUNS = 20


def load_session(model_path):
    session = ort.InferenceSession(str(model_path), providers=["CPUExecutionProvider"])
    meta = validate(read_session_metadata(session))
    return session, meta


def predict(session, x, batch=BATCH):
    name = session.get_inputs()[0].name
    scores = np.empty(len(x), dtype=np.float64)
    for i in range(0, len(x), batch):
        chunk = x[i:i + batch]
        probs = session.run([OUTPUT_NAME], {name: chunk})[0]
        scores[i:i + len(chunk)] = probs[:, 1]
    return scores


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


def subtype_breakdown(scores, y, subtype, threshold):
    flagged = scores >= threshold
    positive = y == 1
    out = {}
    for name in sorted(set(subtype.tolist())):
        mask = subtype == name
        n = int(mask.sum())
        if positive[mask].any():
            out[name] = {"n": n, "recall": float(flagged[mask & positive].mean())}
        else:
            out[name] = {"n": n, "fp_rate": float(flagged[mask].mean())}
    return out


def evaluate_split(session, meta, data_dir, split):
    raw = np.load(Path(data_dir) / f"{split}.npz")
    x, y = raw["x"], raw["y"]
    scores = predict(session, x)
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
