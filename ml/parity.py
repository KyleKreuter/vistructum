import argparse
import base64
import hashlib
import json
from pathlib import Path

import numpy as np
import onnxruntime as ort
from generator.areas import make_area
from generator.rng import rng_for
from generator.scenes import CANVAS

from vistructum_ml import detect, features, scoring
from vistructum_ml.metadata import read_session_metadata, validate
from vistructum_ml.scene import UNKNOWN, Scene

REPO = Path(__file__).resolve().parent.parent
MODELS = REPO / "models"
FIXTURES = REPO / "vistructum-inference" / "src" / "test" / "resources" / "parity"
TEST_MODEL = FIXTURES.parent / "test-mask.onnx"
MAX_DETECTIONS = 20
CASES = (
    ("mask-small", "mask", 40, 1, False),
    ("mask-area", "mask", 110, 2, False),
    ("fullscan-small", "fullscan", 50, 1, False),
    ("fullscan-area", "fullscan", 140, 5, False),
    ("fullscan-unknown", "fullscan", 100, 1, True),
)
SEED = 20260925


def model_sha256(kind):
    return hashlib.sha256((MODELS / f"{kind}.onnx").read_bytes()).hexdigest()


def session_for(kind):
    options = ort.SessionOptions()
    options.intra_op_num_threads = 1
    options.inter_op_num_threads = 1
    session = ort.InferenceSession(str(MODELS / f"{kind}.onnx"), sess_options=options,
                                   providers=["CPUExecutionProvider"])
    return session, validate(read_session_metadata(session))


def scene_for(index, kind, size, n_symbols, unknown_band):
    area = max(size, CANVAS)
    scene, _truth, _biome = make_area(rng_for(SEED, "scan", index), kind, area, n_symbols)
    offset = (area - size) // 2
    crop = (slice(offset, offset + size), slice(offset, offset + size))
    scene = Scene(scene.blocks[crop], scene.heights[crop], scene.luminance[crop], scene.modified[crop])
    blocks = scene.blocks.copy()
    if unknown_band:
        blocks[:, size // 3:size // 3 + 12] = UNKNOWN
        blocks[size - 7:, :] = UNKNOWN
    return Scene(blocks, scene.heights, scene.luminance, scene.modified)


def encode(array, dtype):
    return base64.b64encode(np.ascontiguousarray(array, dtype=dtype).tobytes()).decode()


def fixture(index, name, kind, size, n_symbols, unknown_band):
    scene = scene_for(index, kind, size, n_symbols, unknown_band)
    session, meta = session_for(kind)
    feats = features.extract(kind, scene)
    positions, batch = features.windows(kind, feats)
    scores, refined = scoring.score(session, batch, meta["tta"], meta["prefilter"])
    flagged = detect.flagged(positions, scores, meta["threshold"], meta["min_votes"])
    return {
        "name": name,
        "kind": kind,
        "width": int(scene.shape[1]),
        "height": int(scene.shape[0]),
        "blocks": encode(scene.blocks, "<i2"),
        "heights": encode(scene.heights, "<i2"),
        "luminance": encode(scene.luminance, np.uint8),
        "modified": encode(scene.modified, np.uint8),
        "features": encode(feats, np.uint8),
        "positions": [[int(top), int(left)] for top, left in positions],
        "scores": [float(score) for score in scores],
        "refined": int(refined),
        "detections": [{key: cluster[key] for key in ("top", "left", "bottom", "right", "score", "votes")}
                       for cluster in flagged[:MAX_DETECTIONS]],
        "model_sha256": model_sha256(kind),
    }


def fixtures():
    return {name: fixture(index, name, kind, size, n_symbols, unknown_band)
            for index, (name, kind, size, n_symbols, unknown_band) in enumerate(CASES)}


def test_model(version="test-mask-1"):
    import onnx
    from onnx import TensorProto, helper

    graph = helper.make_graph(
        [
            helper.make_node("Cast", ["features"], ["as_float"], to=TensorProto.FLOAT),
            helper.make_node("ReduceMean", ["as_float"], ["share"], axes=[1, 2, 3], keepdims=0),
            helper.make_node("Unsqueeze", ["share", "axis"], ["positive"]),
            helper.make_node("Sub", ["one", "positive"], ["negative"]),
            helper.make_node("Concat", ["negative", "positive"], ["scores"], axis=1),
        ],
        "test-mask",
        [helper.make_tensor_value_info("features", TensorProto.UINT8, ["batch", 1, 64, 64])],
        [helper.make_tensor_value_info("scores", TensorProto.FLOAT, ["batch", 2])],
        [helper.make_tensor("axis", TensorProto.INT64, [1], [1]), helper.make_tensor("one", TensorProto.FLOAT, [], [1.0])],
    )
    model = helper.make_model(graph, opset_imports=[helper.make_opsetid("", 13)])
    model.ir_version = 8
    helper.set_model_props(model, {
        "vistructum.model_version": version,
        "vistructum.kind": "mask",
        "vistructum.labels": json.dumps(["ok", "hakenkreuz"]),
        "vistructum.feature_spec": "fs-1",
        "vistructum.threshold": "0.5",
        "vistructum.min_votes": "1",
        "vistructum.tta": "0",
    })
    onnx.checker.check_model(model)
    return model.SerializeToString()


def main():
    parser = argparse.ArgumentParser(description="writes the fixtures the Java inference parity test checks against")
    parser.parse_args()
    TEST_MODEL.write_bytes(test_model())
    FIXTURES.mkdir(parents=True, exist_ok=True)
    for name, content in fixtures().items():
        (FIXTURES / f"{name}.json").write_text(json.dumps(content, indent=1) + "\n")
        print(f"{name}: {len(content['scores'])} windows, {len(content['detections'])} detections")


if __name__ == "__main__":
    main()
