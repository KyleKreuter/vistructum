import numpy as np

from vistructum_ml import features
from vistructum_ml.scene import Scene

from .builds import BUILD_FAMILIES, village
from .palette import BUILD_BLOCKS, ID_LUMINANCE, block_id
from .shapes import HARD_NEGATIVE_FAMILIES
from .symbol import build_symbol_mask, rotate45, sample_size_thick, sanitize_negative_mask, visible_fraction
from .terrain import generate_terrain

CANVAS = 96
CROP = 64
MARGIN = (CANVAS - CROP) // 2

MIN_VISIBLE = 0.9
MAX_REDRAWS = 24
EXTRA_GAP = 1
MAX_DECOYS = 6

HOLDOUT_ONLY_HARD_FAMILY = "windmill-3"

POS_BASE_MODES = (
    "raised", "raised-same", "flush-diff", "flush-same-lum", "carved", "mixed", "outlined",
)
POS_CONTEXTS = ("plain", "on-roof", "in-water", "in-snow", "on-plaza")

_BUILD_POOL_NAMES = list(BUILD_FAMILIES) + ["village"]


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


def _random_build_stamp(rng):
    name = _BUILD_POOL_NAMES[int(rng.integers(0, len(_BUILD_POOL_NAMES)))]
    fn = BUILD_FAMILIES.get(name, village)
    return name, fn(rng)


def _stamp_decoys(rng, blocks, heights, n):
    for _ in range(n):
        _name, stamp = _random_build_stamp(rng)
        h, w = stamp.mask.shape
        if h >= CANVAS or w >= CANVAS:
            continue
        top, left = _random_top_left(rng, CANVAS, h, w, bias_center=False, jitter=0)
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
    size, thick = sample_size_thick(rng, max_size=size_max)
    mirror = bool(rng.random() < 0.5)
    rot_k = int(rng.integers(0, 4))
    mask = build_symbol_mask(size, thick, mirror)
    mask = np.rot90(mask, rot_k)
    diag = rng.random() < 0.03
    if diag:
        mask = rotate45(mask)
    return mask, mirror, diag


def _in_crop_top_left(rng, h, w):
    # anywhere the shape fits fully inside the crop, the way a symbol sits somewhere inside a scan window;
    # shapes larger than the crop stay roughly centered
    if h > CROP or w > CROP:
        return _random_top_left(rng, CANVAS, h, w, bias_center=True, jitter=3)
    return MARGIN + int(rng.integers(0, CROP - h + 1)), MARGIN + int(rng.integers(0, CROP - w + 1))


def _place_shape(rng, canvas_size, mask):
    for _attempt in range(MAX_REDRAWS):
        top, left = _in_crop_top_left(rng, *mask.shape)
        vis = visible_fraction(mask, top, left, MARGIN, MARGIN, CROP)
        if vis >= MIN_VISIBLE:
            return top, left, vis
    top, left = _random_top_left(rng, canvas_size, *mask.shape, bias_center=True, jitter=0)
    vis = visible_fraction(mask, top, left, MARGIN, MARGIN, CROP)
    return top, left, vis


def footprint_box(footprint, gap=0):
    rows, cols = np.nonzero(footprint)
    if len(rows) == 0:
        return None
    return (int(rows.min()) - gap, int(cols.min()) - gap, int(rows.max()) + 1 + gap, int(cols.max()) + 1 + gap)


def _box_hits(footprint, top, left, box):
    h, w = footprint.shape
    r0, c0 = max(box[0] - top, 0), max(box[1] - left, 0)
    r1, c1 = min(box[2] - top, h), min(box[3] - left, w)
    return r1 > r0 and c1 > c0 and bool(footprint[r0:r1, c0:c1].any())


def _context_box(rng, blocks, heights, context, top, left, h, w):
    if context == "on-roof":
        stamp = BUILD_FAMILIES["house"](rng)
        sh, sw = max(h + 4, min(stamp.mask.shape[0], CANVAS)), max(w + 4, min(stamp.mask.shape[1], CANVAS))
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
        sh, sw = max(h + 4, min(stamp.mask.shape[0], CANVAS)), max(w + 4, min(stamp.mask.shape[1], CANVAS))
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


def _stamp_shape(rng, blocks, heights, mask):
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

    suffix = mode
    if context != "plain":
        suffix += f"-{context}"
    if sloppy:
        suffix += "-sloppy"
    return footprint, suffix, vis


def _stamp_symbol(rng, blocks, heights, holdout):
    mask, _mirror, diag = _build_symbol_variant(rng, holdout)
    footprint, suffix, vis = _stamp_shape(rng, blocks, heights, mask)
    subtype = f"pos-{suffix}"
    if diag:
        subtype += "-diag45"
    return footprint, subtype, vis


def _negative_pool(holdout):
    hard = [k for k in HARD_NEGATIVE_FAMILIES if holdout or k != HOLDOUT_ONLY_HARD_FAMILY]
    builds = list(BUILD_FAMILIES) + ["village"]
    return hard, builds


