import sqlite3
import struct

import numpy as np
import pytest
from generator.symbol import is_c4_chiral
from recall.__main__ import parser
from recall.plant import (
    Planter,
    at_height,
    base_height,
    fill_commands,
    forceload_command,
    horizontal_gap,
    loaded_command,
    parse_height,
    plan,
    probe_commands,
    raster_of,
    surface_samples,
    to_world,
)
from recall.raster import HANDEDNESS, ROTATIONS, rectangles, sample_symbol, symbol_raster
from recall.rcon import TYPE_COMMAND, TYPE_LOGIN, RconError, decode_packet, encode_packet
from recall.score import cover, load_findings, markdown, match, report, size_bucket

DIM = "minecraft:overworld"


@pytest.mark.parametrize("size,thickness", [(7, 1), (15, 3), (31, 5), (61, 15)])
@pytest.mark.parametrize("handedness", HANDEDNESS)
@pytest.mark.parametrize("rotation", ROTATIONS)
def test_raster_is_a_chiral_swastika(size, thickness, handedness, rotation):
    mask = symbol_raster(size, thickness, handedness, rotation)
    assert mask.any(axis=0).all() and mask.any(axis=1).all()
    assert is_c4_chiral(mask)
    if rotation == 0:
        assert mask.shape == (size, size)
    else:
        assert size < max(mask.shape) <= int(np.ceil(size * np.sqrt(2))) + 1


@pytest.mark.parametrize("rotation", ROTATIONS)
def test_handedness_is_mirrored(rotation):
    right = symbol_raster(21, 3, "right", rotation)
    left = symbol_raster(21, 3, "left", rotation)
    assert np.array_equal(left, np.fliplr(right)) or np.array_equal(left, np.flipud(right))
    assert not np.array_equal(left, right)


def test_right_handed_top_arm_hooks_right():
    mask = symbol_raster(7, 1, "right", 0)
    assert mask[0, 3:].all() and not mask[0, 1:3].any()


def test_sparse_small_symbol_has_one_block_arms():
    mask = symbol_raster(7, 1, "right", 0)
    assert mask.sum() == 25
    assert mask[:, 3].all() and mask[3, :].all()


def test_rectangles_cover_mask_exactly_without_overlap():
    for rotation in ROTATIONS:
        mask = symbol_raster(41, 9, "left", rotation)
        painted = np.zeros(mask.shape, dtype=int)
        for top, left, bottom, right in rectangles(mask):
            painted[top:bottom + 1, left:right + 1] += 1
        assert np.array_equal(painted, mask.astype(int))


def test_rectangles_merge_solid_areas():
    assert rectangles(np.ones((5, 8), dtype=bool)) == [(0, 0, 4, 7)]
    assert len(rectangles(symbol_raster(61, 15, "right", 0))) <= 9


def test_sampled_symbols_stay_within_bounds():
    rng = np.random.default_rng(3)
    seen = set()
    for _ in range(400):
        symbol = sample_symbol(rng, 7, 61)
        mask = symbol_raster(**symbol)
        assert max(mask.shape) <= 61 and symbol["size"] >= 7
        assert symbol["size"] % 2 == 1 and symbol["thickness"] % 2 == 1
        seen.add((symbol["handedness"], symbol["rotation"], symbol["thickness"] > 1))
    assert len(seen) == 8


def structure(axis, size=7, rotation=0, handedness="right", origin=(100, 70, 200)):
    mask = symbol_raster(size, 1, handedness, rotation)
    rows, cols = mask.shape
    extent = {"Y": (cols, 1, rows), "X": (1, rows, cols), "Z": (cols, rows, 1)}[axis]
    return {"id": 0, "world": "world", "material": "obsidian", "size": size, "thickness": 1,
            "handedness": handedness, "rotation": rotation, "axis": axis, "extent": max(mask.shape),
            "blocks": int(mask.sum()), "min": list(origin),
            "max": [origin[i] + extent[i] - 1 for i in range(3)]}


