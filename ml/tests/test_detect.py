from vistructum_ml.detect import clusters, flagged
from vistructum_ml.gates import SCAN_GATES, scan_verdict
from vistructum_ml.metadata import ContractError, validate


def test_overlapping_hits_form_one_cluster():
    positions = [(0, 0), (0, 24), (0, 200), (24, 24)]
    scores = [0.9, 0.8, 0.95, 0.1]
    result = clusters(positions, scores, 0.5)
    assert [c["votes"] for c in result] == [1, 2]
    assert result[1]["top"] == 0 and result[1]["right"] == 24 + 64


def test_min_votes_drops_isolated_hits():
    positions = [(0, 0), (0, 24), (0, 200)]
    scores = [0.9, 0.8, 0.95]
    assert len(flagged(positions, scores, 0.5, 2)) == 1
    assert len(flagged(positions, scores, 0.5, 1)) == 2


def test_windows_touching_edges_do_not_overlap():
    assert len(clusters([(0, 0), (0, 64)], [0.9, 0.9], 0.5)) == 2


def test_min_votes_metadata_defaults_and_validates():
    meta = {
        "vistructum.model_version": "bf-mask-2",
        "vistructum.kind": "mask",
        "vistructum.labels": '["ok", "hakenkreuz"]',
        "vistructum.feature_spec": "fs-1",
        "vistructum.threshold": "0.7",
    }
    assert validate(meta)["min_votes"] == 1
    assert validate(meta | {"vistructum.min_votes": "2"})["min_votes"] == 2
    try:
        validate(meta | {"vistructum.min_votes": "0"})
    except ContractError:
        pass
    else:
        raise AssertionError("min_votes 0 accepted")


def test_scan_verdict():
    gate = SCAN_GATES["fullscan"]
    good = {"windows": 200000, "false_flags_per_window": 1e-5, "recall": 0.6}
    assert scan_verdict(good, gate)[0] == "PASS"
    assert scan_verdict(good | {"recall": 0.3}, gate)[0] == "FAIL"
