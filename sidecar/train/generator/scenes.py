import numpy as np

from vistructum_ml import features
from vistructum_ml.scene import Scene

from .builds import BUILD_FAMILIES, village
from .palette import BUILD_BLOCKS, ID_LUMINANCE, block_id
from .shapes import HARD_NEGATIVE_FAMILIES
from .symbol import build_symbol_mask, rotate45, sanitize_negative_mask, visible_fraction
from .terrain import generate_terrain

CANVAS = 96
CROP = 64
MARGIN = (CANVAS - CROP) // 2

MIN_VISIBLE = 0.9
MAX_REDRAWS = 24

HOLDOUT_ONLY_HARD_FAMILY = "windmill-3"

POS_BASE_MODES = (
    "raised", "raised-same", "flush-diff", "flush-same-lum", "carved", "mixed", "outlined",
)
POS_CONTEXTS = ("plain", "on-roof", "in-water", "in-snow", "on-plaza")


def _stamp(blocks, heights, mask, block_arr, delta_arr, top, left):
    h, w = mask.shape
    r0, r1 = max(0, top), min(blocks.shape[0], top + h)
    c0, c1 = max(0, left), min(blocks.shape[1], left + w)
    footprint = np.zeros(blocks.shape, dtype=bool)
    if r0 >= r1 or c0 >= c1:
        return footprint
    mr0, mr1 = r0 - top, r1 - top
    mc0, mc1 = c0 - left, c1 - left
    m = mask[mr0:mr1, mc0:mc1]
    b = block_arr[mr0:mr1, mc0:mc1] if hasattr(block_arr, "shape") else block_arr
    d = delta_arr[mr0:mr1, mc0:mc1] if hasattr(delta_arr, "shape") else delta_arr
    rb = blocks[r0:r1, c0:c1]
    rh = heights[r0:r1, c0:c1]
    blocks[r0:r1, c0:c1] = np.where(m, b, rb)
    heights[r0:r1, c0:c1] = np.where(m, rh + d, rh)
    footprint[r0:r1, c0:c1] = m
    return footprint


def _random_top_left(rng, size, h, w, bias_center, jitter):
    if bias_center:
        top = (size - h) // 2 + int(rng.integers(-jitter, jitter + 1))
        left = (size - w) // 2 + int(rng.integers(-jitter, jitter + 1))
    else:
        top = int(rng.integers(0, max(1, size - h + 1)))
        left = int(rng.integers(0, max(1, size - w + 1)))
    top = int(np.clip(top, 0, max(0, size - h)))
    left = int(np.clip(left, 0, max(0, size - w)))
    return top, left


def _stamp_decoys(rng, blocks, heights, n, avoid_center_box=None):
    families = list(BUILD_FAMILIES.values()) + [village]
    for _ in range(n):
        fn = families[int(rng.integers(0, len(families)))]
        stamp = fn(rng)
        h, w = stamp.mask.shape
        if h >= CANVAS or w >= CANVAS:
            continue
        for _try in range(4):
            top, left = _random_top_left(rng, CANVAS, h, w, bias_center=False, jitter=0)
            if avoid_center_box is not None:
                at, al, ah, aw = avoid_center_box
                overlap = not (top + h <= at or top >= at + ah or left + w <= al or left >= al + aw)
                if overlap:
                    continue
            break
        _stamp(blocks, heights, stamp.mask, stamp.block, stamp.height_delta, top, left)


def _shape_block_and_delta(mask, rng, mixed=False, outlined=False):
    if outlined:
        border = np.zeros_like(mask)
        border[:-1, :] |= mask[:-1, :] & ~mask[1:, :]
        border[1:, :] |= mask[1:, :] & ~mask[:-1, :]
        border[:, :-1] |= mask[:, :-1] & ~mask[:, 1:]
        border[:, 1:] |= mask[:, 1:] & ~mask[:, :-1]
        border &= mask
        fill_blk = block_id(BUILD_BLOCKS[int(rng.integers(0, len(BUILD_BLOCKS)))])
        border_blk = block_id(BUILD_BLOCKS[int(rng.integers(0, len(BUILD_BLOCKS)))])
        block = np.where(border, border_blk, fill_blk).astype(np.int32)
        return block
    if mixed:
        n = int(rng.integers(2, 4))
        ids = [block_id(BUILD_BLOCKS[int(rng.integers(0, len(BUILD_BLOCKS)))]) for _ in range(n)]
        idx = rng.integers(0, n, size=mask.shape)
        return np.array(ids, dtype=np.int32)[idx]
    blk = block_id(BUILD_BLOCKS[int(rng.integers(0, len(BUILD_BLOCKS)))])
    return np.full(mask.shape, blk, dtype=np.int32)


