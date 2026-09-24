import numpy as np


def value_noise(rng, rows, cols, cell):
    cell = max(2, int(cell))
    gr = rows // cell + 2
    gc = cols // cell + 2
    grid = rng.random((gr, gc))
    ys = np.arange(rows) / cell
    xs = np.arange(cols) / cell
    y0 = ys.astype(np.int64)
    x0 = xs.astype(np.int64)
    fy = (ys - y0)
    fx = (xs - x0)
    fy = (fy * fy * (3 - 2 * fy))[:, None]
    fx = (fx * fx * (3 - 2 * fx))[None, :]
    g00 = grid[y0][:, x0]
    g10 = grid[y0 + 1][:, x0]
    g01 = grid[y0][:, x0 + 1]
    g11 = grid[y0 + 1][:, x0 + 1]
    top = g00 * (1 - fx) + g01 * fx
    bot = g10 * (1 - fx) + g11 * fx
    return top * (1 - fy) + bot * fy


def multi_octave(rng, rows, cols, octaves=3, base_cell=24, persistence=0.5, lacunarity=2.0):
    total = np.zeros((rows, cols), dtype=np.float64)
    amp = 1.0
    amp_sum = 0.0
    cell = float(base_cell)
    for _ in range(octaves):
        total += amp * value_noise(rng, rows, cols, cell)
        amp_sum += amp
        amp *= persistence
        cell = max(2.0, cell / lacunarity)
    return total / amp_sum
