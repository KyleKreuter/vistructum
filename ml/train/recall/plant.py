import json
import re
import sys
import time
from pathlib import Path

import numpy as np

from .raster import rectangles, sample_symbol, symbol_raster

AXES = ("Y", "X", "Z")
MATERIALS = ("obsidian", "crying_obsidian", "stone", "cobblestone", "stone_bricks", "oak_planks", "spruce_planks",
             "white_wool", "red_wool", "black_wool", "sandstone", "netherrack", "glass", "gold_block", "iron_block",
             "quartz_block", "bricks", "black_concrete", "white_concrete", "red_concrete")
PROBE_TAG = "vistructum_recall_probe"
MAX_ATTEMPTS_PER_STRUCTURE = 2000
LOAD_POLLS = 100
LOAD_POLL_SECONDS = 0.05
BUILD_TOP = 319
DRY_RUN_SURFACE = 64
HEIGHT_PATTERN = re.compile(r"(-?\d+(?:\.\d+)?)d\s*$")


def footprint(axis, rows, cols):
    if axis == "Y":
        return cols, 1, rows
    if axis == "X":
        return 1, rows, cols
    if axis == "Z":
        return cols, rows, 1
    raise ValueError(f"unknown axis {axis}")


def horizontal_gap(a, b):
    gap_x = max(a["min"][0] - b["max"][0], b["min"][0] - a["max"][0]) - 1
    gap_z = max(a["min"][2] - b["max"][2], b["min"][2] - a["max"][2]) - 1
    return max(gap_x, gap_z)


def plan(rng, count, world, area, materials, axes, min_size, max_extent, spacing):
    min_x, min_z, max_x, max_z = area
    structures = []
    for index in range(count):
        symbol = sample_symbol(rng, min_size, max_extent)
        mask = symbol_raster(**symbol)
        axis = axes[int(rng.integers(0, len(axes)))]
        size_x, size_y, size_z = footprint(axis, *mask.shape)
        if size_x > max_x - min_x + 1 or size_z > max_z - min_z + 1:
            raise ValueError("area is smaller than a single structure")
        structure = {"id": index, "world": world, "material": materials[int(rng.integers(0, len(materials)))],
                     **symbol, "axis": axis, "extent": int(max(mask.shape)), "blocks": int(mask.sum())}
        for _ in range(MAX_ATTEMPTS_PER_STRUCTURE):
            x = int(rng.integers(min_x, max_x - size_x + 2))
            z = int(rng.integers(min_z, max_z - size_z + 2))
            candidate = {**structure, "min": [x, 0, z], "max": [x + size_x - 1, size_y - 1, z + size_z - 1]}
            if all(horizontal_gap(candidate, other) >= spacing for other in structures):
                structures.append(candidate)
                break
        else:
            raise ValueError(f"could only fit {len(structures)} of {count} structures with spacing {spacing}; "
                             f"enlarge the area or lower the count")
    return structures


def raster_of(structure):
    return symbol_raster(structure["size"], structure["thickness"], structure["handedness"], structure["rotation"])


def at_height(structure, base_y):
    height = structure["max"][1] - structure["min"][1]
    return {**structure, "min": [structure["min"][0], base_y, structure["min"][2]],
            "max": [structure["max"][0], base_y + height, structure["max"][2]]}


def to_world(structure, rows, row, col):
    x, y, z = structure["min"]
    if structure["axis"] == "Y":
        return x + col, y, z + row
    if structure["axis"] == "X":
        return x, y + rows - 1 - row, z + col
    return x + col, y + rows - 1 - row, z


def in_dimension(dimension, command):
    return f"execute in {dimension} run {command}"


def fill_commands(structure, dimension):
    mask = raster_of(structure)
    rows = mask.shape[0]
    commands = []
    for top, left, bottom, right in rectangles(mask):
        first = to_world(structure, rows, top, left)
        second = to_world(structure, rows, bottom, right)
        x0, y0, z0 = map(min, first, second)
        x1, y1, z1 = map(max, first, second)
        commands.append(in_dimension(dimension, f"fill {x0} {y0} {z0} {x1} {y1} {z1} minecraft:{structure['material']}"))
    return commands


def chunk_range(structure):
    return (structure["min"][0] >> 4, structure["min"][2] >> 4, structure["max"][0] >> 4, structure["max"][2] >> 4)


def forceload_command(structure, dimension, action):
    x0, z0, x1, z1 = structure["min"][0], structure["min"][2], structure["max"][0], structure["max"][2]
    return in_dimension(dimension, f"forceload {action} {x0} {z0} {x1} {z1}")


def loaded_command(structure, dimension):
    cx0, cz0, cx1, cz1 = chunk_range(structure)
    checks = " ".join(f"if loaded {cx * 16} 0 {cz * 16}" for cx in range(cx0, cx1 + 1) for cz in range(cz0, cz1 + 1))
    return f"execute in {dimension} {checks}"


