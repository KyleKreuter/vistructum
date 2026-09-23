import base64
import importlib
import json

import numpy as np
import onnx
import pytest
from fastapi.testclient import TestClient
from onnx import TensorProto, helper

GRID = 64


def b64_int16(values):
    return base64.b64encode(np.asarray(values, dtype="<i2").tobytes()).decode("ascii")


def b64_uint8(values):
    return base64.b64encode(np.asarray(values, dtype=np.uint8).tobytes()).decode("ascii")


def build_onnx_model(path, channels, kind, version, threshold=0.5, feature_spec="fs-1",
                      labels=("ok", "hakenkreuz"), commit="deadbeef", drop_key=None, min_votes=None):
    features_in = helper.make_tensor_value_info("features", TensorProto.UINT8, ["N", channels, GRID, GRID])
    scores_out = helper.make_tensor_value_info("scores", TensorProto.FLOAT, ["N", 2])
    cast = helper.make_node("Cast", ["features"], ["feat_f"], to=TensorProto.FLOAT)
    mean = helper.make_node("ReduceMean", ["feat_f"], ["mean"], axes=[1, 2, 3], keepdims=1)
    shape_init = helper.make_tensor("shape_const", TensorProto.INT64, [2], [-1, 1])
    reshape = helper.make_node("Reshape", ["mean", "shape_const"], ["mean2d"])
    one_init = helper.make_tensor("one_const", TensorProto.FLOAT, [1], [1.0])
    sub = helper.make_node("Sub", ["one_const", "mean2d"], ["neg"])
    concat = helper.make_node("Concat", ["neg", "mean2d"], ["logits"], axis=1)
    softmax = helper.make_node("Softmax", ["logits"], ["scores"], axis=1)
    graph = helper.make_graph(
        [cast, mean, reshape, sub, concat, softmax],
        "tiny",
        [features_in],
        [scores_out],
        initializer=[shape_init, one_init],
    )
    model = helper.make_model(graph, opset_imports=[helper.make_opsetid("", 13)])
    model.ir_version = 8
    meta = {
        "vistructum.model_version": version,
        "vistructum.kind": kind,
        "vistructum.labels": json.dumps(list(labels)),
        "vistructum.feature_spec": feature_spec,
        "vistructum.threshold": str(threshold),
        "vistructum.commit": commit,
    }
    if min_votes is not None:
        meta["vistructum.min_votes"] = str(min_votes)
    if drop_key:
        del meta[drop_key]
    helper.set_model_props(model, meta)
    onnx.save(model, str(path))


def make_client(monkeypatch, model_dir, ort_threads=None):
    monkeypatch.setenv("MODEL_DIR", str(model_dir))
    if ort_threads is not None:
        monkeypatch.setenv("ORT_THREADS", str(ort_threads))
    import app as app_module
    importlib.reload(app_module)
    return TestClient(app_module.app)


@pytest.fixture
def both_models_dir(tmp_path):
    build_onnx_model(tmp_path / "mask.onnx", 1, "mask", "bf-mask-1", threshold=0.5)
    build_onnx_model(tmp_path / "scan.onnx", 3, "fullscan", "bf-scan-1", threshold=0.5)
    return tmp_path


def test_health_ok_when_both_loaded(monkeypatch, both_models_dir):
    client = make_client(monkeypatch, both_models_dir)
    with client:
        resp = client.get("/health")
        assert resp.status_code == 200
        assert resp.json() == {"status": "ok", "models": {"mask": True, "fullscan": True}}


def test_health_degraded_when_one_missing(monkeypatch, tmp_path):
    build_onnx_model(tmp_path / "mask.onnx", 1, "mask", "bf-mask-1", threshold=0.5)
    client = make_client(monkeypatch, tmp_path)
    with client:
        resp = client.get("/health")
        assert resp.status_code == 200
        body = resp.json()
        assert body["status"] == "degraded"
        assert body["models"] == {"mask": True, "fullscan": False}


def test_health_503_when_none_loaded(monkeypatch, tmp_path):
    client = make_client(monkeypatch, tmp_path)
    with client:
        resp = client.get("/health")
        assert resp.status_code == 503
        assert resp.json()["status"] == "degraded"


