import base64
import json

import numpy as np
import pytest
from findings import VERDICT_LABELS, convert, decode_scene, sample

from vistructum_ml import features
from vistructum_ml.contract import GRID, KINDS, LABELS, POSITIVE
from vistructum_ml.scene import UNKNOWN, Scene


def encode(array, dtype):
    return base64.b64encode(np.ascontiguousarray(array, dtype=dtype).tobytes()).decode()


def line(finding, kind, scene, window, verdict="CONFIRMED", source="mask"):
    rows, cols = scene.shape
    return {
        "finding": finding,
        "source": source,
        "verdict": verdict,
        "model_version": "bf-test",
        "window": dict(zip(("top", "left", "bottom", "right"), window)),
        "kind": kind,
        "width": cols,
        "height": rows,
        "blocks": encode(scene.blocks, "<i2"),
        "heights": encode(scene.heights, "<i2"),
        "luminance": encode(scene.luminance, np.uint8),
        "modified": encode(scene.modified, np.uint8),
    }


def surface_scene(rows, cols, seed=0):
    rng = np.random.default_rng(seed)
    blocks = rng.integers(0, 40, size=(rows, cols))
    blocks[:, :5] = UNKNOWN
    heights = 60 + rng.integers(-3, 4, size=(rows, cols))
    luminance = rng.integers(0, 256, size=(rows, cols))
    return Scene(blocks, heights, luminance, np.zeros((rows, cols), dtype=bool))


def mask_scene(rows, cols, seed=0):
    rng = np.random.default_rng(seed)
    unknown = np.full((rows, cols), UNKNOWN)
    return Scene(unknown, np.zeros((rows, cols)), np.zeros((rows, cols)), rng.random((rows, cols)) < 0.3)


def write(path, lines):
    path.write_text("".join(json.dumps(entry) + "\n" for entry in lines), encoding="utf-8")
    return path


def test_labels_follow_the_contract():
    assert LABELS[VERDICT_LABELS["CONFIRMED"]] == LABELS[POSITIVE] == "hakenkreuz"
    assert LABELS[VERDICT_LABELS["FALSE_ALARM"]] == "ok"


def test_decode_scene_is_lossless():
    scene = surface_scene(30, 45)
    decoded = decode_scene(line(1, "fullscan", scene, (0, 0, 64, 64)))
    for name in ("blocks", "heights", "luminance", "modified"):
        assert np.array_equal(getattr(decoded, name), getattr(scene, name))


def test_sample_equals_the_model_window_of_a_single_window_detection():
    scene = surface_scene(160, 130, seed=3)
    feats = features.extract("fullscan", scene)
    positions, stack = features.windows("fullscan", feats)
    index = 5
    top, left = positions[index]
    x = sample(line(1, "fullscan", scene, (top, left, top + GRID, left + GRID)))
    assert x.shape == (KINDS["fullscan"].channels, GRID, GRID)
    assert x.dtype == np.uint8
    assert np.array_equal(x, stack[index])


def test_small_scene_is_padded_like_inference():
    scene = mask_scene(20, 50, seed=1)
    x = sample(line(1, "mask", scene, (0, 0, 64, 64)))
    assert x.shape == (1, GRID, GRID)
    assert np.array_equal(x, features.windows("mask", features.extract("mask", scene))[1][0])
    assert not x[:, 20:, :].any()


def test_merged_detection_is_centred_and_clamped():
    scene = mask_scene(100, 200, seed=2)
    padded = features.pad_to_grid("mask", features.extract("mask", scene))
    centred = sample(line(1, "mask", scene, (0, 40, 88, 152)))
    assert np.array_equal(centred, padded[:, 12:76, 64:128])
    clamped = sample(line(1, "mask", scene, (60, 150, 130, 260)))
    assert np.array_equal(clamped, padded[:, 36:100, 136:200])


def test_convert_writes_a_loadable_split(tmp_path):
    lines = [
        line(3, "mask", mask_scene(70, 70, seed=0), (0, 0, 64, 64), "CONFIRMED"),
        line(7, "mask", mask_scene(40, 90, seed=1), (0, 24, 64, 88), "FALSE_ALARM", source="fullscan"),
    ]
    target, kind, positives, negatives = convert([write(tmp_path / "mask.jsonl", lines)], tmp_path / "data", "findings")
    assert (kind, positives, negatives) == ("mask", 1, 1)
    raw = np.load(target)
    assert raw["x"].shape == (2, 1, GRID, GRID)
    assert raw["x"].dtype == np.uint8
    assert raw["y"].tolist() == [POSITIVE, VERDICT_LABELS["FALSE_ALARM"]]
    assert raw["finding"].tolist() == [3, 7]
    assert raw["subtype"].tolist() == ["finding-mask-confirmed", "finding-fullscan-false-alarm"]


def test_convert_holds_out_a_stratified_val_split(tmp_path):
    lines = [line(i, "mask", mask_scene(64, 64, seed=i), (0, 0, 64, 64), "CONFIRMED" if i % 2 else "FALSE_ALARM")
             for i in range(1, 21)]
    source = write(tmp_path / "mask.jsonl", lines)
    target, kind, positives, negatives = convert([source], tmp_path / "data", "mask", val_fraction=0.2, seed=3)
    assert (target.name, kind, positives, negatives) == ("mask-train.npz", "mask", 10, 10)
    val = np.load(tmp_path / "data" / "mask-val.npz")
    train = np.load(target)
    assert sorted((val["y"] == POSITIVE).tolist()) == [False, False, True, True]
    assert len(train["y"]) == 16
    assert not set(val["finding"].tolist()) & set(train["finding"].tolist())
    convert([source], tmp_path / "again", "mask", val_fraction=0.2, seed=3)
    assert np.load(tmp_path / "again" / "mask-val.npz")["finding"].tolist() == val["finding"].tolist()
    with pytest.raises(ValueError, match="val_fraction"):
        convert([source], tmp_path / "bad", "mask", val_fraction=1.0)


def test_convert_rejects_mixed_kinds_and_empty_input(tmp_path):
    mixed = write(tmp_path / "mixed.jsonl", [
        line(1, "mask", mask_scene(64, 64), (0, 0, 64, 64)),
        line(2, "fullscan", surface_scene(64, 64), (0, 0, 64, 64)),
    ])
    with pytest.raises(ValueError, match="several kinds"):
        convert([mixed], tmp_path / "out", "findings")
    with pytest.raises(ValueError, match="no findings"):
        convert([write(tmp_path / "empty.jsonl", [])], tmp_path / "out", "findings")


def test_decode_scene_rejects_wrong_sizes():
    entry = line(9, "mask", mask_scene(10, 10), (0, 0, 64, 64))
    entry["width"] = 11
    with pytest.raises(ValueError, match="finding 9"):
        decode_scene(entry)