def surface_samples(structure):
    xs = sorted({structure["min"][0], (structure["min"][0] + structure["max"][0]) // 2, structure["max"][0]})
    zs = sorted({structure["min"][2], (structure["min"][2] + structure["max"][2]) // 2, structure["max"][2]})
    return [(x, z) for x in xs for z in zs]


def probe_commands(dimension, heightmap, x, z):
    return ((f"execute in {dimension} positioned {x} 0 {z} positioned over {heightmap} "
             f"run summon minecraft:marker ~ ~ ~ {{Tags:[\"{PROBE_TAG}\"]}}"),
            f"data get entity @e[type=minecraft:marker,tag={PROBE_TAG},limit=1] Pos[1]",
            f"kill @e[type=minecraft:marker,tag={PROBE_TAG}]")


def parse_height(response):
    match = HEIGHT_PATTERN.search(response.strip())
    if match is None:
        raise RuntimeError(f"unexpected height probe response: {response}")
    return int(float(match.group(1)))


def base_height(heights, structure_height, lift):
    return min(max(heights) + lift, BUILD_TOP - structure_height + 1)


def air_height(rng, y_range, structure_height):
    low, high = y_range
    if high - structure_height + 1 < low:
        raise ValueError(f"y range {low}..{high} cannot hold a structure of height {structure_height}")
    return int(rng.integers(low, high - structure_height + 2))


def fill_succeeded(response):
    return response.startswith(("Successfully filled", "No blocks were filled"))


class Planter:
    def __init__(self, client, dimension, heightmap, lift, dry_run=False, log=sys.stderr):
        self.client = client
        self.dimension = dimension
        self.heightmap = heightmap
        self.lift = lift
        self.dry_run = dry_run
        self.log = log

    def send(self, command):
        if self.dry_run:
            print(command)
            return ""
        return self.client.command(command)

    def await_loaded(self, structure):
        command = loaded_command(structure, self.dimension)
        for _ in range(LOAD_POLLS):
            if self.dry_run or "passed" in self.send(command):
                return
            time.sleep(LOAD_POLL_SECONDS)
        raise RuntimeError(f"chunks of structure {structure['id']} did not load")

    def surface(self, structure):
        if self.dry_run:
            return DRY_RUN_SURFACE
        heights = []
        for x, z in surface_samples(structure):
            summon, read, kill = probe_commands(self.dimension, self.heightmap, x, z)
            self.send(summon)
            try:
                heights.append(parse_height(self.send(read)))
            finally:
                self.send(kill)
        return base_height(heights, structure["max"][1] - structure["min"][1] + 1, self.lift)

    def fill(self, command):
        for _ in range(LOAD_POLLS):
            response = self.send(command)
            if self.dry_run or fill_succeeded(response):
                return
            if "not loaded" not in response:
                raise RuntimeError(f"{command}: {response}")
            time.sleep(LOAD_POLL_SECONDS)
        raise RuntimeError(f"{command}: position stayed unloaded")

    def place(self, structure, base_y=None):
        self.send(forceload_command(structure, self.dimension, "add"))
        try:
            self.await_loaded(structure)
            placed = at_height(structure, self.surface(structure) if base_y is None else base_y)
            for command in fill_commands(placed, self.dimension):
                self.fill(command)
            return placed
        finally:
            self.send(forceload_command(structure, self.dimension, "remove"))


def write_truth(path, meta, structures):
    Path(path).write_text(json.dumps({**meta, "structures": structures}, indent=2))


def run(client, args):
    rng = np.random.default_rng(args.seed)
    structures = plan(rng, args.count, args.world, args.area, args.materials, args.axes, args.min_size,
                      args.max_extent, args.spacing)
    meta = {"world": args.world, "dimension": args.dimension, "seed": args.seed, "area": list(args.area),
            "placement": args.placement, "heightmap": args.heightmap if args.placement == "surface" else None,
            "lift": args.lift if args.placement == "surface" else None,
            "y_range": list(args.y_range) if args.placement == "air" else None, "spacing": args.spacing}
    planter = Planter(client, args.dimension, args.heightmap, args.lift, args.dry_run)
    placed = []
    started = time.time()
    try:
        for structure in structures:
            height = structure["max"][1] - structure["min"][1] + 1
            base_y = air_height(rng, args.y_range, height) if args.placement == "air" else None
            placed.append(planter.place(structure, base_y))
            if len(placed) % 25 == 0:
                print(f"placed {len(placed)}/{len(structures)} in {time.time() - started:.0f}s", file=sys.stderr)
        planter.send("save-all flush")
    finally:
        write_truth(args.out, meta, placed)
        print(f"wrote {len(placed)} structures to {args.out}", file=sys.stderr)
    return placed
