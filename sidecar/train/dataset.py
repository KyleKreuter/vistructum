import argparse
import json
from pathlib import Path

import numpy as np

GRID = 64
SEED = 42
PER_CLASS = 3334
SPLIT_RATIOS = (0.8, 0.1, 0.1)
ADVERSARIAL_FRACTION = 0.2
WALL_SHARE = 1.0 / 3.0
CLIPPED_SHARE = 1.0 / 3.0
DENSE_SHARE = 1.0 / 3.0
TRANSLATE_PIXELS = 0
CUTOUT_PROBABILITY = 0.0
CUTOUT_MIN = 8
CUTOUT_MAX = 16
ZOOM_MIN = 0.8
ZOOM_MAX = 1.25
INVERSION_PROBABILITY = 0.06
LOW_CONTRAST_SHARE = 0.0
LOW_CONTRAST_MIN = 0
LOW_CONTRAST_MAX = 28

LABEL_OK = 0
LABEL_HAKENKREUZ = 1

BLACK = 16
WHITE = 235
RED = 90
WATCH = (BLACK, WHITE, RED)

GROUND_DIRNAME = "backgrounds-ground"
SKY_DIRNAME = "backgrounds-sky"

OK_KINDS = ("noise", "plus", "ring", "stripes", "checker", "letter", "sprite", "corner", "diagonal")
HARD_OK_KINDS = (1, 5, 6, 7, 8)
HARD_OK_SHARE = 0.0
MINED_DIRNAME = "mined"
MINED_OK_SHARE = 0.0
SLOP_SHARE = 0.0
SLOP_DECOYS_MIN = 1
SLOP_DECOYS_MAX = 3

LETTERS = {
    "A": ["01110", "10001", "10001", "11111", "10001", "10001", "10001"],
    "B": ["11110", "10001", "10001", "11110", "10001", "10001", "11110"],
    "E": ["11111", "10000", "10000", "11110", "10000", "10000", "11111"],
    "H": ["10001", "10001", "10001", "11111", "10001", "10001", "10001"],
    "K": ["10001", "10010", "10100", "11000", "10100", "10010", "10001"],
    "L": ["10000", "10000", "10000", "10000", "10000", "10000", "11111"],
    "N": ["10001", "11001", "10101", "10011", "10001", "10001", "10001"],
    "O": ["01110", "10001", "10001", "10001", "10001", "10001", "01110"],
    "R": ["11110", "10001", "10001", "11110", "10100", "10010", "10001"],
    "S": ["01111", "10000", "10000", "01110", "00001", "00001", "11110"],
    "T": ["11111", "00100", "00100", "00100", "00100", "00100", "00100"],
    "Z": ["11111", "00001", "00010", "00100", "01000", "10000", "11111"],
}


def fill(canvas, r0, r1, c0, c1, value):
    r0c = max(0, r0)
    r1c = min(canvas.shape[0], r1)
    c0c = max(0, c0)
    c1c = min(canvas.shape[1], c1)
    if r0c < r1c and c0c < c1c:
        canvas[r0c:r1c, c0c:c1c] = value


def draw_hakenkreuz(canvas, top, left, size, thick, value, mirror):
    bottom = top + size
    right = left + size
    cx = (left + right) // 2
    cy = (top + bottom) // 2
    half = thick // 2
    fill(canvas, top, bottom, cx - half, cx + half + thick % 2, value)
    fill(canvas, cy - half, cy + half + thick % 2, left, right, value)
    if not mirror:
        fill(canvas, top, top + thick, cx, right, value)
        fill(canvas, cy, bottom, right - thick, right, value)
        fill(canvas, bottom - thick, bottom, left, cx, value)
        fill(canvas, top, cy, left, left + thick, value)
    else:
        fill(canvas, top, top + thick, left, cx, value)
        fill(canvas, cy, bottom, left, left + thick, value)
        fill(canvas, bottom - thick, bottom, cx, right, value)
        fill(canvas, top, cy, right - thick, right, value)


def draw_plus(canvas, top, left, size, thick, value):
    bottom = top + size
    right = left + size
    cx = (left + right) // 2
    cy = (top + bottom) // 2
    half = thick // 2
    fill(canvas, top, bottom, cx - half, cx + half + thick % 2, value)
    fill(canvas, cy - half, cy + half + thick % 2, left, right, value)


