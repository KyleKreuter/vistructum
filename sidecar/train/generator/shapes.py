import numpy as np

from .symbol import build_irregular_mask, sample_size_thick


def _canvas(size):
    return np.zeros((size, size), dtype=bool)


def _fill(canvas, r0, r1, c0, c1):
    r0c, r1c = max(0, r0), min(canvas.shape[0], r1)
    c0c, c1c = max(0, c0), min(canvas.shape[1], c1)
    if r0c < r1c and c0c < c1c:
        canvas[r0c:r1c, c0c:c1c] = True


def greek_cross(rng, max_size=None):
    size, thick = sample_size_thick(rng, max_size) if max_size else sample_size_thick(rng)
    c = size // 2
    half = thick // 2
    canvas = _canvas(size)
    _fill(canvas, 0, size, c - half, c + half + thick % 2)
    _fill(canvas, c - half, c + half + thick % 2, 0, size)
    return canvas


def latin_cross(rng, max_size=None):
    size, thick = sample_size_thick(rng, max_size) if max_size else sample_size_thick(rng)
    canvas = _canvas(size)
    c = size // 2
    half = thick // 2
    _fill(canvas, 0, size, c - half, c + half + thick % 2)
    bar_row = max(thick, size // 4)
    _fill(canvas, bar_row - half, bar_row + half + thick % 2, 0, size)
    return canvas


def iron_cross(rng, max_size=None):
    size, thick = sample_size_thick(rng, max_size) if max_size else sample_size_thick(rng)
    canvas = greek_cross_fixed(size, thick)
    c = size // 2
    flare = max(1, thick)
    _fill(canvas, 0, flare, c - flare - flare, c - flare)
    _fill(canvas, 0, flare, c + flare, c + flare + flare)
    _fill(canvas, size - flare, size, c - flare - flare, c - flare)
    _fill(canvas, size - flare, size, c + flare, c + flare + flare)
    _fill(canvas, c - flare - flare, c - flare, 0, flare)
    _fill(canvas, c + flare, c + flare + flare, 0, flare)
    _fill(canvas, c - flare - flare, c - flare, size - flare, size)
    _fill(canvas, c + flare, c + flare + flare, size - flare, size)
    return canvas


def greek_cross_fixed(size, thick):
    c = size // 2
    half = thick // 2
    canvas = _canvas(size)
    _fill(canvas, 0, size, c - half, c + half + thick % 2)
    _fill(canvas, c - half, c + half + thick % 2, 0, size)
    return canvas


def celtic_cross(rng, max_size=None):
    size, thick = sample_size_thick(rng, max_size) if max_size else sample_size_thick(rng)
    canvas = greek_cross_fixed(size, thick)
    center = size / 2.0
    radius = size * 0.36
    yy, xx = np.mgrid[0:size, 0:size]
    dist = np.sqrt((yy - center) ** 2 + (xx - center) ** 2)
    canvas |= np.abs(dist - radius) <= max(1, thick) / 2
    return canvas


def l_shapes_d4(rng, max_size=None):
    size, thick = sample_size_thick(rng, max_size) if max_size else sample_size_thick(rng)
    arm = max(2, size // 3)
    canvas = _canvas(size)
    q = size // 2
    _fill(canvas, 0, thick, 0, arm)
    _fill(canvas, 0, arm, 0, thick)
    canvas[:, size - q:] |= np.fliplr(canvas[:, :q])
    canvas[size - q:, :] |= np.flipud(canvas[:q, :])
    return canvas


def t_shapes_d4(rng, max_size=None):
    size, thick = sample_size_thick(rng, max_size) if max_size else sample_size_thick(rng)
    arm = max(3, size // 3)
    q = size // 2
    quadrant = _canvas(q)
    _fill(quadrant, 0, thick, 0, arm)
    _fill(quadrant, 0, arm, max(0, arm - thick), arm)
    canvas = _canvas(size)
    canvas[:q, :q] |= quadrant
    canvas[:q, size - q:] |= np.fliplr(quadrant)
    canvas[size - q:, :q] |= np.flipud(quadrant)
    canvas[size - q:, size - q:] |= np.flipud(np.fliplr(quadrant))
    return canvas


def plus_alternating_hooks(rng, max_size=None):
    size, thick = sample_size_thick(rng, max_size) if max_size else sample_size_thick(rng)
    if size % 2 == 0:
        size -= 1
    if thick % 2 == 0:
        thick = max(1, thick - 1)
    canvas = _canvas(size)
    c = size // 2
    half = thick // 2
    _fill(canvas, 0, size, c - half, c + half + 1)
    _fill(canvas, c - half, c + half + 1, 0, size)
    hook = max(1, size // 2 - thick)
    _fill(canvas, 0, thick, c, c + hook)
    _fill(canvas, size - thick, size, c - hook, c)
    _fill(canvas, c, c + hook, size - thick, size)
    _fill(canvas, c - hook, c, 0, thick)
    return canvas


def plus_partial_hooks(rng, max_size=None):
    size, thick = sample_size_thick(rng, max_size) if max_size else sample_size_thick(rng)
    canvas = _canvas(size)
    c = size // 2
    half = thick // 2
    _fill(canvas, 0, size, c - half, c + half + thick % 2)
    _fill(canvas, c - half, c + half + thick % 2, 0, size)
    hook = max(1, size // 2 - thick)
    n_hooks = int(rng.integers(1, 4))
    hooks = [
        lambda: _fill(canvas, 0, thick, c, c + hook),
        lambda: _fill(canvas, c, c + hook, size - thick, size),
        lambda: _fill(canvas, size - thick, size, c - hook, c),
        lambda: _fill(canvas, c - hook, c, 0, thick),
    ]
    for i in rng.choice(4, size=n_hooks, replace=False):
        hooks[int(i)]()
    return canvas


def windmill3(rng, max_size=None):
    size, thick = sample_size_thick(rng, max_size) if max_size else sample_size_thick(rng)
    canvas = _canvas(size)
    c = size / 2.0
    arm_len = size * 0.46
    yy, xx = np.mgrid[0:size, 0:size]
    dy = yy - c
    dx = xx - c
    ang = np.arctan2(dy, dx)
    dist = np.sqrt(dy ** 2 + dx ** 2)
    for k in range(3):
        blade_ang = ang - (2 * np.pi / 3) * k
        blade_ang = (blade_ang + np.pi) % (2 * np.pi) - np.pi
        curved = blade_ang - dist / arm_len * 0.6
        canvas |= (dist <= arm_len) & (np.abs(curved) <= (thick / np.maximum(dist, 1e-6)).clip(0, 1))
    return canvas


def spiral(rng, max_size=None):
    size, thick = sample_size_thick(rng, max_size) if max_size else sample_size_thick(rng)
    canvas = _canvas(size)
    c = size / 2.0
    turns = rng.uniform(1.5, 2.5)
    yy, xx = np.mgrid[0:size, 0:size]
    dy = yy - c
    dx = xx - c
    ang = np.arctan2(dy, dx)
    dist = np.sqrt(dy ** 2 + dx ** 2)
    r_expected = (ang % (2 * np.pi)) / (2 * np.pi) * (size * 0.46 / turns)
    for t in range(int(turns) + 1):
        canvas |= np.abs(dist - (r_expected + t * size * 0.46 / turns)) <= max(1, thick) / 2
    canvas &= dist <= size * 0.48
    return canvas


def square_spiral(rng, max_size=None):
    size, thick = sample_size_thick(rng, max_size) if max_size else sample_size_thick(rng)
    thick = max(1, thick // 2)
    canvas = _canvas(size)
    top, bottom, left, right = 0, size, 0, size
    direction = 0
    steps = 0
    max_steps = max(2, int(size * 0.35) // max(1, thick))
    while top < bottom - thick and left < right - thick and steps < max_steps:
        if direction == 0:
            _fill(canvas, top, top + thick, left, right)
            top += thick * 2
        elif direction == 1:
            _fill(canvas, top, bottom, right - thick, right)
            right -= thick * 2
        elif direction == 2:
            _fill(canvas, bottom - thick, bottom, left, right)
            bottom -= thick * 2
        else:
            _fill(canvas, top, bottom, left, left + thick)
            left += thick * 2
        direction = (direction + 1) % 4
        steps += 1
    return canvas


def tetris(rng, max_size=None):
    size, thick = sample_size_thick(rng, max_size) if max_size else sample_size_thick(rng)
    cell = max(1, thick)
    shapes = [
        [(0, 0), (0, 1), (1, 0), (1, 1)],
        [(0, 0), (1, 0), (2, 0), (2, 1)],
        [(0, 1), (1, 1), (2, 1), (2, 0)],
        [(0, 0), (0, 1), (0, 2), (1, 1)],
        [(0, 0), (1, 0), (1, 1), (2, 1)],
    ]
    shape = shapes[int(rng.integers(0, len(shapes)))]
    reps = max(3, size // cell)
    canvas = _canvas(size)
    for _ in range(reps):
        ro = int(rng.integers(0, max(1, size - 3 * cell)))
        co = int(rng.integers(0, max(1, size - 3 * cell)))
        for r, c in shape:
            _fill(canvas, ro + r * cell, ro + (r + 1) * cell, co + c * cell, co + (c + 1) * cell)
    return canvas


def hash_grid(rng, max_size=None):
    size, thick = sample_size_thick(rng, max_size) if max_size else sample_size_thick(rng)
    thick = max(1, thick // 2)
    canvas = _canvas(size)
    n = int(rng.integers(2, 4))
    for k in range(1, n + 1):
        pos = size * k // (n + 1)
        _fill(canvas, pos - thick // 2, pos - thick // 2 + thick, 0, size)
        _fill(canvas, 0, size, pos - thick // 2, pos - thick // 2 + thick)
    return canvas


def fylfot_mirror(rng, max_size=None):
    size, thick = sample_size_thick(rng, max_size) if max_size else sample_size_thick(rng)
    if size % 2 == 0:
        size -= 1
    if thick % 2 == 0:
        thick = max(1, thick - 1)
    canvas = _canvas(size)
    c = size // 2
    half = thick // 2
    _fill(canvas, 0, size, c - half, c + half + 1)
    _fill(canvas, c - half, c + half + 1, 0, size)
    hook = max(1, size // 2 - thick)
    _fill(canvas, 0, thick, c - hook, c)
    _fill(canvas, 0, thick, c, c + hook)
    _fill(canvas, size - thick, size, c - hook, c)
    _fill(canvas, size - thick, size, c, c + hook)
    return canvas


def irregular_partial_hooks(rng, max_size=None):
    hooked = set(int(i) for i in rng.choice(4, size=int(rng.integers(0, 3)), replace=False))
    side = 1 if rng.random() < 0.5 else -1
    return build_irregular_mask(rng, tuple(side if arm in hooked else 0 for arm in range(4)))


def irregular_alternating_hooks(rng, max_size=None):
    side = 1 if rng.random() < 0.5 else -1
    flipped = int(rng.integers(0, 2))
    return build_irregular_mask(rng, tuple(-side if arm % 2 == flipped else side for arm in range(4)))


HARD_NEGATIVE_FAMILIES = {
    "greek-cross": greek_cross,
    "latin-cross": latin_cross,
    "iron-cross": iron_cross,
    "celtic-cross": celtic_cross,
    "l-shapes-d4": l_shapes_d4,
    "t-shapes-d4": t_shapes_d4,
    "plus-alt-hooks": plus_alternating_hooks,
    "plus-partial-hooks": plus_partial_hooks,
    "irregular-partial-hooks": irregular_partial_hooks,
    "irregular-alt-hooks": irregular_alternating_hooks,
    "windmill-3": windmill3,
    "spiral": spiral,
    "square-spiral": square_spiral,
    "tetris": tetris,
    "hash-grid": hash_grid,
    "fylfot-mirror": fylfot_mirror,
}
