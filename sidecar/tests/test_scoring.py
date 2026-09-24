import numpy as np
import pytest

from vistructum_ml import scoring
from vistructum_ml.contract import (
    FEATURE_SPEC,
    LABELS,
    META_FEATURE_SPEC,
    META_KIND,
    META_LABELS,
    META_PREFILTER,
    META_THRESHOLD,
    META_TTA,
    META_VERSION,
    OUTPUT_NAME,
)
from vistructum_ml.metadata import ContractError, validate


class FakeSession:
    """scores a window by the value of its top-left pixel, so a window's views score differently"""

    def __init__(self):
        self.rows = 0

    def run(self, outputs, feeds):
        assert outputs == [OUTPUT_NAME]
        x = next(iter(feeds.values()))
        self.rows += len(x)
        p = x[:, 0, 0, 0].astype(np.float64) / 100.0
        return [np.stack([1.0 - p, p], axis=1)]


def windows(corners):
    x = np.zeros((len(corners), 1, 4, 4), dtype=np.uint8)
    x[:, 0, 0, 0] = corners
    return x


def test_d4_views_matches_the_training_order():
    x = np.arange(2 * 1 * 3 * 3, dtype=np.uint8).reshape(2, 1, 3, 3)
    views = scoring.d4_views(x).reshape(8, 2, 1, 3, 3)
    assert np.array_equal(views[0], x)
    assert np.array_equal(views[1], x[..., ::-1])
    assert np.array_equal(views[4], x.transpose(0, 1, 3, 2))
    # all 8 views of an asymmetric window are distinct
    assert len({v[0].tobytes() for v in views}) == 8


def test_all_views_is_the_mean_over_the_eight_views():
    session = FakeSession()
    x = windows([80, 0, 40])
    scores = scoring.all_views(session, x, batch_size=16)
    # only the identity and the transpose keep the top-left pixel in place, so 2 of the 8 views score it
    assert np.allclose(scores, np.array([80, 0, 40]) / 100.0 * 2 / 8)
    assert session.rows == 24


def test_cascade_refines_only_windows_that_reach_the_prefilter():
    session = FakeSession()
    x = windows([80, 10, 40])
    scores, refined = scoring.score(session, x, tta=True, prefilter=0.3)
    assert refined == 2
    assert np.allclose(scores, [0.8 / 4, 0.1, 0.4 / 4])
    assert session.rows == 3 + 2 * 8


def test_score_without_tta_or_prefilter():
    x = windows([80, 10])
    assert scoring.score(FakeSession(), x)[1] == 0
    scores, refined = scoring.score(FakeSession(), x, tta=True)
    assert refined == 2 and np.allclose(scores, [0.2, 0.025])


def meta(**extra):
    base = {META_VERSION: "bf-scan-2", META_KIND: "fullscan", META_LABELS: '["ok", "hakenkreuz"]',
            META_FEATURE_SPEC: FEATURE_SPEC, META_THRESHOLD: "0.9"}
    assert tuple(LABELS) == ("ok", "hakenkreuz")
    return base | extra


def test_metadata_reads_the_cascade():
    info = validate(meta(**{META_TTA: "1", META_PREFILTER: "0.3"}))
    assert info["tta"] is True and info["prefilter"] == 0.3
    plain = validate(meta())
    assert plain["tta"] is False and plain["prefilter"] is None


@pytest.mark.parametrize("extra", [
    {META_TTA: "yes"},
    {META_PREFILTER: "0.3"},
    {META_TTA: "1", META_PREFILTER: "0.95"},
    {META_TTA: "1", META_PREFILTER: "0"},
])
def test_metadata_rejects_an_inconsistent_cascade(extra):
    with pytest.raises(ContractError):
        validate(meta(**extra))
