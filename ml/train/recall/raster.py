import numpy as np
from generator.symbol import build_symbol_mask, rotate45

HANDEDNESS = ("right", "left")
ROTATIONS = (0, 45)
DIAGONAL_GROWTH = np.sqrt(2.0)


def trim(mask):
    rows = np.flatnonzero(mask.any(axis=1))
    cols = np.flatnonzero(mask.any(axis=0))
    if len(rows) == 0:
        return mask[:0, :0]
    return mask[rows[0]:rows[-1] + 1, cols[0]:cols[-1] + 1]


def symbol_raster(size, thickness, handedness, rotation):
    if handedness not in HANDEDNESS:
        raise ValueError(f"unknown handedness {handedness}")
    if rotation not in ROTATIONS:
        raise ValueError(f"unknown rotation {rotation}")
    mask = build_symbol_mask(size, thickness, handedness == "left")
    if rotation == 45:
        pad = int(np.ceil(mask.shape[0] * (DIAGONAL_GROWTH - 1.0) / 2.0)) + 1
        mask = rotate45(np.pad(mask, pad))
    return trim(mask)


def max_size_for(rotation, max_extent):
    limit = int(max_extent / DIAGONAL_GROWTH) if rotation == 45 else int(max_extent)
    return limit if limit % 2 else limit - 1


def sample_symbol(rng, min_size, max_extent):
    rotation = ROTATIONS[int(rng.integers(0, len(ROTATIONS)))]
    handedness = HANDEDNESS[int(rng.integers(0, len(HANDEDNESS)))]
    low = min_size if min_size % 2 else min_size + 1
    high = max(low, max_size_for(rotation, max_extent))
    size = int(low + 2 * rng.integers(0, (high - low) // 2 + 1))
    max_thickness = max(1, size // 4)
    thickness = int(1 + 2 * rng.integers(0, (max_thickness - 1) // 2 + 1))
    return {"size": size, "thickness": thickness, "handedness": handedness, "rotation": rotation}


def rectangles(mask):
    remaining = mask.copy()
    height, width = mask.shape
    out = []
    for row, col in zip(*np.nonzero(mask)):
        if not remaining[row, col]:
            continue
        right = col
        while right + 1 < width and remaining[row, right + 1]:
            right += 1
        bottom = row
        while bottom + 1 < height and remaining[bottom + 1, col:right + 1].all():
            bottom += 1
        remaining[row:bottom + 1, col:right + 1] = False
        out.append((int(row), int(col), int(bottom), int(right)))
    return out
