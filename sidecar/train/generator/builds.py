from dataclasses import dataclass

import numpy as np

from .palette import BUILD_BLOCKS, PATH_BLOCKS, ROOF_BLOCKS, block_id


@dataclass
class Stamp:
    mask: np.ndarray
    block: np.ndarray
    height_delta: np.ndarray
    name: str


def _pick(rng, options):
    return options[int(rng.integers(0, len(options)))]


def _rect_mask(h, w):
    return np.ones((h, w), dtype=bool)


def house(rng):
    w = int(rng.integers(4, 13))
    h = int(rng.integers(4, 13))
    mask = _rect_mask(h, w)
    roof = block_id(_pick(rng, ROOF_BLOCKS))
    block = np.full((h, w), roof, dtype=np.int32)
    if rng.random() < 0.5:
        delta = np.full((h, w), int(rng.integers(3, 7)), dtype=np.int32)
    else:
        peak = int(rng.integers(4, 9))
        eave = int(rng.integers(2, peak))
        along_rows = rng.random() < 0.5
        n = h if along_rows else w
        ridge = n // 2
        dist = np.abs(np.arange(n) - ridge)
        profile = (peak - (peak - eave) * (dist / max(1, dist.max()))).astype(np.int32)
        delta = np.broadcast_to(profile[:, None] if along_rows else profile[None, :], (h, w)).copy()
    return Stamp(mask, block, delta, "house")