def _stamp_hard_negative(rng, blocks, heights, holdout):
    hard, _builds = _negative_pool(holdout)
    name = hard[int(rng.integers(0, len(hard)))]
    raw = HARD_NEGATIVE_FAMILIES[name](rng)
    raw = sanitize_negative_mask(raw, rng)
    rot_k = int(rng.integers(0, 4))
    raw = np.rot90(raw, rot_k)
    footprint, suffix, vis = _stamp_shape(rng, blocks, heights, raw)
    return footprint, f"neg-hard-{name}-{suffix}", vis


def _stamp_negative_family(rng, blocks, heights, holdout):
    _hard, builds = _negative_pool(holdout)
    roll = rng.random()
    if roll < 0.10:
        return "neg-terrain", np.zeros(blocks.shape, dtype=bool)
    if roll < 0.10 + 0.70:
        footprint, subtype, _vis = _stamp_hard_negative(rng, blocks, heights, holdout)
        return subtype, footprint
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

    # scan areas pack several overlapping builds into one window (towns, farms around a plaza); crops need the same
    # clutter or dense build-up is never seen as negative and becomes the main source of false scan flags
    n_decoys = int(rng.integers(0, MAX_DECOYS + 1)) * decoy_mult
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


def _apply_dropout(rng, footprint, keep_lo=0.85, keep_hi=1.0):
    modified = np.zeros(footprint.shape, dtype=bool)
    cells = np.argwhere(footprint)
    if len(cells) == 0:
        return modified
    keep_frac = float(rng.uniform(keep_lo, keep_hi))
    n_keep = max(1, int(len(cells) * keep_frac))
    idx = rng.choice(len(cells), size=n_keep, replace=False)
    modified[cells[idx, 0], cells[idx, 1]] = True
    return modified


def _sprinkle_scatter(rng, modified, p=0.25, max_n=8):
    if rng.random() < p:
        n = int(rng.integers(1, max_n + 1))
        rr = rng.integers(0, CANVAS, size=n)
        cc = rng.integers(0, CANVAS, size=n)
        modified = modified.copy()
        modified[rr, cc] = True
    return modified


def _stamp_plain_build(rng, blocks, heights, holdout, avoid=None):
    """avoid: a (top, left, bottom, right) box the build must not touch, so it cannot bury the primary shape"""
    _hard, builds = _negative_pool(holdout)
    name = builds[int(rng.integers(0, len(builds)))]
    fn = BUILD_FAMILIES.get(name, village)
    stamp = fn(rng)
    h, w = stamp.mask.shape
    if h >= CANVAS or w >= CANVAS:
        return np.zeros((CANVAS, CANVAS), dtype=bool), None
    if avoid is None:
        top, left, _ = _place_shape(rng, CANVAS, stamp.mask)
    else:
        for _attempt in range(MAX_REDRAWS):
            top, left = _random_top_left(rng, CANVAS, h, w, bias_center=False, jitter=0)
            if not _box_hits(stamp.mask, top, left, avoid):
                break
        else:
            return np.zeros((CANVAS, CANVAS), dtype=bool), None
    footprint = _stamp(blocks, heights, stamp.mask, stamp.block, stamp.height_delta, top, left)
    return footprint, f"neg-{name}"


def _mask_primary_negative(rng, blocks, heights, holdout):
    if rng.random() < 0.65:
        footprint, subtype, _vis = _stamp_hard_negative(rng, blocks, heights, holdout)
        return footprint, subtype
    footprint, subtype = _stamp_plain_build(rng, blocks, heights, holdout)
    if subtype is None:
        return np.zeros((CANVAS, CANVAS), dtype=bool), "neg-terrain"
    return footprint, subtype


def make_mask_sample(rng, label, holdout=False):
    blocks, heights, biome = generate_terrain(rng, CANVAS, amplitude=float(rng.uniform(0, 10)))
    n_decoys = int(rng.integers(0, 3)) * (2 if holdout else 1)
    _stamp_decoys(rng, blocks, heights, n_decoys)

    if label == 1:
        footprint, subtype, vis = _stamp_symbol(rng, blocks, heights, holdout)
    else:
        footprint, subtype = _mask_primary_negative(rng, blocks, heights, holdout)
        vis = None

    modified = _apply_dropout(rng, footprint)

    # the same "several builds in one crop" mixing applies to positives and
    # negatives alike, with the same probabilities, so the NUMBER of shapes
    # merged into `modified` is not itself a label cue.
    # extra builds keep a gap to the primary shape: a symbol buried inside another modified region is invisible
    # in the binary mask, so labelling it positive would only teach noise (the fullscan model covers that case)
    avoid = footprint_box(footprint, gap=EXTRA_GAP)
    n_extra = int(rng.integers(0, 3))
    for _ in range(n_extra):
        extra_footprint, _extra_subtype = _stamp_plain_build(rng, blocks, heights, holdout, avoid)
        modified |= _apply_dropout(rng, extra_footprint)

    modified = _sprinkle_scatter(rng, modified)
    _, _, modified = _apply_d4(rng, blocks, heights, modified)
    luminance = np.zeros((CANVAS, CANVAS), dtype=np.uint8)
    scene = Scene(blocks, heights, luminance, modified)
    feats = features.extract("mask", scene)
    cropped = feats[:, MARGIN:MARGIN + CROP, MARGIN:MARGIN + CROP]
    return cropped.astype(np.uint8), int(label), subtype, biome, vis


MAKE_SAMPLE = {"fullscan": make_fullscan_sample, "mask": make_mask_sample}