def test_invalid_model_is_skipped_not_fatal(monkeypatch, tmp_path):
    build_onnx_model(tmp_path / "mask.onnx", 1, "mask", "bf-mask-1", threshold=0.5)
    build_onnx_model(tmp_path / "broken.onnx", 1, "fullscan", "bf-scan-broken", drop_key="vistructum.threshold")
    client = make_client(monkeypatch, tmp_path)
    with client:
        resp = client.get("/health")
        body = resp.json()
        assert body["models"] == {"mask": True, "fullscan": False}


def test_duplicate_kind_fails_startup(monkeypatch, tmp_path):
    build_onnx_model(tmp_path / "a-mask.onnx", 1, "mask", "bf-mask-1", threshold=0.5)
    build_onnx_model(tmp_path / "b-mask.onnx", 1, "mask", "bf-mask-2", threshold=0.5)
    client = make_client(monkeypatch, tmp_path)
    with pytest.raises(RuntimeError, match="duplicate"), client:
        pass


def test_version_reports_metadata_not_hardcoded(monkeypatch, both_models_dir):
    client = make_client(monkeypatch, both_models_dir)
    with client:
        resp = client.get("/version")
        assert resp.status_code == 200
        body = resp.json()
        assert body["mask"]["model_version"] == "bf-mask-1"
        assert body["mask"]["labels"] == ["ok", "hakenkreuz"]
        assert body["mask"]["threshold"] == 0.5
        assert body["mask"]["feature_spec"] == "fs-1"
        assert body["mask"]["commit"] == "deadbeef"
        assert body["mask"]["min_votes"] == 1
        assert body["fullscan"]["model_version"] == "bf-scan-1"


def test_version_reports_min_votes_from_metadata(monkeypatch, tmp_path):
    build_onnx_model(tmp_path / "mask.onnx", 1, "mask", "bf-mask-1", threshold=0.5, min_votes=3)
    client = make_client(monkeypatch, tmp_path)
    with client:
        resp = client.get("/version")
        assert resp.status_code == 200
        assert resp.json()["mask"]["min_votes"] == 3


def test_infer_mask_flagged_true_for_heavily_modified_area(monkeypatch, both_models_dir):
    client = make_client(monkeypatch, both_models_dir)
    with client:
        modified = b64_uint8(np.ones(64 * 64, dtype=np.uint8))
        resp = client.post("/infer", json={"kind": "mask", "width": 64, "height": 64, "modified": modified})
        assert resp.status_code == 200
        body = resp.json()
        assert body["kind"] == "mask"
        assert body["model_version"] == "bf-mask-1"
        assert body["windows"] == 1
        assert body["flagged"] is True
        assert body["max_score"] > body["threshold"]
        assert len(body["detections"]) == 1
        assert "elapsed_ms" in body


def test_infer_mask_flagged_false_for_untouched_area(monkeypatch, both_models_dir):
    client = make_client(monkeypatch, both_models_dir)
    with client:
        modified = b64_uint8(np.zeros(64 * 64, dtype=np.uint8))
        resp = client.post("/infer", json={"kind": "mask", "width": 64, "height": 64, "modified": modified})
        body = resp.json()
        assert body["flagged"] is False
        assert body["detections"] == []


def test_infer_fullscan_uses_luminance_channel(monkeypatch, both_models_dir):
    client = make_client(monkeypatch, both_models_dir)
    with client:
        n = 64 * 64
        payload = {
            "kind": "fullscan",
            "width": 64,
            "height": 64,
            "blocks": b64_int16(np.ones(n, dtype=np.int16)),
            "heights": b64_int16(np.full(n, 64, dtype=np.int16)),
            "luminance": b64_uint8(np.full(n, 255, dtype=np.uint8)),
        }
        resp = client.post("/infer", json=payload)
        assert resp.status_code == 200
        body = resp.json()
        assert body["kind"] == "fullscan"
        assert body["flagged"] is True


def test_infer_wrong_length_returns_422(monkeypatch, both_models_dir):
    client = make_client(monkeypatch, both_models_dir)
    with client:
        resp = client.post("/infer", json={"kind": "mask", "width": 64, "height": 64, "modified": b64_uint8([1, 0])})
        assert resp.status_code == 422
        assert "modified" in resp.json()["detail"]


