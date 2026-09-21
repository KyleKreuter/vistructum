import argparse
import io
import json
import struct
import sys
import zlib
from pathlib import Path

import nbtlib
import numpy as np

AIR = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}


def read_chunks(region_path):
    blob = Path(region_path).read_bytes()
    chunks = {}
    for index in range(1024):
        location, = struct.unpack_from(">I", blob, index * 4)
        if not location:
            continue
        offset, sectors = location >> 8, location & 0xFF
        if not sectors:
            continue
        start = offset * 4096
        length, = struct.unpack_from(">I", blob, start)
        compression = blob[start + 4]
        payload = blob[start + 5:start + 4 + length]
        if compression == 1:
            import gzip
            payload = gzip.decompress(payload)
        elif compression == 2:
            payload = zlib.decompress(payload)
        elif compression != 3:
            continue
        root = nbtlib.File.from_fileobj(io.BytesIO(payload))
        chunks[(int(root["xPos"]), int(root["zPos"]))] = root
    return chunks


def unpack_section(states):
    palette = [str(entry["Name"]) for entry in states["palette"]]
    order = np.arange(4096, dtype=np.int64)
    if "data" not in states:
        return np.zeros(4096, dtype=np.uint16), palette
    words = np.array([int(v) & 0xFFFFFFFFFFFFFFFF for v in states["data"]],
                     dtype=np.uint64)
    bits = max(4, (len(palette) - 1).bit_length())
    stride = 64 // bits
    mask = (1 << bits) - 1
    shifts = ((order % stride) * bits).astype(np.uint64)
    indices = ((words[order // stride] >> shifts) & np.uint64(mask)).astype(np.uint16)
    return indices, palette


def surface_of_chunk(root):
    names = np.full((16, 16), "", dtype=object)
    heights = np.full((16, 16), -1, dtype=np.int32)
    for section in sorted(root["sections"], key=lambda s: int(s["Y"]), reverse=True):
        if "block_states" not in section:
            continue
        indices, palette = unpack_section(section["block_states"])
        grid = indices.reshape(16, 16, 16)
        for local_y in range(15, -1, -1):
            layer = grid[local_y]
            for z in range(16):
                for x in range(16):
                    if not names[z, x]:
                        candidate = palette[layer[z, x]] if layer[z, x] < len(palette) else ""
                        if candidate and candidate not in AIR:
                            names[z, x] = candidate
                            heights[z, x] = int(section["Y"]) * 16 + local_y
    return names, heights


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("region_dir")
    parser.add_argument("luminances")
    parser.add_argument("out")
    parser.add_argument("--windows", type=int, default=420)
    parser.add_argument("--stride", type=int, default=16)
    args = parser.parse_args()
    table = json.loads(Path(args.luminances).read_text())
    blocks = sorted(table)
    block_index = {name: position for position, name in enumerate(blocks)}
    values = np.array([table[name] for name in blocks], dtype=np.float32)
    tiles = {}
    for region_path in sorted(Path(args.region_dir).glob("r.*.*.mca")):
        coords = region_path.name[2:-4].split(".")
        origin = (int(coords[0]) * 512, int(coords[1]) * 512)
        for (chunk_x, chunk_z), root in read_chunks(region_path).items():
            names, heights = surface_of_chunk(root)
            tiles[(origin[0] + (chunk_x - int(coords[0]) * 32) * 16,
                   origin[1] + (chunk_z - int(coords[1]) * 32) * 16)] = (names, heights)
    print(json.dumps({"chunk_windows": len(tiles)}))
    if not tiles:
        raise SystemExit("no chunks parsed")
    xs = [x for x, _ in tiles]
    zs = [z for _, z in tiles]
    full = np.full((max(xs) + 16 - min(xs), max(zs) + 16 - min(zs)), -1, dtype=np.int32)
    full_h = np.full_like(full, -1)
    for (x, z), (names, heights) in tiles.items():
        for local_z in range(16):
            for local_x in range(16):
                name = names[local_z, local_x]
                if name and name in block_index:
                    full[x - min(xs) + local_x, z - min(zs) + local_z] = block_index[name]
                    full_h[x - min(xs) + local_x, z - min(zs) + local_z] = heights[local_z, local_x]
    candidates = []
    for x in range(0, full.shape[0] - 64 + 1, args.stride):
        for z in range(0, full.shape[1] - 64 + 1, args.stride):
            window = full[x:x + 64, z:z + 64]
            missing = float(np.mean(window < 0))
            if missing > 0.05:
                continue
            water = float(np.mean(window == block_index.get("minecraft:water", -2)))
            candidates.append((x, z, water))
    rng = np.random.default_rng(42)
    order = rng.permutation(len(candidates))
    picked = []
    lakes = 0
    for position in order:
        x, z, water = candidates[position]
        if water > 0.8:
            if lakes >= max(4, args.windows // 10):
                continue
            lakes += 1
        picked.append((x, z))
        if len(picked) >= args.windows:
            break
    out_dir = Path(args.out) / "backgrounds"
    out_dir.mkdir(parents=True, exist_ok=True)
    for number, (x, z) in enumerate(picked):
        patch = values[np.clip(full[x:x + 64, z:z + 64], 0, len(values) - 1)]
        np.save(out_dir / f"real-{number:04d}.npy", patch.astype(np.uint8))
        np.save(out_dir / f"real-{number:04d}-h.npy",
                full_h[x:x + 64, z:z + 64].astype(np.int32))
    report = {"windows": len(picked), "candidates": len(candidates),
              "mean_luminance": float(np.mean(
                  [float(np.load(out_dir / f"real-{n:04d}.npy").mean()) for n in range(len(picked))])),
              "seed": 20260920}
    (Path(args.out) / "backgrounds.json").write_text(json.dumps(report, indent=2))
    print(json.dumps(report))


if __name__ == "__main__":
    sys.exit(main())
