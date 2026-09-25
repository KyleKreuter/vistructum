import json

import pytest

parity = pytest.importorskip("parity")

STALE = "stale parity fixture, regenerate with: PYTHONPATH=ml:ml/train python3 ml/parity.py"


@pytest.mark.parametrize("case", parity.CASES, ids=[case[0] for case in parity.CASES])
def test_committed_fixtures_match_the_python_runtime(case):
    index = parity.CASES.index(case)
    name = case[0]
    committed = json.loads((parity.FIXTURES / f"{name}.json").read_text())
    fresh = parity.fixture(index, *case)
    assert committed["model_sha256"] == fresh["model_sha256"], STALE
    for key in ("blocks", "heights", "luminance", "modified", "features", "positions", "refined", "detections"):
        assert committed[key] == fresh[key], f"{key}: {STALE}"
    assert committed["scores"] == pytest.approx(fresh["scores"], abs=1e-6), STALE
