import numpy as np
import pytest
from generator.build import build_split
from generator.rng import rng_for, sample_seed, split_offset
from generator.scenes import CROP, make_fullscan_sample, make_mask_sample
from generator.shapes import HARD_NEGATIVE_FAMILIES
from generator.shortcut_check import compute_shortcut_auc
from generator.symbol import (
    build_symbol_mask,
    is_c4_chiral,
    is_c4_symmetric,
    is_mirror_symmetric,
    sanitize_negative_mask,
    visible_fraction,
)

from vistructum_ml.contract import FULLSCAN


def test_determinism_fullscan():
    rng1 = rng_for(7, "train", 42)
    rng2 = rng_for(7, "train", 42)
    x1, y1, st1, biome1, _vis1 = make_fullscan_sample(rng1, 1)
    x2, y2, st2, biome2, _vis2 = make_fullscan_sample(rng2, 1)
    assert np.array_equal(x1, x2)
    assert (y1, st1, biome1) == (y2, st2, biome2)


def test_determinism_mask():
    rng1 = rng_for(7, "val", 5)
    rng2 = rng_for(7, "val", 5)
    x1, y1, st1, biome1, _vis1 = make_mask_sample(rng1, 0)
    x2, y2, st2, biome2, _vis2 = make_mask_sample(rng2, 0)
    assert np.array_equal(x1, x2)
    assert (y1, st1, biome1) == (y2, st2, biome2)


def test_split_seed_ranges_disjoint():
    seed = 3
    seen = set()
    for split in ("train", "val", "test", "holdout"):
        for index in range(0, 50000, 5000):
            value = sample_seed(seed, split, index)
            assert value not in seen
            seen.add(value)
    offsets = [split_offset(s) for s in ("train", "val", "test", "holdout")]
    assert len(set(offsets)) == 4


def test_split_rng_streams_differ():
    a = rng_for(1, "train", 0).random(8)
    b = rng_for(1, "val", 0).random(8)
    c = rng_for(1, "test", 0).random(8)
    d = rng_for(1, "holdout", 0).random(8)
    assert not np.array_equal(a, b)
    assert not np.array_equal(a, c)
    assert not np.array_equal(a, d)
    assert not np.array_equal(b, c)


@pytest.mark.parametrize("size,thick", [(5, 1), (9, 1), (17, 3), (31, 5), (59, 9), (7, 2), (40, 6)])
def test_symbol_is_c4_symmetric_but_chiral(size, thick):
    for mirror in (False, True):
        mask = build_symbol_mask(size, thick, mirror)
        assert is_c4_symmetric(mask)
        assert not is_mirror_symmetric(mask)
        assert is_c4_chiral(mask)


def test_symbol_chiralities_are_mirror_images_of_each_other():
    mask_a = build_symbol_mask(21, 3, False)
    mask_b = build_symbol_mask(21, 3, True)
    assert not np.array_equal(mask_a, mask_b)
    assert np.array_equal(mask_a, np.fliplr(mask_b))


def test_no_hard_negative_family_is_c4_chiral():
    rng = np.random.default_rng(99)
    total = 0
    for name, fn in HARD_NEGATIVE_FAMILIES.items():
        for _ in range(40):
            mask = fn(rng)
            mask = sanitize_negative_mask(mask, rng)
            total += 1
            assert not is_c4_chiral(mask), f"{name} produced a C4-chiral (swastika-like) shape"
    assert total >= 500