def draw_ring(canvas, center, radius, thick, value):
    yy, xx = np.ogrid[: canvas.shape[0], : canvas.shape[1]]
    dist = np.sqrt((yy - center[0]) ** 2 + (xx - center[1]) ** 2)
    canvas[np.abs(dist - radius) <= thick / 2] = value


def draw_bitmap(canvas, bitmap, top, left, scale, value):
    for r, row in enumerate(bitmap):
        for c, bit in enumerate(row):
            if bit == "1":
                fill(canvas, top + r * scale, top + (r + 1) * scale, left + c * scale, left + (c + 1) * scale, value)


RELIEF_STRENGTH = 0.5


def apply_relief(canvas, heights):
    h = heights.astype(np.float32)
    span = float(h.max() - h.min())
    if span < 1e-6:
        return canvas
    relief = (h - float(h.min())) / span
    shaded = canvas.astype(np.float32) * ((1.0 - RELIEF_STRENGTH / 2.0) + RELIEF_STRENGTH * relief)
    return np.clip(shaded, 0, 255).astype(np.uint8)


def make_background(rng, backgrounds):
    ground, sky = backgrounds
    pool = None
    if ground and sky:
        pool = ground if rng.random() < 0.5 else sky
    elif ground:
        pool = ground
    elif sky:
        pool = sky
    if pool:
        patch, heights = pool[rng.integers(len(pool))]
        canvas = patch.copy()
        if heights is not None:
            canvas = apply_relief(canvas, heights)
        return canvas
    base = int(rng.integers(70, 190))
    canvas = np.full((GRID, GRID), base, dtype=np.int32)
    cell = int(rng.choice([4, 8]))
    for r in range(0, GRID, cell):
        for c in range(0, GRID, cell):
            canvas[r : r + cell, c : c + cell] += int(rng.integers(-14, 15))
    for _ in range(int(rng.integers(1, 4))):
        h = int(rng.integers(6, 20))
        w = int(rng.integers(6, 20))
        r = int(rng.integers(0, GRID - h))
        c = int(rng.integers(0, GRID - w))
        canvas[r : r + h, c : c + w] += int(rng.integers(-25, 26))
    canvas += rng.normal(0, 10, (GRID, GRID)).astype(np.int32)
    return np.clip(canvas, 0, 255).astype(np.uint8)


def foreground_value(rng, background, low_contrast=False):
    mean = float(background.mean())
    if low_contrast:
        offset = float(rng.uniform(LOW_CONTRAST_MIN, LOW_CONTRAST_MAX))
        sign = 1.0 if rng.random() < 0.5 else -1.0
        return int(np.clip(mean + sign * offset, 0, 255))
    if mean > 200:
        return BLACK
    if mean < 55:
        return WHITE
    return WATCH[int(rng.integers(len(WATCH)))]


def make_standard_positive(rng, backgrounds, low_contrast=False):
    canvas = make_background(rng, backgrounds)
    value = foreground_value(rng, canvas, low_contrast)
    size = int(rng.integers(28, 52))
    thick = int(rng.integers(4, 9))
    top = int(rng.integers(-4, GRID - size + 4))
    left = int(rng.integers(-4, GRID - size + 4))
    mirror = bool(rng.integers(2))
    draw_hakenkreuz(canvas, top, left, size, thick, value, mirror)
    return canvas, ("lowcontrast" if low_contrast else "standard")


def make_pattern_wall(rng, backgrounds):
    canvas = make_background(rng, backgrounds)
    value = foreground_value(rng, canvas)
    count = int(rng.integers(2, 10))
    for _ in range(count):
        size = int(rng.integers(10, 22))
        thick = int(rng.integers(2, 5))
        top = int(rng.integers(0, GRID - size))
        left = int(rng.integers(0, GRID - size))
        mirror = bool(rng.integers(2))
        draw_hakenkreuz(canvas, top, left, size, thick, value, mirror)
    return canvas, "wall"


