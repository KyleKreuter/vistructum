import numpy as np


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


def rotate45(mask):
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


def jitter_mask(mask, rng, remove_frac=0.03, add_frac=0.01, shift_prob=0.0):
    out = mask.copy()
    if remove_frac > 0:
        on = np.argwhere(out)
        n = int(len(on) * remove_frac)
        if n and len(on):
            idx = rng.choice(len(on), size=min(n, len(on)), replace=False)
            for i in idx:
                r, c = on[i]
                out[r, c] = False
    if add_frac > 0:
        off = np.argwhere(~out)
        n = int(off.shape[0] * add_frac * (mask.sum() / mask.size))
        if n and len(off):
            idx = rng.choice(len(off), size=min(n, len(off)), replace=False)
            for i in idx:
                r, c = off[i]
                out[r, c] = True
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