def placed_blocks(commands):
    blocks = set()
    for command in commands:
        parts = command.split()
        assert parts[:4] == ["execute", "in", DIM, "run"] and parts[4] == "fill"
        x0, y0, z0, x1, y1, z1 = map(int, parts[5:11])
        assert x0 <= x1 and y0 <= y1 and z0 <= z1
        assert parts[11] == "minecraft:obsidian"
        blocks |= {(x, y, z) for x in range(x0, x1 + 1) for y in range(y0, y1 + 1) for z in range(z0, z1 + 1)}
    return blocks


def expected_blocks(item):
    mask = raster_of(item)
    return {to_world(item, mask.shape[0], r, c) for r, c in zip(*np.nonzero(mask))}


def test_flat_symbol_lies_in_one_layer_matching_the_raster():
    item = structure("Y")
    blocks = placed_blocks(fill_commands(item, DIM))
    assert blocks == expected_blocks(item)
    assert {y for _, y, _ in blocks} == {70}
    assert (103, 70, 200) in blocks and (101, 70, 200) not in blocks


@pytest.mark.parametrize("axis,fixed", [("X", 0), ("Z", 2)])
def test_standing_symbol_is_upright_in_its_plane(axis, fixed):
    item = structure(axis, size=9, rotation=45)
    blocks = placed_blocks(fill_commands(item, DIM))
    assert blocks == expected_blocks(item)
    assert {b[fixed] for b in blocks} == {item["min"][fixed]}
    ys = {y for _, y, _ in blocks}
    assert min(ys) == item["min"][1] and max(ys) == item["max"][1]


def test_standing_symbol_top_row_is_highest():
    item = structure("Z")
    blocks = placed_blocks(fill_commands(item, DIM))
    top = {x for x, y, _ in blocks if y == item["max"][1]}
    assert top == {100, 103, 104, 105, 106}


def test_forceload_and_loaded_checks_cover_all_chunks():
    item = structure("Y", size=41, origin=(-20, 64, 30))
    assert forceload_command(item, DIM, "add") == f"execute in {DIM} run forceload add -20 30 20 70"
    command = loaded_command(item, DIM)
    assert command.count("if loaded") == 4 * 4
    assert "if loaded -32 0 16" in command and "if loaded 16 0 64" in command


def test_surface_probe_and_height():
    item = structure("X", size=9)
    assert len(surface_samples(item)) == 3
    assert len(surface_samples(structure("Y", size=9))) == 9
    summon, read, kill = probe_commands(DIM, "motion_blocking", 5, -7)
    assert summon.startswith(f"execute in {DIM} positioned 5 0 -7 positioned over motion_blocking run summon")
    assert "tag=vistructum_recall_probe" in read and read.endswith("Pos[1]")
    assert kill.startswith("kill @e[type=minecraft:marker")
    assert parse_height("Marker has the following entity data: 71.0d") == 71
    assert parse_height("Marker has the following entity data: -12.0d") == -12
    with pytest.raises(RuntimeError):
        parse_height("No entity was found")
    assert base_height([63, 71, 68], 1, 0) == 71
    assert base_height([315], 9, 2) == 311


class FakeClient:
    def __init__(self, heights):
        self.heights = list(heights)
        self.sent = []

    def command(self, text):
        self.sent.append(text)
        if text.startswith("data get"):
            return f"Marker has the following entity data: {self.heights.pop(0)}.0d"
        if " if loaded " in text:
            return "Test passed"
        if " fill " in text:
            return "Successfully filled 3 block(s)"
        return ""


def test_planter_places_on_the_highest_sample_and_releases_chunks():
    client = FakeClient([64, 66, 65, 70, 64, 64, 64, 64, 64])
    item = at_height(structure("Y", size=15), 0)
    placed = Planter(client, DIM, "motion_blocking", 0).place(item)
    assert placed["min"][1] == 70 and placed["max"][1] == 70
    assert client.sent[0].endswith("forceload add 100 200 114 214")
    assert client.sent[-1].endswith("forceload remove 100 200 114 214")
    assert all(" 70 " in c for c in client.sent if " fill " in c)