def test_no_hard_negative_family_equals_the_symbol():
    rng = np.random.default_rng(11)
    for fn in HARD_NEGATIVE_FAMILIES.values():
        mask = fn(rng)
        size = mask.shape[0]
        if size < 5:
            continue
        for mirror in (False, True):
            sym = build_symbol_mask(size, max(1, size // 8), mirror)
            if sym.shape == mask.shape:
                assert not np.array_equal(sym, mask)


def test_visibility_rule_accepts_only_high_visibility():
    mask = np.ones((40, 40), dtype=bool)
    crop_top = crop_left = 16
    full = visible_fraction(mask, 16, 16, crop_top, crop_left, CROP)
    assert full == 1.0
    half_out = visible_fraction(mask, 16, 60, crop_top, crop_left, CROP)
    assert half_out < 0.9


def test_generated_positive_samples_meet_visibility_or_are_none():
    rng = np.random.default_rng(555)
    for _i in range(20):
        _x, _y, _subtype, _biome, vis = make_fullscan_sample(rng, 1)
        assert vis is None or vis >= 0.9


def test_fullscan_output_format(tmp_path):
    data, stats = build_split("fullscan", 123, "val", 24, workers=1)
    assert data["x"].shape == (24, FULLSCAN.channels, 64, 64)
    assert data["x"].dtype == np.uint8
    assert data["y"].shape == (24,)
    assert data["y"].dtype == np.int64
    assert set(np.unique(data["y"]).tolist()) <= {0, 1}
    assert data["subtype"].shape == (24,)
    assert data["subtype"].dtype.kind == "U"
    assert data["seed"].shape == (24,)
    assert data["seed"].dtype == np.int64
    assert len(set(data["seed"].tolist())) == 24
    assert stats["count"] == 24
    assert stats["positives"] + stats["negatives"] == 24


def test_mask_output_format():
    data, _stats = build_split("mask", 321, "val", 20, workers=1)
    assert data["x"].shape == (20, 1, 64, 64)
    assert data["x"].dtype == np.uint8
    assert set(np.unique(data["x"]).tolist()) <= {0, 1}


def test_holdout_only_hard_negative_family_absent_outside_holdout():
    rng = np.random.default_rng(4321)
    for split in ("train", "val", "test"):
        for _i in range(80):
            _x, _y, subtype, _biome, _vis = make_fullscan_sample(rng, 0, holdout=False)
            assert "windmill-3" not in subtype


def _build_npz_pair(kind, out_dir, n_train, n_test, seed):
    out_dir.mkdir(parents=True, exist_ok=True)
    train_data, _stats = build_split(kind, seed, "train", n_train, workers=4)
    test_data, _stats = build_split(kind, seed, "test", n_test, workers=4)
    np.savez_compressed(out_dir / "train.npz", x=train_data["x"], y=train_data["y"],
                         subtype=train_data["subtype"], seed=train_data["seed"])
    np.savez_compressed(out_dir / "test.npz", x=test_data["x"], y=test_data["y"],
                         subtype=test_data["subtype"], seed=test_data["seed"])


def test_shortcut_auc_fullscan(tmp_path):
    out = tmp_path / "shortcut-fullscan"
    _build_npz_pair("fullscan", out, n_train=1200, n_test=600, seed=321)
    auc, info = compute_shortcut_auc(out)
    assert auc <= 0.65, info


def test_shortcut_auc_mask(tmp_path):
    out = tmp_path / "shortcut-mask"
    _build_npz_pair("mask", out, n_train=1500, n_test=800, seed=321)
    auc, info = compute_shortcut_auc(out)
    assert auc <= 0.65, info


def test_end_to_end_tiny_build(tmp_path):
    import sys

    from generator.build import main as build_main

    out = tmp_path / "ds"
    argv = ["build.py", "--kind", "fullscan", "--out", str(out),
            "--train", "12", "--val", "12", "--test", "12", "--holdout", "12",
            "--workers", "1", "--seed", "9"]
    old_argv = sys.argv
    sys.argv = argv
    try:
        build_main()
    finally:
        sys.argv = old_argv

    import json
    manifest = json.loads((out / "manifest.json").read_text())
    assert manifest["kind"] == "fullscan"
    assert manifest["feature_spec"] == "fs-1"
    assert set(manifest["splits"]) == {"train", "val", "test", "holdout"}
    for split in ("train", "val", "test", "holdout"):
        npz = np.load(out / f"{split}.npz", allow_pickle=True)
        assert npz["x"].shape == (12, FULLSCAN.channels, 64, 64)
        assert npz["y"].shape == (12,)


def _spy_dropout(monkeypatch):
    from generator import scenes

    calls = []
    original = scenes._apply_dropout

    def spy(rng, footprint, *args, **kwargs):
        calls.append(footprint.copy())
        return original(rng, footprint, *args, **kwargs)

    monkeypatch.setattr(scenes, "_apply_dropout", spy)
    return calls


def test_mask_extra_builds_never_bury_the_symbol(monkeypatch):
    calls = _spy_dropout(monkeypatch)
    for i in range(150):
        calls.clear()
        make_mask_sample(rng_for(3, "train", i), 1)
        symbol, extras = calls[0], calls[1:]
        rows, cols = np.nonzero(symbol)
        for extra in extras:
            assert not extra[rows.min() - 1:rows.max() + 2, cols.min() - 1:cols.max() + 2].any()


def test_symbols_are_spread_over_the_crop_not_centered(monkeypatch):
    from generator.scenes import MARGIN

    calls = _spy_dropout(monkeypatch)
    centers = []
    for i in range(200):
        calls.clear()
        make_mask_sample(rng_for(4, "train", i), 1)
        rows, _cols = np.nonzero(calls[0])
        centers.append((rows.min() + rows.max()) / 2 - MARGIN)
    spread = np.percentile(centers, 90) - np.percentile(centers, 10)
    assert spread > CROP / 4
