import numpy as np
import pytest

from vistructum_ml.contract import FULLSCAN, GRID, HEIGHT_CLIP, MASK, STRIDE
from vistructum_ml.features import block_boundaries, extract, relative_height, window_origins, windows
from vistructum_ml.scene import UNKNOWN, Scene


def flat_scene(size=64, block=1, height=64, lum=120):
    return Scene(
        np.full((size, size), block),
        np.full((size, size), height),
        np.full((size, size), lum),
        np.zeros((size, size), dtype=bool),
    )


def test_same_block_raised_is_visible_in_height_channel():
    scene = flat_scene()
    scene.heights[20:30, 20:24] += 1
    rel = relative_height(scene).astype(int) - HEIGHT_CLIP
    assert (rel[20:30, 20:24] == 1).all()
    assert (rel[:10, :10] == 0).all()
    assert block_boundaries(scene).sum() == 0


def test_linear_slope_cancels():
    scene = flat_scene()
    scene.heights[:] = 64 + (np.arange(64)[None, :] // 3)
    rel = relative_height(scene).astype(int) - HEIGHT_CLIP
    assert np.abs(rel[16:48, 16:48]).max() <= 1


def test_carved_symbol_is_negative_and_clipped():
    scene = flat_scene()
    scene.heights[10:20, 10:14] -= 9
    rel = relative_height(scene).astype(int) - HEIGHT_CLIP
    assert (rel[10:20, 10:14] == -HEIGHT_CLIP).all()


def test_boundaries_mark_both_sides():
    scene = flat_scene()
    scene.blocks[10:12, 10:12] = 2
    edge = block_boundaries(scene)
    assert edge[10, 10] == 1 and edge[9, 10] == 1 and edge[10, 9] == 1
    assert edge[0, 0] == 0


def test_unknown_blocks_are_neutral():
    scene = flat_scene()
    scene.blocks[:, :8] = UNKNOWN
    scene.heights[:, :8] = -999
    feats = extract(FULLSCAN.name, scene)
    assert (feats[0, :, :8] == HEIGHT_CLIP).all()
    assert (feats[1, :, :8] == 0).all()
    assert (feats[2, :, :8] == 128).all()
    assert (feats[0, :, 8:] == HEIGHT_CLIP).all()


def test_feature_shapes_and_dtype():
    scene = flat_scene(size=100)
    scene.modified[5, 5] = True
    mask = extract(MASK.name, scene)
    full = extract(FULLSCAN.name, scene)
    assert mask.shape == (MASK.channels, 100, 100) and mask.dtype == np.uint8
    assert full.shape == (FULLSCAN.channels, 100, 100) and full.dtype == np.uint8
    assert mask[0, 5, 5] == 1


@pytest.mark.parametrize("length,expected", [(64, [0]), (40, [0]), (100, [0, 24, 36]), (160, [0, 24, 48, 72, 96])])
def test_window_origins_cover_edges(length, expected):
    assert window_origins(length, GRID, STRIDE) == expected


def test_windows_pad_small_areas():
    scene = flat_scene(size=20)
    positions, stack = windows(FULLSCAN.name, extract(FULLSCAN.name, scene))
    assert positions == [(0, 0)]
    assert stack.shape == (1, 3, GRID, GRID)
    assert (stack[0, 0, 30:, 30:] == HEIGHT_CLIP).all()
    assert (stack[0, 2, 30:, 30:] == 128).all()