def test_infer_missing_fullscan_fields_returns_422(monkeypatch, both_models_dir):
    client = make_client(monkeypatch, both_models_dir)
    with client:
        resp = client.post("/infer", json={"kind": "fullscan", "width": 64, "height": 64})
        assert resp.status_code == 422


def test_infer_unloaded_kind_returns_503(monkeypatch, tmp_path):
    build_onnx_model(tmp_path / "mask.onnx", 1, "mask", "bf-mask-1", threshold=0.5)
    client = make_client(monkeypatch, tmp_path)
    with client:
        n = 64 * 64
        resp = client.post("/infer", json={
            "kind": "fullscan", "width": 64, "height": 64,
            "blocks": b64_int16(np.zeros(n, dtype=np.int16)),
            "heights": b64_int16(np.zeros(n, dtype=np.int16)),
            "luminance": b64_uint8(np.zeros(n, dtype=np.uint8)),
        })
        assert resp.status_code == 503


def test_infer_unknown_kind_returns_503(monkeypatch, both_models_dir):
    client = make_client(monkeypatch, both_models_dir)
    with client:
        resp = client.post("/infer", json={"kind": "banana", "width": 64, "height": 64})
        assert resp.status_code == 503


def test_infer_area_smaller_than_grid_still_produces_one_window(monkeypatch, both_models_dir):
    client = make_client(monkeypatch, both_models_dir)
    with client:
        modified = b64_uint8(np.ones(8 * 8, dtype=np.uint8))
        resp = client.post("/infer", json={"kind": "mask", "width": 8, "height": 8, "modified": modified})
        assert resp.status_code == 200
        body = resp.json()
        assert body["windows"] == 1


def test_infer_width_out_of_bounds_returns_422(monkeypatch, both_models_dir):
    client = make_client(monkeypatch, both_models_dir)
    with client:
        resp = client.post("/infer", json={"kind": "mask", "width": 0, "height": 64})
        assert resp.status_code == 422
        resp2 = client.post("/infer", json={"kind": "mask", "width": 64, "height": 513})
        assert resp2.status_code == 422


def test_infer_detections_shape_sorted_by_score_and_capped(monkeypatch, both_models_dir):
    client = make_client(monkeypatch, both_models_dir)
    with client:
        modified = np.ones((150, 150), dtype=np.uint8)
        payload = {"kind": "mask", "width": 150, "height": 150, "modified": b64_uint8(modified.flatten())}
        resp = client.post("/infer", json=payload)
        body = resp.json()
        assert body["windows"] > 1
        assert body["min_votes"] == 1
        assert len(body["detections"]) >= 1
        assert len(body["detections"]) <= 20
        for det in body["detections"]:
            assert det["score"] >= body["threshold"]
            assert set(det.keys()) == {"top", "left", "bottom", "right", "score", "votes"}
        scores = [d["score"] for d in body["detections"]]
        assert scores == sorted(scores, reverse=True)


def test_infer_isolated_single_window_not_flagged_when_min_votes_two(monkeypatch, tmp_path):
    build_onnx_model(tmp_path / "mask.onnx", 1, "mask", "bf-mask-1", threshold=0.5, min_votes=2)
    client = make_client(monkeypatch, tmp_path)
    with client:
        modified = b64_uint8(np.ones(64 * 64, dtype=np.uint8))
        resp = client.post("/infer", json={"kind": "mask", "width": 64, "height": 64, "modified": modified})
        body = resp.json()
        assert body["windows"] == 1
        assert body["max_score"] >= body["threshold"]
        assert body["min_votes"] == 2
        assert body["flagged"] is False
        assert body["detections"] == []


def test_infer_overlapping_windows_flagged_when_min_votes_two(monkeypatch, tmp_path):
    build_onnx_model(tmp_path / "mask.onnx", 1, "mask", "bf-mask-1", threshold=0.5, min_votes=2)
    client = make_client(monkeypatch, tmp_path)
    with client:
        modified = np.ones((64 + GRID, 64), dtype=np.uint8)
        payload = {"kind": "mask", "width": 64, "height": 64 + GRID, "modified": b64_uint8(modified.flatten())}
        resp = client.post("/infer", json=payload)
        body = resp.json()
        assert body["windows"] > 1
        assert body["flagged"] is True
        assert len(body["detections"]) == 1
        assert body["detections"][0]["votes"] >= 2