def make_clipped_positive(rng, backgrounds):
    canvas = make_background(rng, backgrounds)
    value = foreground_value(rng, canvas)
    size = int(rng.integers(30, 54))
    thick = int(rng.integers(4, 9))
    edge = int(rng.integers(4))
    if edge == 0:
        top, left = int(rng.integers(-size + 10, -4)), int(rng.integers(0, GRID - size))
    elif edge == 1:
        top, left = int(rng.integers(GRID - 10, GRID - 4)), int(rng.integers(0, GRID - size))
    elif edge == 2:
        top, left = int(rng.integers(0, GRID - size)), int(rng.integers(-size + 10, -4))
    else:
        top, left = int(rng.integers(0, GRID - size)), int(rng.integers(GRID - 10, GRID - 4))
    mirror = bool(rng.integers(2))
    draw_hakenkreuz(canvas, top, left, size, thick, value, mirror)
    return canvas, "clipped"


def make_dense_positive(rng, backgrounds):
    canvas, _ = make_standard_positive(rng, backgrounds)
    value = foreground_value(rng, canvas)
    for _ in range(int(rng.integers(3, 8))):
        h = int(rng.integers(4, 14))
        w = int(rng.integers(4, 14))
        r = int(rng.integers(0, GRID - h))
        c = int(rng.integers(0, GRID - w))
        canvas[r : r + h, c : c + w] = value
    return canvas, "dense"


def make_sloppy_positive(rng, backgrounds):
    canvas = make_background(rng, backgrounds)
    value = foreground_value(rng, canvas)
    size = int(rng.integers(26, 44))
    thick = int(rng.integers(4, 8))
    top = int(rng.integers(-4, GRID - size + 4))
    left = int(rng.integers(-4, GRID - size + 4))
    mirror = bool(rng.integers(2))
    draw_hakenkreuz(canvas, top, left, size, thick, value, mirror)
    for _ in range(int(rng.integers(SLOP_DECOYS_MIN, SLOP_DECOYS_MAX + 1))):
        style = int(rng.integers(3))
        if style == 0:
            arm = int(rng.integers(10, 24))
            bit = int(rng.integers(3, 7))
            r0 = int(rng.integers(0, GRID - arm))
            c0 = int(rng.integers(0, GRID - arm))
            fill(canvas, r0, r0 + bit, c0, c0 + arm, value)
            fill(canvas, r0, r0 + arm, c0, c0 + bit, value)
        elif style == 1:
            length = int(rng.integers(12, 26))
            bit = int(rng.integers(3, 7))
            r0 = int(rng.integers(0, GRID - bit))
            c0 = int(rng.integers(0, GRID - length))
            fill(canvas, r0, r0 + bit, c0, c0 + length, value)
        else:
            length = int(rng.integers(12, 26))
            bit = int(rng.integers(3, 7))
            r0 = int(rng.integers(0, GRID - length))
            c0 = int(rng.integers(0, GRID - length))
            for i in range(length):
                fill(canvas, r0 + i, r0 + i + bit, c0 + i, c0 + i + bit, value)
    return canvas, "sloppy"


def make_positive(rng, backgrounds):
    if rng.random() < LOW_CONTRAST_SHARE:
        return make_standard_positive(rng, backgrounds, low_contrast=True)
    if rng.random() < SLOP_SHARE:
        return make_sloppy_positive(rng, backgrounds)
    if rng.random() < ADVERSARIAL_FRACTION:
        total = WALL_SHARE + CLIPPED_SHARE + DENSE_SHARE
        pick = rng.random() * total
        if pick < WALL_SHARE:
            return make_pattern_wall(rng, backgrounds)
        if pick < WALL_SHARE + CLIPPED_SHARE:
            return make_clipped_positive(rng, backgrounds)
        return make_dense_positive(rng, backgrounds)
    return make_standard_positive(rng, backgrounds)


