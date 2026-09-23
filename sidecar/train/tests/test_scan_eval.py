import numpy as np
from generator.areas import make_area
from generator.rng import rng_for
from scan_eval import calibrate, score_at

from vistructum_ml.gates import ScanGate


def area(positions, scores, boxes=()):
    return {"positions": positions, "scores": np.array(scores, dtype=np.float32),
            "truth": [{"box": b, "subtype": "pos-raised"} for b in boxes], "biome": "plains"}


def test_isolated_false_hit_is_removed_by_votes():
    results = [area([(0, 0), (0, 96)], [0.95, 0.1]), area([(0, 0), (0, 24)], [0.9, 0.9], [(10, 20, 40, 50)])]
    one = score_at(results, 0.5, 1)
    two = score_at(results, 0.5, 2)
    assert one["false_flags"] == 1 and one["recall"] == 1.0
    assert two["false_flags"] == 0 and two["recall"] == 1.0


def test_partial_overlap_is_not_a_false_flag():
    results = [area([(0, 0)], [0.9], [(50, 50, 110, 110)])]
    row = score_at(results, 0.5, 1)
    assert row["false_flags"] == 0 and row["recall"] == 0.0 and row["precision"] == 1.0


def test_calibrate_maximizes_recall_within_budget():
    table = [
        {"threshold": 0.5, "min_votes": 1, "false_flags_per_window": 1e-3, "recall": 0.99},
        {"threshold": 0.9, "min_votes": 1, "false_flags_per_window": 1e-5, "recall": 0.7},
        {"threshold": 0.7, "min_votes": 2, "false_flags_per_window": 2e-5, "recall": 0.8},
    ]
    best = calibrate(table, ScanGate(max_false_flags_per_window=3e-5, min_recall=0.5, min_windows=1))
    assert (best["threshold"], best["min_votes"]) == (0.7, 2)
    assert calibrate(table[:1], ScanGate(3e-5, 0.5, 1)) is None


def test_areas_place_disjoint_symbols_with_truth_boxes():
    for kind, size in (("fullscan", 256), ("mask", 128)):
        scene, truth, _ = make_area(rng_for(3, "scan", 7), kind, size, 3)
        assert scene.shape == (size, size)
        assert 1 <= len(truth) <= 3
        for i, a in enumerate(truth):
            assert 0 <= a["box"][0] < a["box"][2] <= size and 0 <= a["box"][1] < a["box"][3] <= size
            for b in truth[i + 1:]:
                assert a["box"][2] <= b["box"][0] or b["box"][2] <= a["box"][0] \
                    or a["box"][3] <= b["box"][1] or b["box"][3] <= a["box"][1]
            if kind == "mask":
                r0, c0, r1, c1 = a["box"]
                assert scene.modified[r0:r1, c0:c1].sum() >= 0.8 * a["cells"]


def test_negative_areas_have_no_truth():
    _, truth, _ = make_area(rng_for(3, "scan", 1), "fullscan", 256, 0)
    assert truth == []
