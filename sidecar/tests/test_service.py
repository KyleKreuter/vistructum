import base64

import numpy as np
import pytest
from pydantic import ValidationError

from service import (
    Detection,
    InferRequest,
    LoadedModel,
    build_scene,
    decode_int16,
    decode_uint8,
    run_inference,
)
from vistructum_ml.contract import STRIDE


def b64_int16(values):
    return base64.b64encode(np.asarray(values, dtype="<i2").tobytes()).decode("ascii")


def b64_uint8(values):
    return base64.b64encode(np.asarray(values, dtype=np.uint8).tobytes()).decode("ascii")


def test_decode_int16_roundtrip():
    arr = decode_int16(b64_int16([1, -1, 300]), 3, "blocks")
    assert arr.tolist() == [1, -1, 300]


def test_decode_int16_missing_defaults_zero():
    arr = decode_int16(None, 4, "blocks")
    assert arr.tolist() == [0, 0, 0, 0]


def test_decode_int16_wrong_length_raises():
    with pytest.raises(ValueError, match="blocks"):
        decode_int16(b64_int16([1, 2]), 3, "blocks")


def test_decode_uint8_wrong_length_raises():
    with pytest.raises(ValueError, match="luminance"):
        decode_uint8(b64_uint8([1, 2, 3]), 4, "luminance")


def test_build_scene_mask_only_needs_modified():
    payload = InferRequest(kind="mask", width=4, height=4, modified=b64_uint8([1, 0] * 8))
    scene = build_scene(payload)
    assert scene.shape == (4, 4)
    assert scene.modified.sum() == 8


def test_build_scene_mask_missing_modified_raises():
    payload = InferRequest(kind="mask", width=4, height=4)
    with pytest.raises(ValueError, match="modified"):
        build_scene(payload)


def test_build_scene_fullscan_requires_blocks_heights_luminance():
    payload = InferRequest(kind="fullscan", width=2, height=2)
    with pytest.raises(ValueError):
        build_scene(payload)


def test_build_scene_fullscan_happy_path():
    payload = InferRequest(
        kind="fullscan",
        width=2,
        height=2,
        blocks=b64_int16([1, 1, 1, 1]),
        heights=b64_int16([64, 64, 64, 64]),
        luminance=b64_uint8([10, 20, 30, 40]),
    )
    scene = build_scene(payload)
    assert scene.blocks.tolist() == [[1, 1], [1, 1]]
    assert scene.luminance.tolist() == [[10, 20], [30, 40]]
    assert scene.modified.sum() == 0


def test_build_scene_bad_modified_values_raise():
    payload = InferRequest(kind="mask", width=2, height=2, modified=b64_uint8([0, 1, 2, 0]))
    with pytest.raises(ValueError, match="0 or 1"):
        build_scene(payload)


def test_detection_and_infer_request_models_validate():
    det = Detection(top=0, left=0, bottom=64, right=64, score=0.5, votes=1)
    assert det.bottom == 64
    req = InferRequest(kind="mask", width=64, height=64)
    assert req.width == 64


def test_infer_request_rejects_out_of_range_dims():
    with pytest.raises(ValidationError):
        InferRequest(kind="mask", width=0, height=64)
    with pytest.raises(ValidationError):
        InferRequest(kind="mask", width=64, height=513)


class FakeSession:
    def __init__(self, positive_fn):
        self.positive_fn = positive_fn
        self.calls = []

    def run(self, output_names, feed):
        self.calls.append(feed[next(iter(feed))].shape[0])
        batch = feed["features"]
        n = batch.shape[0]
        positive = np.array([self.positive_fn(batch[i]) for i in range(n)], dtype=np.float32)
        negative = 1.0 - positive
        return [np.stack([negative, positive], axis=1)]


def test_run_inference_flags_high_score_window():
    session = FakeSession(lambda window: 1.0 if window.mean() > 0.4 else 0.0)
    model = LoadedModel(session, "mask", "v1", ("ok", "hakenkreuz"), 0.5, "fs-1", "commit1", "path", 1)
    modified = np.zeros((64, 64), dtype=bool)
    modified[:, :] = True
    from vistructum_ml.scene import Scene
    scene = Scene(np.zeros((64, 64)), np.zeros((64, 64)), np.zeros((64, 64), dtype=np.uint8), modified)
    result = run_inference(model, scene)
    assert result["flagged"] is True
    assert result["max_score"] == 1.0
    assert result["windows"] == 1
    assert result["min_votes"] == 1
    det = result["detections"][0]
    assert det["score"] == 1.0
    assert det["votes"] == 1
    assert set(det.keys()) == {"top", "left", "bottom", "right", "score", "votes"}
    assert "elapsed_ms" in result


def test_run_inference_batches_in_groups_of_64():
    from vistructum_ml.scene import Scene
    session = FakeSession(lambda window: 0.0)
    model = LoadedModel(session, "mask", "v1", ("ok", "hakenkreuz"), 0.5, "fs-1", "commit1", "path", 1)
    modified = np.zeros((400, 400), dtype=bool)
    scene = Scene(np.zeros((400, 400)), np.zeros((400, 400)), np.zeros((400, 400), dtype=np.uint8), modified)
    result = run_inference(model, scene)
    assert result["windows"] > 64
    assert max(session.calls) <= 64


def test_run_inference_isolated_hit_not_flagged_when_min_votes_two():
    session = FakeSession(lambda window: 1.0 if window.mean() == 1.0 else 0.0)
    model = LoadedModel(session, "mask", "v1", ("ok", "hakenkreuz"), 0.5, "fs-1", "commit1", "path", 2)
    modified = np.zeros((88, 88), dtype=bool)
    modified[:64, :64] = True
    from vistructum_ml.scene import Scene
    scene = Scene(np.zeros((88, 88)), np.zeros((88, 88)), np.zeros((88, 88), dtype=np.uint8), modified)
    result = run_inference(model, scene)
    assert result["max_score"] >= model.threshold
    assert result["flagged"] is False
    assert result["detections"] == []


def test_run_inference_overlapping_hits_flagged_when_min_votes_two():
    session = FakeSession(lambda window: 1.0 if window.mean() == 1.0 else 0.0)
    model = LoadedModel(session, "mask", "v1", ("ok", "hakenkreuz"), 0.5, "fs-1", "commit1", "path", 2)
    modified = np.zeros((64 + STRIDE, 64), dtype=bool)
    modified[:, :] = True
    from vistructum_ml.scene import Scene
    scene = Scene(np.zeros((64 + STRIDE, 64)), np.zeros((64 + STRIDE, 64)),
                   np.zeros((64 + STRIDE, 64), dtype=np.uint8), modified)
    result = run_inference(model, scene)
    assert result["flagged"] is True
    assert len(result["detections"]) == 1
    assert result["detections"][0]["votes"] >= 2


def test_java_golden_request_decodes_to_the_same_scene():
    import json
    from pathlib import Path

    from service import InferRequest, build_scene

    golden = Path(__file__).resolve().parents[2] / "vistructum-core/src/test/resources/infer-request-golden.json"
    scene = build_scene(InferRequest(**json.loads(golden.read_text())))
    index = np.arange(15).reshape(3, 5)
    assert (scene.blocks == index - 3).all() and scene.blocks[0, 2] == -1
    assert (scene.heights == (index - 7) * 100).all()
    assert (scene.luminance == (index * 17) % 256).all()
    assert sorted(np.flatnonzero(scene.modified)) == [2, 5, 11]
