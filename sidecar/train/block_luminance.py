import argparse
import io
import json
import sys
import zipfile
from pathlib import Path

import numpy as np
from PIL import Image

EXPLICIT_TEXTURES = {
    "minecraft:water": "water_still",
    "minecraft:lava": "lava_still",
}

SKIP_BLOCKS = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}


def mean_luminance(png_bytes):
    image = Image.open(io.BytesIO(png_bytes)).convert("RGBA")
    rgb = np.asarray(image, dtype=np.float32)
    alpha = rgb[:, :, 3] / 255.0
    gray = rgb[:, :, 0] * 0.299 + rgb[:, :, 1] * 0.587 + rgb[:, :, 2] * 0.114
    return float(np.mean(gray * alpha + 128.0 * (1.0 - alpha)))


def texture_ref_to_file(ref):
    name = ref.split("#")[-1]
    if ":" in name:
        name = name.split(":", 1)[1]
    if name.startswith("block/"):
        name = name[len("block/"):]
    elif "/" in name:
        return None
    return f"assets/minecraft/textures/block/{name}.png"


def resolve_model_textures(archive, model_name, depth=0):
    if depth > 6:
        return {}
    path = f"assets/minecraft/models/block/{model_name}.json"
    try:
        with archive.open(path) as handle:
            model = json.load(handle)
    except KeyError:
        return {}
    textures = dict(model.get("textures", {}))
    parent = model.get("parent", "")
    if parent:
        parent_name = parent.split(":", 1)[-1]
        if parent_name.startswith("block/"):
            parent_name = parent_name[len("block/"):]
        parent_textures = resolve_model_textures(archive, parent_name, depth + 1)
        merged = dict(parent_textures)
        merged.update(textures)
        textures = merged
    resolved = {}
    for key, ref in textures.items():
        if not isinstance(ref, str) or ref.startswith("#"):
            continue
        resolved[key] = ref
    return resolved


def variant_model(blockstate):
    if "variants" in blockstate:
        variants = blockstate["variants"]
        if "" in variants:
            picked = variants[""]
        else:
            picked = variants[sorted(variants)[0]]
        if isinstance(picked, list):
            picked = picked[0]
        return picked.get("model", "")
    if "multipart" in blockstate and blockstate["multipart"]:
        part = blockstate["multipart"][0].get("apply", {})
        if isinstance(part, list):
            part = part[0]
        return part.get("model", "")
    return ""


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("jar")
    parser.add_argument("out")
    args = parser.parse_args()
    archive = zipfile.ZipFile(args.jar)
    texture_means = {}
    for name in archive.namelist():
        if name.startswith("assets/minecraft/textures/block/") and name.endswith(".png"):
            with archive.open(name) as handle:
                texture_means[name] = mean_luminance(handle.read())
    table = {}
    unmapped = []
    states = [n for n in archive.namelist()
              if n.startswith("assets/minecraft/blockstates/") and n.endswith(".json")]
    for state_path in sorted(states):
        block = "minecraft:" + state_path.rsplit("/", 1)[1][:-5]
        if block in SKIP_BLOCKS:
            continue
        files = set()
        if block in EXPLICIT_TEXTURES:
            candidate = f"assets/minecraft/textures/block/{EXPLICIT_TEXTURES[block]}.png"
            if candidate in texture_means:
                files.add(candidate)
        else:
            with archive.open(state_path) as handle:
                blockstate = json.load(handle)
            model = variant_model(blockstate)
            model_name = model.split(":", 1)[-1]
            if model_name.startswith("block/"):
                model_name = model_name[len("block/"):]
            for ref in resolve_model_textures(archive, model_name).values():
                target = texture_ref_to_file(ref)
                if target and target in texture_means:
                    files.add(target)
        if files:
            table[block] = float(np.mean([texture_means[f] for f in files]))
        else:
            unmapped.append(block)
    Path(args.out).write_text(json.dumps(table, indent=1))
    print(json.dumps({"mapped": len(table), "unmapped": len(unmapped),
                      "unmapped_sample": unmapped[:20]}))


if __name__ == "__main__":
    sys.exit(main())
