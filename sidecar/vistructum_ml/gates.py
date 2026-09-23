from dataclasses import dataclass

import numpy as np

from .contract import FULLSCAN, MASK, POSITIVE


@dataclass(frozen=True)
class Gate:
    min_precision: float
    max_fp_rate: float
    min_recall: float
    min_negatives: int


GATES = {
    MASK.name: Gate(min_precision=0.95, max_fp_rate=0.001, min_recall=0.90, min_negatives=5000),
    FULLSCAN.name: Gate(min_precision=0.90, max_fp_rate=0.001, min_recall=0.60, min_negatives=5000),
}


def metrics_at(scores, labels, threshold):
    scores = np.asarray(scores, dtype=np.float64)
    positive = np.asarray(labels) == POSITIVE
    flagged = scores >= threshold
    true_positives = int((flagged & positive).sum())
    false_positives = int((flagged & ~positive).sum())
    n_positive = int(positive.sum())
    n_negative = int((~positive).sum())
    n_flagged = true_positives + false_positives
    return {
        "threshold": float(threshold),
        "precision": true_positives / n_flagged if n_flagged else 1.0,
        "recall": true_positives / n_positive if n_positive else 0.0,
        "fp_rate": false_positives / n_negative if n_negative else 0.0,
        "false_positives": false_positives,
        "flagged": n_flagged,
        "positives": n_positive,
        "negatives": n_negative,
    }


def select_threshold(scores, labels, gate):
    for threshold in np.round(np.arange(0.05, 1.0, 0.01), 2):
        metrics = metrics_at(scores, labels, threshold)
        if metrics["precision"] >= gate.min_precision and metrics["fp_rate"] <= gate.max_fp_rate:
            return float(threshold)
    return None


def verdict(metrics, gate):
    failures = []
    if metrics["negatives"] < gate.min_negatives:
        failures.append(f"negatives {metrics['negatives']} < {gate.min_negatives}")
    if metrics["precision"] < gate.min_precision:
        failures.append(f"precision {metrics['precision']:.4f} < {gate.min_precision}")
    if metrics["fp_rate"] > gate.max_fp_rate:
        failures.append(f"fp_rate {metrics['fp_rate']:.5f} > {gate.max_fp_rate}")
    if metrics["recall"] < gate.min_recall:
        failures.append(f"recall {metrics['recall']:.4f} < {gate.min_recall}")
    return ("PASS" if not failures else "FAIL"), failures
