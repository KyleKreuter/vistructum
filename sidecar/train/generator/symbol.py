import numpy as np

MIN_SIZE = 5
MAX_SIZE = 60


def sample_size_thick(rng, max_size=MAX_SIZE, min_size=MIN_SIZE):
    size = int(rng.integers(min_size, max_size + 1))
    thick = int(rng.integers(1, max(2, size // 5) + 1))
    return size, thick


def build_symbol_mask(size, thick, mirror):
    size = max(5, size)
    if size % 2 == 0:
        size -= 1
    thick = max(1, min(thick, size // 2))
    if thick % 2 == 0:
        thick = max(1, thick - 1)
    canvas = np.zeros((size, size), dtype=bool)
    cx = size // 2
    cy = size // 2
    half = thick // 2
    canvas[:, max(0, cx - half):cx + half + thick % 2] = True
    canvas[max(0, cy - half):cy + half + thick % 2, :] = True
    if not mirror:
        canvas[0:thick, cx:size] = True
        canvas[cy:size, size - thick:size] = True
        canvas[size - thick:size, 0:cx] = True
        canvas[0:cy, 0:thick] = True
    else:
        canvas[0:thick, 0:cx] = True
        canvas[cy:size, 0:thick] = True
        canvas[size - thick:size, cx:size] = True
        canvas[0:cy, size - thick:size] = True
    return canvas


def _pad_square(mask):
    h, w = mask.shape
    size = max(h, w)
    out = np.zeros((size, size), dtype=mask.dtype)
    out[(size - h) // 2:(size - h) // 2 + h, (size - w) // 2:(size - w) // 2 + w] = mask
    return out


def rotate45(mask):
    mask = _pad_square(mask)
    size = mask.shape[0]
    cy = cx = (size - 1) / 2.0
    ys, xs = np.mgrid[0:size, 0:size]
    ys = ys.astype(np.float64) - cy
    xs = xs.astype(np.float64) - cx
    ang = np.pi / 4.0
    ca, sa = np.cos(-ang), np.sin(-ang)
    src_y = ca * ys - sa * xs + cy
    src_x = sa * ys + ca * xs + cx
    sy = np.rint(src_y).astype(np.int64)
    sx = np.rint(src_x).astype(np.int64)
    valid = (sy >= 0) & (sy < size) & (sx >= 0) & (sx < size)
    out = np.zeros_like(mask)
    out[valid] = mask[sy[valid], sx[valid]]
    return out


def visible_fraction(footprint, top, left, crop_top, crop_left, crop_size):
    rows, cols = np.nonzero(footprint)
    if len(rows) == 0:
        return 0.0
    gr = rows + top
    gc = cols + left
    inside = (gr >= crop_top) & (gr < crop_top + crop_size) & (gc >= crop_left) & (gc < crop_left + crop_size)
    return float(inside.mean())


def is_c4_symmetric(mask):
    return bool(np.array_equal(mask, np.rot90(mask)))


def is_mirror_symmetric(mask):
    return bool(np.array_equal(mask, np.fliplr(mask)) or np.array_equal(mask, np.flipud(mask)))


def is_c4_chiral(mask):
    return is_c4_symmetric(mask) and not is_mirror_symmetric(mask)


def sanitize_negative_mask(mask, rng):
    if not mask.any():
        return mask
    out = mask
    guard = 0
    while is_c4_chiral(out) and guard < 8:
        on = np.argwhere(out)
        i = rng.integers(0, len(on))
        r, c = on[i]
        out = out.copy()
        out[r, c] = False
        guard += 1
    return out



SMALL_ARM_SHARE = 0.45
MAX_ARM = 30
MAX_ARM_RATIO = 2.5
MAX_HOOK_RATIO = 1.3
HOOK_SHIFT_SHARE = 0.2


def sample_irregular_arms(rng):
    if rng.random() < SMALL_ARM_SHARE:
        base = int(rng.integers(2, 5))
    else:
        base = int(rng.integers(2, MAX_ARM + 1))
    thick = int(rng.integers(1, max(1, base // 3) + 1))
    low = max(thick + 1, int(np.ceil(base / MAX_ARM_RATIO)))
    high = max(low, min(MAX_ARM, int(base * MAX_ARM_RATIO)))
    arms = [int(rng.integers(low, high + 1)) if rng.random() < 0.6 else max(low, base) for _ in range(4)]
    return arms, thick


def _rotate_clockwise(cells, thick, turns):
    for _ in range(turns):
        cells = [(c, thick - 1 - r) for r, c in cells]
    return cells


def _upward_arm(rng, arm, thick, hook_side):
    cells = [(r, c) for r in range(-arm, 0) for c in range(thick)]
    if hook_side == 0:
        return cells
    shift = 1 if arm > thick and rng.random() < HOOK_SHIFT_SHARE else 0
    hook = int(rng.integers(1, max(1, round(arm * MAX_HOOK_RATIO)) + 1))
    columns = range(thick, thick + hook) if hook_side > 0 else range(-hook, 0)
    cells += [(r, c) for r in range(-arm + shift, -arm + shift + thick) for c in columns]
    return cells


def build_irregular_mask(rng, hook_sides):
    arms, thick = sample_irregular_arms(rng)
    cells = [(r, c) for r in range(thick) for c in range(thick)]
    for turn, (arm, side) in enumerate(zip(arms, hook_sides)):
        cells += _rotate_clockwise(_upward_arm(rng, arm, thick, side), thick, turn)
    rows = np.array([r for r, _ in cells])
    cols = np.array([c for _, c in cells])
    mask = np.zeros((rows.max() - rows.min() + 1, cols.max() - cols.min() + 1), dtype=bool)
    mask[rows - rows.min(), cols - cols.min()] = True
    return mask


def build_irregular_symbol(rng, mirror):
    return build_irregular_mask(rng, (-1 if mirror else 1,) * 4)