def test_planter_raises_on_rejected_fill():
    client = FakeClient([])
    client.command = lambda text: "Test passed" if "loaded" in text else "Unknown block type"
    with pytest.raises(RuntimeError):
        Planter(client, DIM, "motion_blocking", 0).place(structure("Y"), base_y=100)


def test_plan_respects_area_and_spacing():
    structures = plan(np.random.default_rng(1), 60, "world", (0, 0, 1499, 1499), ("stone", "glass"),
                      ("Y", "X", "Z"), 7, 61, 64)
    assert len(structures) == 60
    assert {s["axis"] for s in structures} == {"Y", "X", "Z"}
    for s in structures:
        assert 0 <= s["min"][0] and s["max"][0] <= 1499 and 0 <= s["min"][2] and s["max"][2] <= 1499
        assert max(s["max"][i] - s["min"][i] + 1 for i in range(3)) == s["extent"]
    for i, a in enumerate(structures):
        for b in structures[i + 1:]:
            assert horizontal_gap(a, b) >= 64
    again = plan(np.random.default_rng(1), 60, "world", (0, 0, 1499, 1499), ("stone", "glass"),
                 ("Y", "X", "Z"), 7, 61, 64)
    assert again == structures


def test_plan_fails_when_area_is_too_small():
    with pytest.raises(ValueError):
        plan(np.random.default_rng(0), 50, "world", (0, 0, 200, 200), ("stone",), ("Y",), 7, 61, 64)


def test_packet_roundtrip():
    packet = encode_packet(7, TYPE_COMMAND, "list")
    assert packet[:4] == struct.pack("<i", 4 + 4 + 4 + 2)
    assert decode_packet(packet) == (7, TYPE_COMMAND, "list")
    assert decode_packet(encode_packet(-1, TYPE_LOGIN, "")) == (-1, TYPE_LOGIN, "")


def test_packet_rejects_bad_input():
    with pytest.raises(RconError):
        encode_packet(1, TYPE_COMMAND, "x" * 2000)
    with pytest.raises(RconError):
        decode_packet(encode_packet(1, TYPE_COMMAND, "list")[:-1])
    with pytest.raises(RconError):
        decode_packet(b"\x00" * 5)


def finding(fid, box, detail="Kachel 0,0", world="world"):
    keys = ("min_x", "min_y", "min_z", "max_x", "max_y", "max_z")
    return {"id": fid, "source": "FULLSCAN", "world": world, **dict(zip(keys, box)), "score": 0.97,
            "detail": detail}


def truth_item(sid, lo, hi, **extra):
    base = {"id": sid, "world": "world", "material": "obsidian", "extent": 7, "thickness": 1, "axis": "Y",
            "handedness": "right", "rotation": 0}
    return {**base, **extra, "min": list(lo), "max": list(hi)}


def test_window_sized_box_matches_small_symbol():
    small = truth_item(0, (10, 70, 10), (16, 70, 16))
    assert cover(small, finding(1, (0, 69, 0, 63, 72, 63))) == 1.0
    assert cover(small, finding(1, (13, 69, 0, 63, 72, 63))) == pytest.approx(4 / 7)
    assert cover(small, finding(1, (0, 80, 0, 63, 90, 63))) == 0.0
    assert cover(small, finding(1, (0, 72, 0, 63, 90, 63))) == 1.0


def test_standing_symbol_matches_surface_box_touching_its_top():
    standing = truth_item(0, (10, 70, 10), (10, 90, 30), axis="X")
    assert cover(standing, finding(1, (0, 90, 0, 40, 90, 40))) == 1.0


def test_match_classifies_findings():
    structures = [truth_item(0, (10, 70, 10), (16, 70, 16)), truth_item(1, (500, 70, 500), (520, 70, 520))]
    findings = [finding(1, (0, 69, 0, 63, 72, 63)), finding(2, (515, 70, 515, 600, 70, 600)),
                finding(3, (2000, 60, 2000, 2063, 70, 2063)), finding(4, (0, 69, 0, 63, 72, 63), world="nether")]
    hits, classes = match(structures, findings)
    assert [f["id"] for f in hits[0]] == [1] and hits[1] == []
    assert classes == {1: "matched", 2: "partial", 3: "background", 4: "background"}