def _same_luminance_other_block(rng, ground_id):
    lum = int(ID_LUMINANCE[ground_id])
    candidates = np.nonzero(ID_LUMINANCE == lum)[0]
    candidates = candidates[candidates != ground_id]
    if len(candidates) == 0:
        return int(rng.integers(0, len(ID_LUMINANCE)))
    return int(candidates[int(rng.integers(0, len(candidates)))])


def _build_symbol_variant(rng, holdout):
    size_max = 64 if holdout else 60
    size = int(rng.integers(5, size_max + 1))
    thick = int(rng.integers(1, max(2, size // 5) + 1))
    mirror = bool(rng.random() < 0.5)
    rot_k = int(rng.integers(0, 4))
    mask = build_symbol_mask(size, thick, mirror)
    mask = np.rot90(mask, rot_k)
    diag = rng.random() < 0.03
    if diag:
        mask = rotate45(mask)
    return mask, mirror, diag


def _place_shape(rng, canvas_size, mask):
    h, w = mask.shape
    for attempt in range(MAX_REDRAWS):
        jitter = 3 if attempt < MAX_REDRAWS - 1 else 0
        top, left = _random_top_left(rng, canvas_size, h, w, bias_center=True, jitter=jitter)
        vis = visible_fraction(mask, top, left, MARGIN, MARGIN, CROP)
        if vis >= MIN_VISIBLE:
            return top, left, vis
    top, left = _random_top_left(rng, canvas_size, h, w, bias_center=True, jitter=0)
    vis = visible_fraction(mask, top, left, MARGIN, MARGIN, CROP)
    return top, left, vis


def _context_box(rng, blocks, heights, context, top, left, h, w):
    if context == "on-roof":
        stamp = BUILD_FAMILIES["house"](rng)
        sh, sw = max(h + 4, stamp.mask.shape[0]), max(w + 4, stamp.mask.shape[1])
        roof_blk = int(stamp.block[0, 0])
        roof_h = int(stamp.height_delta.max())
        rtop, rleft = top - (sh - h) // 2, left - (sw - w) // 2
        r0, r1 = max(0, rtop), min(blocks.shape[0], rtop + sh)
        c0, c1 = max(0, rleft), min(blocks.shape[1], rleft + sw)
        if r1 > r0 and c1 > c0:
            base = int(np.median(heights[r0:r1, c0:c1]))
            blocks[r0:r1, c0:c1] = roof_blk
            heights[r0:r1, c0:c1] = base + roof_h
        return "raised", int(rng.integers(1, 3))
    if context == "on-plaza":
        stamp = BUILD_FAMILIES["plaza"](rng)
        sh, sw = max(h + 4, stamp.mask.shape[0]), max(w + 4, stamp.mask.shape[1])
        ptop, pleft = top - (sh - h) // 2, left - (sw - w) // 2
        base = int(np.median(heights))
        r0, r1 = max(0, ptop), min(blocks.shape[0], ptop + sh)
        c0, c1 = max(0, pleft), min(blocks.shape[1], pleft + sw)
        if r1 > r0 and c1 > c0:
            blocks[r0:r1, c0:c1] = block_id(BUILD_BLOCKS[int(rng.integers(0, len(BUILD_BLOCKS)))])
            heights[r0:r1, c0:c1] = base
        return "flush-diff", 0
    if context == "in-water":
        sh = h + int(rng.integers(4, 10))
        sw = w + int(rng.integers(4, 10))
        wtop, wleft = top - (sh - h) // 2, left - (sw - w) // 2
        base = int(np.median(heights))
        r0, r1 = max(0, wtop), min(blocks.shape[0], wtop + sh)
        c0, c1 = max(0, wleft), min(blocks.shape[1], wleft + sw)
        if r1 > r0 and c1 > c0:
            blocks[r0:r1, c0:c1] = block_id("water")
            heights[r0:r1, c0:c1] = base
        return "raised", int(rng.integers(1, 3))
    if context == "in-snow":
        sh = h + int(rng.integers(4, 10))
        sw = w + int(rng.integers(4, 10))
        stop, sleft = top - (sh - h) // 2, left - (sw - w) // 2
        base = int(np.median(heights))
        r0, r1 = max(0, stop), min(blocks.shape[0], stop + sh)
        c0, c1 = max(0, sleft), min(blocks.shape[1], sleft + sw)
        if r1 > r0 and c1 > c0:
            blocks[r0:r1, c0:c1] = block_id("snow_block")
            heights[r0:r1, c0:c1] = base
        return "raised-diff", int(rng.integers(1, 3))
    return None, None


def _stamp_symbol(rng, blocks, heights, holdout):
    mask, _mirror, diag = _build_symbol_variant(rng, holdout)
    top, left, vis = _place_shape(rng, CANVAS, mask)
    h, w = mask.shape

    context = str(rng.choice(POS_CONTEXTS, p=(0.55, 0.14, 0.11, 0.10, 0.10)))
    forced_mode, forced_delta = _context_box(rng, blocks, heights, context, top, left, h, w)

    mode = forced_mode or POS_BASE_MODES[int(rng.integers(0, len(POS_BASE_MODES)))]
    ground_block_region = blocks[top:top + h, left:left + w]
    ground_id = int(np.bincount(ground_block_region.ravel().clip(min=0)).argmax()) if ground_block_region.size else 0

    if mode in ("raised", "raised-diff"):
        delta = forced_delta if forced_delta is not None else int(rng.integers(1, 4))
        block = np.full((h, w), block_id(BUILD_BLOCKS[int(rng.integers(0, len(BUILD_BLOCKS)))]), dtype=np.int32)
        delta_arr = np.full((h, w), delta, dtype=np.int32)
    elif mode == "raised-same":
        block = ground_block_region.copy()
        delta_arr = np.full((h, w), int(rng.integers(1, 4)), dtype=np.int32)
    elif mode == "flush-same-lum":
        block = np.full((h, w), _same_luminance_other_block(rng, ground_id), dtype=np.int32)
        delta_arr = np.zeros((h, w), dtype=np.int32)
    elif mode == "carved":
        block = np.full((h, w), block_id(BUILD_BLOCKS[int(rng.integers(0, len(BUILD_BLOCKS)))]), dtype=np.int32)
        delta_arr = np.full((h, w), -int(rng.integers(1, 4)), dtype=np.int32)
    elif mode == "mixed":
        block = _shape_block_and_delta(mask, rng, mixed=True)
        delta_arr = np.zeros((h, w), dtype=np.int32)
    elif mode == "outlined":
        block = _shape_block_and_delta(mask, rng, outlined=True)
        delta_arr = np.zeros((h, w), dtype=np.int32)
    else:
        block = np.full((h, w), block_id(BUILD_BLOCKS[int(rng.integers(0, len(BUILD_BLOCKS)))]), dtype=np.int32)
        delta_arr = np.zeros((h, w), dtype=np.int32)

    sloppy = rng.random() < 0.25
    stamp_mask = mask
    if sloppy:
        remove = float(rng.uniform(0.01, 0.05))
        add = float(rng.uniform(0.0, 0.03))
        on = np.argwhere(stamp_mask)
        keep = np.ones(len(on), dtype=bool)
        n_remove = int(len(on) * remove)
        if n_remove:
            idx = rng.choice(len(on), size=n_remove, replace=False)
            keep[idx] = False
        stamp_mask = np.zeros_like(mask)
        stamp_mask[on[keep, 0], on[keep, 1]] = True
        off = np.argwhere(~mask)
        n_add = int(len(off) * add)
        if n_add and len(off):
            idx = rng.choice(len(off), size=n_add, replace=False)
            stamp_mask[off[idx, 0], off[idx, 1]] = True

    footprint = _stamp(blocks, heights, stamp_mask, block, delta_arr, top, left)

    subtype = f"pos-{mode}"
    if context != "plain":
        subtype += f"-{context}"
    if sloppy:
        subtype += "-sloppy"
    if diag:
        subtype += "-diag45"
    return footprint, subtype, vis


def _negative_pool(holdout):
    hard = [k for k in HARD_NEGATIVE_FAMILIES if holdout or k != HOLDOUT_ONLY_HARD_FAMILY]
    builds = list(BUILD_FAMILIES) + ["village"]
    return hard, builds


def _stamp_negative_family(rng, blocks, heights, holdout):
    hard, builds = _negative_pool(holdout)
    roll = rng.random()
    if roll < 0.12:
        return "neg-terrain", np.zeros(blocks.shape, dtype=bool)
    if roll < 0.12 + 0.35:
        name = hard[int(rng.integers(0, len(hard)))]
        raw = HARD_NEGATIVE_FAMILIES[name](rng)
        raw = sanitize_negative_mask(raw, rng)
        h, w = raw.shape
        top, left = _place_shape(rng, CANVAS, raw)[:2]
        blk = block_id(BUILD_BLOCKS[int(rng.integers(0, len(BUILD_BLOCKS)))])
        delta = int(rng.integers(0, 4))
        footprint = _stamp(blocks, heights, raw, blk, delta, top, left)
        return f"neg-hard-{name}", footprint
    name = builds[int(rng.integers(0, len(builds)))]
    fn = BUILD_FAMILIES.get(name, village)
    stamp = fn(rng)
    h, w = stamp.mask.shape
    if h >= CANVAS:
        h = CANVAS - 1
        stamp = type(stamp)(stamp.mask[:h], stamp.block[:h], stamp.height_delta[:h], stamp.name)
    if w >= CANVAS:
        w = CANVAS - 1
        stamp = type(stamp)(stamp.mask[:, :w], stamp.block[:, :w], stamp.height_delta[:, :w], stamp.name)
    top, left, _ = _place_shape(rng, CANVAS, stamp.mask)
    footprint = _stamp(blocks, heights, stamp.mask, stamp.block, stamp.height_delta, top, left)
    return f"neg-{name}", footprint


def _apply_d4(rng, blocks, heights, modified):
    k = int(rng.integers(0, 4))
    flip = rng.random() < 0.5
    blocks = np.rot90(blocks, k)
    heights = np.rot90(heights, k)
    modified = np.rot90(modified, k)
    if flip:
        blocks = np.fliplr(blocks)
        heights = np.fliplr(heights)
        modified = np.fliplr(modified)
    return np.ascontiguousarray(blocks), np.ascontiguousarray(heights), np.ascontiguousarray(modified)


def _luminance_from_blocks(blocks, rng, jitter):
    lum = ID_LUMINANCE[blocks].astype(np.int32)
    if jitter > 0:
        noise = rng.integers(-jitter, jitter + 1, size=blocks.shape)
        lum = lum + noise
    return np.clip(lum, 0, 255).astype(np.uint8)


def make_fullscan_sample(rng, label, holdout=False):
    amp_max = 16.0 if holdout else 12.0
    lum_jitter = 15 if holdout else 0
    decoy_mult = 2 if holdout else 1

    blocks, heights, biome = generate_terrain(rng, CANVAS, amplitude=float(rng.uniform(0, amp_max)))
    modified = np.zeros((CANVAS, CANVAS), dtype=bool)

    n_decoys = int(rng.integers(0, 3)) * decoy_mult
    _stamp_decoys(rng, blocks, heights, n_decoys)

    if label == 1:
        _footprint, subtype, vis = _stamp_symbol(rng, blocks, heights, holdout)
    else:
        subtype, _footprint = _stamp_negative_family(rng, blocks, heights, holdout)
        vis = None

    blocks, heights, modified = _apply_d4(rng, blocks, heights, modified)
    luminance = _luminance_from_blocks(blocks, rng, lum_jitter)

    scene = Scene(blocks, heights, luminance, modified)
    feats = features.extract("fullscan", scene)
    cropped = feats[:, MARGIN:MARGIN + CROP, MARGIN:MARGIN + CROP]
    return cropped.astype(np.uint8), int(label), subtype, biome, vis


def _mask_negative_footprints(rng, blocks, heights, holdout):
    modified = np.zeros((CANVAS, CANVAS), dtype=bool)
    n_builds = int(rng.integers(1, 4))
    subtype = "neg-scatter"
    for i in range(n_builds):
        hard, builds = _negative_pool(holdout)
        if rng.random() < 0.35:
            name = hard[int(rng.integers(0, len(hard)))]
            raw = HARD_NEGATIVE_FAMILIES[name](rng)
            raw = sanitize_negative_mask(raw, rng)
            top, left, _ = _place_shape(rng, CANVAS, raw)
            blk = block_id(BUILD_BLOCKS[int(rng.integers(0, len(BUILD_BLOCKS)))])
            footprint = _stamp(blocks, heights, raw, blk, int(rng.integers(0, 3)), top, left)
            subtype = f"neg-hard-{name}"
        else:
            name = builds[int(rng.integers(0, len(builds)))]
            fn = BUILD_FAMILIES.get(name, village)
            stamp = fn(rng)
            h, w = stamp.mask.shape
            if h >= CANVAS or w >= CANVAS:
                continue
            top, left, _ = _place_shape(rng, CANVAS, stamp.mask)
            footprint = _stamp(blocks, heights, stamp.mask, stamp.block, stamp.height_delta, top, left)
            subtype = f"neg-{name}"
        keep_frac = float(rng.uniform(0.3, 1.0))
        cells = np.argwhere(footprint)
        if len(cells):
            n_keep = max(1, int(len(cells) * keep_frac))
            idx = rng.choice(len(cells), size=n_keep, replace=False)
            modified[cells[idx, 0], cells[idx, 1]] = True
    if rng.random() < 0.3:
        n_scatter = int(rng.integers(1, 15))
        rr = rng.integers(0, CANVAS, size=n_scatter)
        cc = rng.integers(0, CANVAS, size=n_scatter)
        modified[rr, cc] = True
        subtype = "neg-scatter"
    return modified, subtype


def make_mask_sample(rng, label, holdout=False):
    blocks, heights, biome = generate_terrain(rng, CANVAS, amplitude=float(rng.uniform(0, 10)))
    n_decoys = int(rng.integers(0, 3)) * (2 if holdout else 1)
    _stamp_decoys(rng, blocks, heights, n_decoys)

    if label == 1:
        footprint, subtype, vis = _stamp_symbol(rng, blocks, heights, holdout)
        keep_frac = float(rng.uniform(0.85, 1.0))
        cells = np.argwhere(footprint)
        modified = np.zeros((CANVAS, CANVAS), dtype=bool)
        if len(cells):
            n_keep = max(1, int(len(cells) * keep_frac))
            idx = rng.choice(len(cells), size=n_keep, replace=False)
            modified[cells[idx, 0], cells[idx, 1]] = True
        if rng.random() < 0.5:
            extra_modified, _ = _mask_negative_footprints(rng, blocks, heights, holdout)
            modified |= extra_modified
    else:
        modified, subtype = _mask_negative_footprints(rng, blocks, heights, holdout)
        vis = None

    _, _, modified = _apply_d4(rng, blocks, heights, modified)
    luminance = np.zeros((CANVAS, CANVAS), dtype=np.uint8)
    scene = Scene(blocks, heights, luminance, modified)
    feats = features.extract("mask", scene)
    cropped = feats[:, MARGIN:MARGIN + CROP, MARGIN:MARGIN + CROP]
    return cropped.astype(np.uint8), int(label), subtype, biome, vis


MAKE_SAMPLE = {"fullscan": make_fullscan_sample, "mask": make_mask_sample}
