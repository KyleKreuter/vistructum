import numpy as np

from vistructum_ml.scene import Scene

from .scenes import (
    CANVAS,
    _apply_dropout,
    _luminance_from_blocks,
    _stamp_decoys,
    _stamp_hard_negative,
    _stamp_plain_build,
    _stamp_symbol,
)
from .terrain import generate_terrain

HARD_NEGATIVE_SHARE = 0.25
RECENT_SHARE = 0.6
MAX_PLACEMENT_TRIES = 20


def _view(size, rng):
    top = int(rng.integers(0, size - CANVAS + 1))
    left = int(rng.integers(0, size - CANVAS + 1))
    return top, left


def _bbox(footprint, top, left):
    rows, cols = np.nonzero(footprint)
    if len(rows) == 0:
        return None
    return (int(rows.min()) + top, int(cols.min()) + left, int(rows.max()) + top + 1, int(cols.max()) + left + 1)


def _intersects(a, b, margin=0):
    return not (a[2] + margin <= b[0] or b[2] + margin <= a[0] or a[3] + margin <= b[1] or b[3] + margin <= a[1])


def _stamp_background(rng, blocks, heights, size, holdout, kind):
    slots = max(1, (size // 64) ** 2)
    footprints = []
    for _ in range(slots):
        top, left = _view(size, rng)
        view_blocks = blocks[top:top + CANVAS, left:left + CANVAS]
        view_heights = heights[top:top + CANVAS, left:left + CANVAS]
        if kind == "fullscan":
            _stamp_decoys(rng, view_blocks, view_heights, int(rng.integers(0, 3)))
        if rng.random() < HARD_NEGATIVE_SHARE:
            footprint, _subtype, _vis = _stamp_hard_negative(rng, view_blocks, view_heights, holdout)
        else:
            footprint, subtype = _stamp_plain_build(rng, view_blocks, view_heights, holdout)
            if subtype is None:
                continue
        full = np.zeros((size, size), dtype=bool)
        full[top:top + CANVAS, left:left + CANVAS] = footprint
        footprints.append(full)
    return footprints


def _stamp_symbols(rng, blocks, heights, size, count, holdout):
    symbols = []
    for _ in range(count):
        for _attempt in range(MAX_PLACEMENT_TRIES):
            top, left = _view(size, rng)
            view_blocks = blocks[top:top + CANVAS, left:left + CANVAS].copy()
            view_heights = heights[top:top + CANVAS, left:left + CANVAS].copy()
            footprint, subtype, _vis = _stamp_symbol(rng, view_blocks, view_heights, holdout)
            box = _bbox(footprint, top, left)
            if box is None or any(_intersects(box, other["box"], margin=8) for other in symbols):
                continue
            blocks[top:top + CANVAS, left:left + CANVAS] = view_blocks
            heights[top:top + CANVAS, left:left + CANVAS] = view_heights
            full = np.zeros((size, size), dtype=bool)
            full[top:top + CANVAS, left:left + CANVAS] = footprint
            symbols.append({"box": box, "subtype": subtype, "footprint": full})
            break
    return symbols


def make_area(rng, kind, size, n_symbols, holdout=False):
    amplitude = float(rng.uniform(0, 16.0 if holdout else 12.0))
    blocks, heights, biome = generate_terrain(rng, size, amplitude=amplitude)
    background = _stamp_background(rng, blocks, heights, size, holdout, kind)
    symbols = _stamp_symbols(rng, blocks, heights, size, n_symbols, holdout)
    modified = np.zeros((size, size), dtype=bool)
    if kind == "mask":
        for footprint in background:
            if rng.random() < RECENT_SHARE:
                modified |= _apply_dropout(rng, footprint)
        for symbol in symbols:
            modified |= _apply_dropout(rng, symbol["footprint"])
        scatter = rng.random((size, size)) < float(rng.uniform(0.0, 0.002))
        modified |= scatter
        luminance = np.zeros((size, size), dtype=np.uint8)
    else:
        luminance = _luminance_from_blocks(blocks, rng, 15 if holdout else 0)
    scene = Scene(blocks, heights, luminance, modified)
    truth = [{"box": s["box"], "subtype": s["subtype"], "cells": int(s["footprint"].sum())} for s in symbols]
    return scene, truth, biome