def test_report_breaks_recall_down():
    structures = [truth_item(0, (0, 70, 0), (6, 70, 6), material="glass"),
                  truth_item(1, (200, 70, 200), (240, 70, 240), extent=41, thickness=9, material="stone"),
                  truth_item(2, (400, 60, 400), (400, 80, 420), extent=21, axis="X", handedness="left")]
    truth = {"world": "world", "area": [0, 0, 1000, 1000], "structures": structures}
    findings = [finding(1, (0, 69, 0, 63, 72, 63)),
                finding(2, (400, 60, 400, 400, 80, 420), detail="Volumen obsidian, Achse X"),
                finding(3, (400, 80, 400, 440, 80, 440)),
                finding(4, (800, 64, 800, 863, 70, 863)), finding(5, (5000, 64, 5000, 5063, 70, 5063))]
    result = report(truth, findings)
    assert result["overall"] == {"planted": 3, "found": 2, "recall": 0.6667}
    assert result["by"]["material"]["stone"]["recall"] == 0.0
    assert result["by"]["axis"]["X"]["found"] == 1
    assert list(result["by"]["size_bucket"]) == ["large >=32", "medium 16-31", "small <=15"]
    assert list(result["by"]["thickness"]) == ["1", "9"]
    assert result["found_by_path"] == {"missed": 1, "surface": 1, "surface+volume": 1}
    assert result["findings"]["background"] == 2 and result["findings"]["background_in_area"] == 1
    assert result["missed"] == [1]
    text = markdown(result)
    assert "| material | planted | found | recall |" in text and "| stone | 1 | 0 | 0.000 |" in text


def test_size_buckets():
    assert [size_bucket(e) for e in (7, 15, 17, 31, 33, 61)] == ["small <=15", "small <=15", "medium 16-31",
                                                                  "medium 16-31", "large >=32", "large >=32"]


def test_findings_are_read_from_a_copy(tmp_path):
    db = tmp_path / "vistructum.db"
    connection = sqlite3.connect(db)
    connection.execute("PRAGMA journal_mode=WAL")
    connection.execute("CREATE TABLE findings (id INTEGER PRIMARY KEY, source TEXT, world TEXT, min_x INT, min_y INT,"
                       " min_z INT, max_x INT, max_y INT, max_z INT, score REAL, detail TEXT)")
    connection.execute("PRAGMA wal_autocheckpoint=0")
    rows = [(1, "FULLSCAN", "world", 0, 60, 0, 63, 70, 63, 0.97, "Kachel 0,0"),
            (2, "MASK", "world", 0, 60, 0, 10, 70, 10, 0.99, "Achse Y"),
            (3, "FULLSCAN", "other", 0, 60, 0, 63, 70, 63, 0.97, "Kachel 0,0")]
    connection.executemany("INSERT INTO findings VALUES (?,?,?,?,?,?,?,?,?,?,?)", rows)
    connection.commit()
    assert (tmp_path / "vistructum.db-wal").stat().st_size > 0
    loaded = load_findings(db, "world")
    assert [f["id"] for f in loaded] == [1, 2]
    assert [f["id"] for f in load_findings(db, "world", source="fullscan")] == [1]
    assert [f["id"] for f in load_findings(db, "world", after_id=1)] == [2]
    assert (tmp_path / "vistructum.db-wal").stat().st_size > 0
    connection.close()


def test_cli_parses_both_subcommands():
    args = parser().parse_args(["plant", "--area", "-500", "-500", "500", "500", "--axes", "y,x"])
    assert args.port == 25575 and args.count == 500 and args.axes == ("Y", "X") and args.placement == "surface"
    args = parser().parse_args(["score", "--truth", "t.json", "--db", "v.db"])
    assert args.min_cover == 0.5 and args.after_id == 0
