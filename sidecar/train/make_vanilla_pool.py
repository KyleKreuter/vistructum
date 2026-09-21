import argparse
import io
import json
import sys
import zipfile
from pathlib import Path

import numpy as np
from PIL import Image

GRID = 64
SEED = 42

EXCLUDE_SUBSTRINGS = (
    "door", "trapdoor", "glass", "pane", "leaves", "sapling", "crop", "wheat",
    "carrot", "potato", "beetroot", "melon_stem", "pumpkin_stem", "rail",
    "torch", "lantern", "candle", "sign", "banner", "bed", "chest", "water",
    "lava", "fire", "portal", "vine", "ladder", "button", "plate", "lever",
    "hook", "flower", "tulip", "dandelion", "poppy", "orchid", "allium",
    "blossom", "roots", "sprouts", "wart", "fungus", "mushroom", "stage",
    "budding", "cluster", "dripstone", "egg", "frogspawn", "sensor",
    "trial_spawner", "vault", "piston_head", "repeater", "comparator",
    "detector", "carpet", "snow", "cactus_flower", "torchflower", "pitcher",
    "overlay", "anemone", "bush", "cocoa", "nether_sprouts", "kelp",
    "seagrass", "pickle", "coral", "sponge_wet", "tnt_side",
    "sculk", "calibrated", "crafter", "decorated_pot", "chiseled",
    "cut_copper", "chest_boat",
)


def load_block_textures(jar_path):
    textures = []
    with zipfile.ZipFile(jar_path) as archive:
        names = [n for n in archive.namelist()
                 if n.startswith("assets/minecraft/textures/block/") and n.endswith(".png")]
        for name in sorted(names):
            short = name.rsplit("/", 1)[1].rsplit(".", 1)[0]
            if any(part in short for part in EXCLUDE_SUBSTRINGS):
                continue
            with archive.open(name) as handle:
                image = Image.open(io.BytesIO(handle.read())).convert("RGBA")
            rgb = np.asarray(image, dtype=np.float32)
            alpha = rgb[:, :, 3] / 255.0
            gray = rgb[:, :, 0] * 0.299 + rgb[:, :, 1] * 0.587 + rgb[:, :, 2] * 0.114
            flat = gray * alpha + 128.0 * (1.0 - alpha)
            textures.append(flat)
    return textures


def tile_to(textures, rng, tile_pixels):
    pick = textures[int(rng.integers(len(textures)))]
    image = Image.fromarray(np.clip(pick, 0, 255).astype(np.uint8))
    return np.asarray(image.resize((tile_pixels, tile_pixels), Image.NEAREST), dtype=np.float32)


def make_patch(textures, rng):
    layout = int(rng.integers(3))
    if layout == 0:
        return tile_to(textures, rng, GRID)
    tiles = 2 if layout == 1 else 4
    size = GRID // tiles
    rows = []
    for _ in range(tiles):
        rows.append(np.concatenate([tile_to(textures, rng, size) for _ in range(tiles)], axis=1))
    return np.concatenate(rows, axis=0)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("jar", help="minecraft client.jar with block textures")
    parser.add_argument("target", help="dataset directory receiving backgrounds/")
    parser.add_argument("--count", type=int, default=384)
    args = parser.parse_args()
    rng = np.random.default_rng(SEED)
    textures = load_block_textures(args.jar)
    if not textures:
        raise SystemExit("no usable block textures found")
    out_dir = Path(args.target) / "backgrounds"
    out_dir.mkdir(parents=True, exist_ok=True)
    luminances = []
    for index in range(args.count):
        patch = np.clip(make_patch(textures, rng), 0, 255).astype(np.uint8)
        np.save(out_dir / f"vanilla-{index:04d}.npy", patch)
        luminances.append(float(patch.mean()))
    report = {
        "textures_used": len(textures),
        "patches": args.count,
        "mean_luminance": float(np.mean(luminances)),
        "min_luminance": float(np.min(luminances)),
        "max_luminance": float(np.max(luminances)),
    }
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    sys.exit(main())
