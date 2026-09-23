import numpy as np

from .noise import multi_octave
from .palette import BIOMES, TERRAIN_BLOCKS, block_id

SEA_LEVEL = 64


def generate_terrain(rng, size, biome=None, amplitude=None):
    biome = biome or str(rng.choice(BIOMES))
    cfg = TERRAIN_BLOCKS[biome]
    amplitude = amplitude if amplitude is not None else float(rng.uniform(0, 12))
    octaves = int(rng.integers(2, 5))
    base_cell = float(rng.uniform(10, 32))
    n = multi_octave(rng, size, size, octaves=octaves, base_cell=base_cell)
    heights = np.rint((n - 0.5) * 2 * amplitude).astype(np.int32) + SEA_LEVEL

    if amplitude > 3 and rng.random() < 0.25:
        heights = _apply_cliff(rng, heights)

    surface = block_id(cfg["surface"])
    blocks = np.full((size, size), surface, dtype=np.int32)

    if biome == "beach":
        water = block_id(cfg["water"])
        blocks = np.where(heights <= SEA_LEVEL, water, blocks)
        heights = np.where(heights <= SEA_LEVEL, SEA_LEVEL, heights)
    elif biome == "swamp" and rng.random() < 0.6:
        water = block_id(cfg["water"])
        low = heights <= np.percentile(heights, 25)
        blocks = np.where(low, water, blocks)
        heights = np.where(low, SEA_LEVEL - 1, heights)
    elif biome == "mountains":
        snow = block_id(cfg["snowcap"])
        cap = heights >= np.percentile(heights, 80)
        blocks = np.where(cap, snow, blocks)
    elif biome == "badlands":
        bands = cfg["bands"]
        band_noise = multi_octave(rng, size, size, octaves=1, base_cell=4)
        idx = np.clip((band_noise * len(bands)).astype(np.int32), 0, len(bands) - 1)
        band_ids = np.array([block_id(b) for b in bands], dtype=np.int32)
        blocks = band_ids[idx]
    elif biome == "snowy" and rng.random() < 0.3:
        ice = block_id(cfg["ice"])
        low = heights <= np.percentile(heights, 20)
        blocks = np.where(low, ice, blocks)
        heights = np.where(low, SEA_LEVEL - 1, heights)

    decor = cfg.get("decor", ())
    if decor and rng.random() < 0.8:
        speckle_p = float(rng.uniform(0.01, 0.08))
        speckle = rng.random((size, size)) < speckle_p
        speckle &= blocks == surface
        choice_idx = rng.integers(0, len(decor), size=(size, size))
        decor_ids = np.array([block_id(d) for d in decor], dtype=np.int32)
        blocks = np.where(speckle, decor_ids[choice_idx], blocks)
        heights = np.where(speckle, heights + 1, heights)

    if biome == "forest":
        blocks, heights = _scatter_canopies(rng, blocks, heights, cfg)

    return blocks, heights, biome


def _apply_cliff(rng, heights):
    size = heights.shape[0]
    axis = int(rng.integers(0, 2))
    split = int(rng.integers(size // 4, 3 * size // 4))
    jump = int(rng.integers(4, 10)) * (1 if rng.random() < 0.5 else -1)
    out = heights.copy()
    if axis == 0:
        out[split:, :] += jump
    else:
        out[:, split:] += jump
    return out


def _scatter_canopies(rng, blocks, heights, cfg):
    size = blocks.shape[0]
    canopy = block_id(cfg["canopy"])
    n_trees = int(rng.integers(3, 14))
    out_blocks = blocks.copy()
    out_heights = heights.copy()
    for _ in range(n_trees):
        cy = int(rng.integers(0, size))
        cx = int(rng.integers(0, size))
        radius = int(rng.integers(2, 5))
        lift = int(rng.integers(4, 9))
        r0, r1 = max(0, cy - radius), min(size, cy + radius + 1)
        c0, c1 = max(0, cx - radius), min(size, cx + radius + 1)
        yy, xx = np.mgrid[r0:r1, c0:c1]
        dist = np.sqrt((yy - cy) ** 2 + (xx - cx) ** 2)
        disk = dist <= radius
        out_blocks[r0:r1, c0:c1] = np.where(disk, canopy, out_blocks[r0:r1, c0:c1])
        out_heights[r0:r1, c0:c1] = np.where(disk, heights[r0:r1, c0:c1] + lift, out_heights[r0:r1, c0:c1])
    return out_blocks, out_heights