def make_ok(rng, backgrounds, mined):
    canvas = make_background(rng, backgrounds)
    value = foreground_value(rng, canvas)
    kind = int(rng.integers(9))
    if rng.random() < HARD_OK_SHARE:
        kind = HARD_OK_KINDS[int(rng.integers(len(HARD_OK_KINDS)))]
    if mined and rng.random() < MINED_OK_SHARE:
        return mined[int(rng.integers(len(mined)))].copy(), "ok-mined"
    if kind == 0:
        noise = rng.integers(0, 256, (GRID, GRID)).astype(np.uint8)
        mask = rng.random((GRID, GRID)) < 0.5
        canvas[mask] = noise[mask]
    elif kind == 1:
        size = int(rng.integers(20, 48))
        thick = int(rng.integers(3, 8))
        draw_plus(canvas, int(rng.integers(0, GRID - size)), int(rng.integers(0, GRID - size)), size, thick, value)
    elif kind == 2:
        draw_ring(canvas, (int(rng.integers(16, 48)), int(rng.integers(16, 48))), int(rng.integers(10, 22)), int(rng.integers(2, 6)), value)
    elif kind == 3:
        step = int(rng.integers(6, 12))
        canvas[::step, :] = value
    elif kind == 4:
        step = int(rng.integers(6, 12))
        for r in range(0, GRID, step):
            for c in range(0, GRID, step):
                if (r // step + c // step) % 2 == 0:
                    canvas[r : r + step, c : c + step] = value
    elif kind == 5:
        letter = LETTERS[sorted(LETTERS)[int(rng.integers(len(LETTERS)))]]
        scale = int(rng.integers(4, 8))
        h = len(letter) * scale
        w = len(letter[0]) * scale
        draw_bitmap(canvas, letter, int(rng.integers(0, GRID - h)), int(rng.integers(0, GRID - w)), scale, value)
    elif kind == 6:
        sprite = rng.random((8, 8)) < 0.45
        sprite = np.logical_or(sprite, np.fliplr(sprite))
        scale = int(rng.integers(3, 6))
        h = 8 * scale
        w = 8 * scale
        top = int(rng.integers(0, GRID - h))
        left = int(rng.integers(0, GRID - w))
        big = np.kron(sprite, np.ones((scale, scale), dtype=bool))
        canvas[top : top + h, left : left + w][big] = value
    elif kind == 7:
        size = int(rng.integers(20, 44))
        thick = int(rng.integers(3, 7))
        top = int(rng.integers(0, GRID - size))
        left = int(rng.integers(0, GRID - size))
        fill(canvas, top, top + thick, left, left + size, value)
        fill(canvas, top, top + size, left, left + thick, value)
    else:
        yy, xx = np.ogrid[:GRID, :GRID]
        canvas[yy > xx + int(rng.integers(-GRID, GRID))] = value
    return canvas, ("ok-" + OK_KINDS[kind])


def rescale(canvas, factor):
    n = canvas.shape[0]
    if abs(factor - 1.0) < 0.01:
        return canvas
    if factor > 1.0:
        idx = np.clip((np.arange(n) / factor + (n - n / factor) / 2).astype(int), 0, n - 1)
        return canvas[idx][:, idx]
    small_n = max(8, int(n * factor))
    idx = (np.arange(small_n) * n / small_n).astype(int)
    small = canvas[idx][:, idx]
    out = np.full_like(canvas, int(np.median(canvas)))
    top = (n - small_n) // 2
    out[top : top + small_n, top : top + small_n] = small
    return out


def translate(canvas, pixels, rng):
    shift_r = int(rng.integers(-pixels, pixels + 1))
    shift_c = int(rng.integers(-pixels, pixels + 1))
    moved = np.roll(canvas, (shift_r, shift_c), axis=(0, 1))
    edge = int(np.median(canvas))
    if shift_r > 0:
        moved[:shift_r, :] = edge
    elif shift_r < 0:
        moved[shift_r:, :] = edge
    if shift_c > 0:
        moved[:, :shift_c] = edge
    elif shift_c < 0:
        moved[:, shift_c:] = edge
    return moved


def augment(canvas, rng):
    out = np.rot90(canvas, int(rng.integers(4))).copy()
    if rng.random() < 0.5:
        out = np.fliplr(out)
    if rng.random() < 0.5:
        out = np.flipud(out)
    if TRANSLATE_PIXELS > 0:
        out = translate(out, TRANSLATE_PIXELS, rng)
    out = rescale(out, float(rng.uniform(ZOOM_MIN, ZOOM_MAX)))
    if rng.random() < 0.4:
        spread = float(rng.uniform(0.8, 1.2))
        shift = float(rng.uniform(-30.0, 30.0))
        out = np.clip((out.astype(np.float32) - 128.0) * spread + 128.0 + shift, 0, 255).astype(np.uint8)
    if rng.random() < INVERSION_PROBABILITY:
        out = 255 - out
    if rng.random() < 0.5:
        flip = rng.random(out.shape) < float(rng.uniform(0.02, 0.08))
        out[flip] = rng.integers(0, 256, flip.sum()).astype(np.uint8)
    if rng.random() < 0.25:
        h = int(rng.integers(3, 10))
        w = int(rng.integers(3, 10))
        fill(out, int(rng.integers(0, GRID - h)), int(rng.integers(0, GRID - h)) + h, int(rng.integers(0, GRID - w)), int(rng.integers(0, GRID - w)) + w, int(rng.integers(0, 256)))
    if rng.random() < CUTOUT_PROBABILITY:
        h = int(rng.integers(CUTOUT_MIN, CUTOUT_MAX + 1))
        w = int(rng.integers(CUTOUT_MIN, CUTOUT_MAX + 1))
        fill(out, int(rng.integers(0, GRID - h)), int(rng.integers(0, GRID - h)) + h, int(rng.integers(0, GRID - w)), int(rng.integers(0, GRID - w)) + w, int(np.median(out)))
    return out


def load_pool(root, dirname):
    pool_dir = root / dirname
    if not pool_dir.is_dir():
        return []
    patches = []
    for path in sorted(pool_dir.glob("*.npy")):
        if path.stem.endswith("-h"):
            continue
        patch = np.load(path)
        if patch.shape != (GRID, GRID):
            continue
        heights = None
        heights_path = path.with_name(path.stem + "-h.npy")
        if heights_path.is_file():
            loaded = np.load(heights_path)
            if loaded.shape == (GRID, GRID):
                heights = loaded.astype(np.int32)
        patches.append((patch.astype(np.uint8), heights))
    return patches


def load_backgrounds(root):
    return load_pool(root, GROUND_DIRNAME), load_pool(root, SKY_DIRNAME)


def load_mined(root):
    mined_dir = root / MINED_DIRNAME
    if not mined_dir.is_dir():
        return []
    patches = []
    for path in sorted(mined_dir.glob("*.npy")):
        patch = np.load(path)
        if patch.shape != (GRID, GRID):
            continue
        patches.append(patch.astype(np.uint8))
    return patches


def stratified_split(rng, images, labels, subtypes):
    ratios = SPLIT_RATIOS
    splits = ([], [], [])
    for label in (LABEL_OK, LABEL_HAKENKREUZ):
        idx = np.where(labels == label)[0]
        rng.shuffle(idx)
        n = len(idx)
        n_train = int(n * ratios[0])
        n_val = int(n * ratios[1])
        splits[0].append(idx[:n_train])
        splits[1].append(idx[n_train : n_train + n_val])
        splits[2].append(idx[n_train + n_val :])
    result = []
    for part in splits:
        idx = np.concatenate(part)
        rng.shuffle(idx)
        result.append((images[idx], labels[idx], subtypes[idx]))
    return result


def build_dataset(out_dir, per_class):
    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    rng = np.random.default_rng(SEED)
    backgrounds = load_backgrounds(out_dir)
    mined = load_mined(out_dir)
    images = []
    labels = []
    subtypes = []
    for label in (LABEL_OK, LABEL_HAKENKREUZ):
        for _ in range(per_class):
            if label == LABEL_OK:
                grid, subtype = make_ok(rng, backgrounds, mined)
            else:
                grid, subtype = make_positive(rng, backgrounds)
            images.append(augment(grid, rng))
            labels.append(label)
            subtypes.append(subtype)
    images = np.stack(images)
    labels = np.array(labels, dtype=np.int64)
    subtypes = np.array(subtypes)
    (train_x, train_y, train_s), (val_x, val_y, val_s), (test_x, test_y, test_s) = stratified_split(rng, images, labels, subtypes)
    np.savez_compressed(out_dir / "train.npz", images=train_x, labels=train_y, subtypes=train_s)
    np.savez_compressed(out_dir / "val.npz", images=val_x, labels=val_y, subtypes=val_s)
    np.savez_compressed(out_dir / "test.npz", images=test_x, labels=test_y, subtypes=test_s)
    manifest = {
        "seed": SEED,
        "per_class": per_class,
        "split_ratios": list(SPLIT_RATIOS),
        "adversarial_fraction": ADVERSARIAL_FRACTION,
        "wall_share": WALL_SHARE,
        "clipped_share": CLIPPED_SHARE,
        "translate_pixels": TRANSLATE_PIXELS,
        "cutout_probability": CUTOUT_PROBABILITY,
        "zoom_range": [ZOOM_MIN, ZOOM_MAX],
        "inversion_probability": INVERSION_PROBABILITY,
        "low_contrast_share": LOW_CONTRAST_SHARE,
        "low_contrast_range": [LOW_CONTRAST_MIN, LOW_CONTRAST_MAX],
        "hard_ok_share": HARD_OK_SHARE,
        "hard_ok_kinds": [OK_KINDS[i] for i in HARD_OK_KINDS],
        "mined_ok_share": MINED_OK_SHARE,
        "mined_negatives": len(mined),
        "slop_share": SLOP_SHARE,
        "grid": GRID,
        "labels": ["ok", "hakenkreuz"],
        "palette": {"black": BLACK, "white": WHITE, "red": RED},
        "synthetic_backgrounds": len(backgrounds[0]) == 0 and len(backgrounds[1]) == 0,
        "background_patches": {"ground": len(backgrounds[0]), "sky": len(backgrounds[1])},
        "counts": {
            "train": [int((train_y == i).sum()) for i in range(2)],
            "val": [int((val_y == i).sum()) for i in range(2)],
            "test": [int((test_y == i).sum()) for i in range(2)],
        },
        "subtype_counts": {
            "train": {str(k): int(v) for k, v in zip(*np.unique(train_s, return_counts=True))},
            "val": {str(k): int(v) for k, v in zip(*np.unique(val_s, return_counts=True))},
            "test": {str(k): int(v) for k, v in zip(*np.unique(test_s, return_counts=True))},
        },
    }
    (out_dir / "manifest.json").write_text(json.dumps(manifest, indent=2))
    return manifest


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("target", nargs="?", default="data")
    parser.add_argument("count", nargs="?", type=int, default=PER_CLASS)
    parser.add_argument("--adversarial-fraction", type=float, default=ADVERSARIAL_FRACTION)
    parser.add_argument("--wall-share", type=float, default=WALL_SHARE)
    parser.add_argument("--clipped-share", type=float, default=CLIPPED_SHARE)
    parser.add_argument("--translate", type=int, default=TRANSLATE_PIXELS)
    parser.add_argument("--cutout", type=float, default=CUTOUT_PROBABILITY)
    parser.add_argument("--zoom-min", type=float, default=ZOOM_MIN)
    parser.add_argument("--zoom-max", type=float, default=ZOOM_MAX)
    parser.add_argument("--inversion", type=float, default=INVERSION_PROBABILITY)
    parser.add_argument("--low-contrast", type=float, default=LOW_CONTRAST_SHARE)
    parser.add_argument("--hard-ok-share", type=float, default=HARD_OK_SHARE)
    parser.add_argument("--mined-ok-share", type=float, default=MINED_OK_SHARE)
    parser.add_argument("--slop-share", type=float, default=SLOP_SHARE)
    args = parser.parse_args()
    ADVERSARIAL_FRACTION = args.adversarial_fraction
    WALL_SHARE = args.wall_share
    CLIPPED_SHARE = args.clipped_share
    TRANSLATE_PIXELS = args.translate
    CUTOUT_PROBABILITY = args.cutout
    ZOOM_MIN = args.zoom_min
    ZOOM_MAX = args.zoom_max
    INVERSION_PROBABILITY = args.inversion
    LOW_CONTRAST_SHARE = args.low_contrast
    HARD_OK_SHARE = args.hard_ok_share
    MINED_OK_SHARE = args.mined_ok_share
    SLOP_SHARE = args.slop_share
    print(json.dumps(build_dataset(args.target, args.count)["counts"]))