def road(rng):
    length = int(rng.integers(20, 60))
    width = int(rng.integers(1, 4))
    horizontal = rng.random() < 0.5
    canvas_h = width + 10 if horizontal else length
    canvas_w = length if horizontal else width + 10
    mask = np.zeros((canvas_h, canvas_w), dtype=bool)
    pos = (canvas_h // 2) if horizontal else (canvas_w // 2)
    step_span = max(1, length // 8)
    for i in range(length):
        if i % step_span == 0 and i > 0:
            pos += int(rng.integers(-2, 3))
        if horizontal:
            lo, hi = max(0, pos - width // 2), min(canvas_h, pos - width // 2 + width)
            mask[lo:hi, i] = True
        else:
            lo, hi = max(0, pos - width // 2), min(canvas_w, pos - width // 2 + width)
            mask[i, lo:hi] = True
    blk = block_id(_pick(rng, PATH_BLOCKS))
    block = np.full(mask.shape, blk, dtype=np.int32)
    return Stamp(mask, block, np.zeros(mask.shape, dtype=np.int32), "road")


def farm(rng):
    w = int(rng.integers(8, 20))
    h = int(rng.integers(8, 20))
    mask = _rect_mask(h, w)
    block = np.full((h, w), block_id("farmland"), dtype=np.int32)
    delta = np.zeros((h, w), dtype=np.int32)
    if rng.random() < 0.4:
        for r in range(0, h, 4):
            block[r, :] = block_id("water")
    for r in range(h):
        if block[r, 0] != block_id("water") and r % 2 == 0:
            delta[r, :] = 1
            block[r, :] = np.where(block[r, :] == block_id("farmland"), block_id("wheat"), block[r, :])
    return Stamp(mask, block, delta, "farm")


def fence(rng):
    w = int(rng.integers(6, 30))
    h = int(rng.integers(6, 30))
    mask = np.zeros((h, w), dtype=bool)
    mask[0, :] = True
    mask[-1, :] = True
    mask[:, 0] = True
    mask[:, -1] = True
    blk = block_id(_pick(rng, ("oak_planks", "spruce_planks", "cobblestone", "stone_bricks")))
    block = np.full((h, w), blk, dtype=np.int32)
    delta = np.full((h, w), int(rng.integers(1, 4)), dtype=np.int32)
    return Stamp(mask, block, delta, "fence")


def _pattern(kind, h, w, rng):
    yy, xx = np.mgrid[0:h, 0:w]
    if kind == "checker":
        cell = int(rng.integers(1, 4))
        return ((yy // cell + xx // cell) % 2) == 0
    if kind == "stripes":
        cell = int(rng.integers(1, 4))
        return (xx // cell % 2) == 0
    if kind == "diagonal":
        cell = int(rng.integers(2, 5))
        return ((xx + yy) // cell % 2) == 0
    if kind == "concentric":
        cy, cx = h / 2, w / 2
        dist = np.sqrt((yy - cy) ** 2 + (xx - cx) ** 2)
        ring = int(rng.integers(2, 5))
        return (dist.astype(np.int32) // ring % 2) == 0
    if kind == "circles":
        cy, cx = h / 2, w / 2
        dist = np.sqrt((yy - cy) ** 2 + (xx - cx) ** 2)
        return dist <= min(h, w) * 0.4
    cy, cx = h // 2, w // 2
    thick = max(1, min(h, w) // 8)
    out = np.zeros((h, w), dtype=bool)
    out[cy - thick:cy + thick, :] = True
    out[:, cx - thick:cx + thick] = True
    return out


def plaza(rng):
    w = int(rng.integers(10, 30))
    h = int(rng.integers(10, 30))
    kind = _pick(rng, ("checker", "stripes", "diagonal", "concentric", "circles", "plus"))
    pat = _pattern(kind, h, w, rng)
    a, b = block_id(_pick(rng, BUILD_BLOCKS)), block_id(_pick(rng, BUILD_BLOCKS))
    block = np.where(pat, a, b).astype(np.int32)
    mask = _rect_mask(h, w)
    return Stamp(mask, block, np.zeros((h, w), dtype=np.int32), f"plaza-{kind}")


def tower(rng):
    radius = int(rng.integers(3, 10))
    size = radius * 2 + 1
    yy, xx = np.mgrid[0:size, 0:size]
    dist = np.sqrt((yy - radius) ** 2 + (xx - radius) ** 2)
    mask = dist <= radius
    blk = block_id(_pick(rng, ("stone_bricks", "quartz_block", "andesite", "cobblestone")))
    block = np.full((size, size), blk, dtype=np.int32)
    delta = np.full((size, size), int(rng.integers(3, 15)), dtype=np.int32)
    return Stamp(mask, block, delta, "tower")


def pool(rng):
    w = int(rng.integers(6, 20))
    h = int(rng.integers(6, 20))
    mask = _rect_mask(h, w)
    block = np.full((h, w), block_id("water"), dtype=np.int32)
    delta = np.full((h, w), -int(rng.integers(1, 4)), dtype=np.int32)
    return Stamp(mask, block, delta, "pool")


def railway(rng):
    length = int(rng.integers(20, 60))
    horizontal = rng.random() < 0.5
    gauge = 3
    canvas_h = gauge + 4 if horizontal else length
    canvas_w = length if horizontal else gauge + 4
    mask = np.zeros((canvas_h, canvas_w), dtype=bool)
    block = np.zeros((canvas_h, canvas_w), dtype=np.int32)
    rail = block_id("iron_block")
    tie = block_id("oak_planks")
    mid = (canvas_h // 2) if horizontal else (canvas_w // 2)
    for i in range(length):
        if horizontal:
            mask[mid - gauge // 2, i] = True
            mask[mid + gauge // 2, i] = True
            block[mid - gauge // 2, i] = rail
            block[mid + gauge // 2, i] = rail
            if i % 3 == 0:
                mask[mid - gauge // 2:mid + gauge // 2 + 1, i] = True
                block[mid - gauge // 2:mid + gauge // 2 + 1, i] = tie
        else:
            mask[i, mid - gauge // 2] = True
            mask[i, mid + gauge // 2] = True
            block[i, mid - gauge // 2] = rail
            block[i, mid + gauge // 2] = rail
            if i % 3 == 0:
                mask[i, mid - gauge // 2:mid + gauge // 2 + 1] = True
                block[i, mid - gauge // 2:mid + gauge // 2 + 1] = tie
    return Stamp(mask, block, np.zeros(mask.shape, dtype=np.int32), "railway")


SPRITE_ROWS = (5, 6, 7, 8)


def pixelart(rng):
    h = int(_pick(rng, SPRITE_ROWS))
    half = int(rng.integers(h // 2 + 1, h + 1))
    left = rng.random((h, half)) < rng.uniform(0.3, 0.6)
    if rng.random() < 0.5:
        full = np.concatenate([left, np.fliplr(left)], axis=1)
    else:
        full = np.concatenate([left, np.fliplr(left[:, :-1])], axis=1)
    blk = block_id(_pick(rng, BUILD_BLOCKS))
    block = np.full(full.shape, blk, dtype=np.int32)
    delta = np.zeros(full.shape, dtype=np.int32)
    if rng.random() < 0.5:
        delta[:] = int(rng.integers(1, 3))
    return Stamp(full, block, delta, "pixelart")


BUILD_FAMILIES = {
    "house": house,
    "road": road,
    "farm": farm,
    "fence": fence,
    "plaza": plaza,
    "tower": tower,
    "pool": pool,
    "railway": railway,
    "pixelart": pixelart,
}


def village(rng):
    w = int(rng.integers(40, 70))
    h = int(rng.integers(40, 70))
    mask = np.zeros((h, w), dtype=bool)
    block = np.zeros((h, w), dtype=np.int32)
    delta = np.zeros((h, w), dtype=np.int32)
    n_houses = int(rng.integers(3, 7))
    for _ in range(n_houses):
        st = house(rng)
        sh, sw = st.mask.shape
        if sh >= h or sw >= w:
            continue
        top = int(rng.integers(0, h - sh))
        left = int(rng.integers(0, w - sw))
        mask[top:top + sh, left:left + sw] |= st.mask
        block[top:top + sh, left:left + sw] = np.where(st.mask, st.block, block[top:top + sh, left:left + sw])
        delta[top:top + sh, left:left + sw] = np.where(st.mask, st.height_delta, delta[top:top + sh, left:left + sw])
    st = road(rng)
    sh, sw = st.mask.shape
    sh, sw = min(sh, h), min(sw, w)
    mask[:sh, :sw] |= st.mask[:sh, :sw]
    block[:sh, :sw] = np.where(st.mask[:sh, :sw], st.block[:sh, :sw], block[:sh, :sw])
    return Stamp(mask, block, delta, "village")
