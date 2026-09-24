import numpy as np

from .contract import FULLSCAN, GRID, HEIGHT_CLIP, HEIGHT_CONTEXT, HEIGHT_SAMPLE_STEP, MASK, STRIDE
from .scene import UNKNOWN

HEIGHT_ZERO = HEIGHT_CLIP
LUMINANCE_PAD = 128


def mask_features(scene):
    return scene.modified.astype(np.uint8)[None]


def relative_height(scene):
    known = scene.blocks != UNKNOWN
    heights = np.where(known, scene.heights, 0).astype(np.float32)
    reach = HEIGHT_CONTEXT // 2
    padded = np.pad(heights, reach, mode="edge")
    padded_known = np.pad(known, reach, mode="edge")
    offsets = range(-reach, reach + 1, HEIGHT_SAMPLE_STEP)
    rows, cols = heights.shape
    samples = []
    for dr in offsets:
        for dc in offsets:
            window = padded[reach + dr:reach + dr + rows, reach + dc:reach + dc + cols]
            valid = padded_known[reach + dr:reach + dr + rows, reach + dc:reach + dc + cols]
            samples.append(np.where(valid, window, np.nan))
    ground = np.nanmedian(np.stack(samples), axis=0) if samples else heights
    ground = np.where(np.isnan(ground), heights, ground)
    relative = np.clip(np.rint(heights - ground), -HEIGHT_CLIP, HEIGHT_CLIP)
    return np.where(known, relative + HEIGHT_ZERO, HEIGHT_ZERO).astype(np.uint8)


def block_boundaries(scene):
    blocks = scene.blocks
    edge = np.zeros(blocks.shape, dtype=bool)
    vertical = blocks[1:, :] != blocks[:-1, :]
    horizontal = blocks[:, 1:] != blocks[:, :-1]
    edge[1:, :] |= vertical
    edge[:-1, :] |= vertical
    edge[:, 1:] |= horizontal
    edge[:, :-1] |= horizontal
    edge &= blocks != UNKNOWN
    return edge.astype(np.uint8)


def height_steps(scene):
    """1 on both sides of every step of at least one block between 4-neighbours: the outline of anything raised or
    sunk, even when it is built from the ground's own block and so has no block-id boundary and no luminance change"""
    known = scene.blocks != UNKNOWN
    heights = scene.heights
    edge = np.zeros(heights.shape, dtype=bool)
    vertical = (heights[1:, :] != heights[:-1, :]) & known[1:, :] & known[:-1, :]
    horizontal = (heights[:, 1:] != heights[:, :-1]) & known[:, 1:] & known[:, :-1]
    edge[1:, :] |= vertical
    edge[:-1, :] |= vertical
    edge[:, 1:] |= horizontal
    edge[:, :-1] |= horizontal
    return edge.astype(np.uint8)


def fullscan_features(scene):
    luminance = np.where(scene.blocks != UNKNOWN, scene.luminance, LUMINANCE_PAD).astype(np.uint8)
    return np.stack([relative_height(scene), block_boundaries(scene), luminance, height_steps(scene)])


EXTRACTORS = {MASK.name: mask_features, FULLSCAN.name: fullscan_features}
PAD_VALUES = {MASK.name: (0,), FULLSCAN.name: (HEIGHT_ZERO, 0, LUMINANCE_PAD, 0)}


def extract(kind, scene):
    return EXTRACTORS[kind](scene)


def pad_to_grid(kind, features):
    channels, rows, cols = features.shape
    out_rows = max(rows, GRID)
    out_cols = max(cols, GRID)
    if (out_rows, out_cols) == (rows, cols):
        return features
    out = np.empty((channels, out_rows, out_cols), dtype=np.uint8)
    for channel, value in enumerate(PAD_VALUES[kind]):
        out[channel] = value
    out[:, :rows, :cols] = features
    return out


def window_origins(length, size=GRID, stride=STRIDE):
    if length <= size:
        return [0]
    origins = list(range(0, length - size + 1, stride))
    if origins[-1] != length - size:
        origins.append(length - size)
    return origins


def windows(kind, features, size=GRID, stride=STRIDE):
    padded = pad_to_grid(kind, features)
    _, rows, cols = padded.shape
    positions = [(top, left) for top in window_origins(rows, size, stride)
                 for left in window_origins(cols, size, stride)]
    stack = np.stack([padded[:, top:top + size, left:left + size] for top, left in positions])
    return positions, stack
