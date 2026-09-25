import numpy as np

from vistructum_ml.gates import GATES, Gate, metrics_at, select_threshold, verdict


def test_metrics_at_counts():
    m = metrics_at([0.9, 0.8, 0.2, 0.95], [1, 0, 0, 1], 0.5)
    assert m["precision"] == 2 / 3 and m["recall"] == 1.0 and m["fp_rate"] == 0.5


def test_select_threshold_respects_fp_budget():
    rng = np.random.default_rng(0)
    neg = rng.uniform(0, 0.7, 10000)
    pos = rng.uniform(0.5, 1.0, 1000)
    scores = np.concatenate([neg, pos])
    labels = np.concatenate([np.zeros(10000), np.ones(1000)])
    gate = Gate(min_precision=0.9, max_fp_rate=0.001, min_recall=0.1, min_negatives=1000)
    t = select_threshold(scores, labels, gate)
    assert t is not None and t >= 0.69
    status, _ = verdict(metrics_at(scores, labels, t), gate)
    assert status == "PASS"


def test_verdict_rejects_small_negative_sets():
    m = metrics_at([0.9], [1], 0.5)
    status, reasons = verdict(m, GATES["mask"])
    assert status == "FAIL" and any("negatives" in r for r in reasons)
