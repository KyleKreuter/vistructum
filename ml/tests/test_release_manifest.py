import hashlib
from pathlib import Path

import pytest

from release_manifest import manifest

MODELS = Path(__file__).resolve().parents[2] / "models"


def test_manifest_lists_every_bundled_model():
    models = manifest(MODELS)["models"]
    assert [m["file"] for m in models] == sorted(p.name for p in MODELS.glob("*.onnx"))
    for m in models:
        assert m["sha256"] == hashlib.sha256((MODELS / m["file"]).read_bytes()).hexdigest()
        assert m["kind"] in {"mask", "fullscan"}
        assert m["model_version"]
        assert m["feature_spec"] == "fs-1"


def test_manifest_rejects_an_empty_directory(tmp_path):
    with pytest.raises(SystemExit):
        manifest(tmp_path)
