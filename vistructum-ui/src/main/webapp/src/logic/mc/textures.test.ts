import { describe, expect, it } from "vitest";
import { alphaClass, animationFrameAt, atlasUv, firstFrameIndex, frameImage, packAtlas, parseAnimation, type TextureImage } from "./atlas";
import { materialClass, proceduralTexture } from "./procedural";
import { blockTint, colormapCoordinates, plainsClimate, plainsFoliage, plainsGrass, redstoneColour, sampleColormap, spruceFoliage, waterColour } from "./tint";
import { parseBlockData } from "./blockData";

function solid(size: number, rgba: [number, number, number, number]): TextureImage {
  const pixels = new Uint8ClampedArray(size * size * 4);
  for (let i = 0; i < size * size; i++) pixels.set(rgba, i * 4);
  return { width: size, height: size, pixels };
}

describe("atlas", () => {
  it("packs tiles with a replicated gutter against bleeding", () => {
    const red = solid(16, [255, 0, 0, 255]);
    const blue = solid(16, [0, 0, 255, 255]);
    const atlas = packAtlas([
      { id: "red", image: red },
      { id: "blue", image: blue },
    ]);
    const rect = atlas.rects.get("red");
    if (!rect) throw new Error("rect");
    const gutter = ((rect.y - 1) * atlas.width + rect.x - 1) * 4;
    expect(Array.from(atlas.pixels.slice(gutter, gutter + 4))).toEqual([255, 0, 0, 255]);
    const other = atlas.rects.get("blue");
    expect(other && (Math.abs(other.x - rect.x) >= 16 + 4 || Math.abs(other.y - rect.y) >= 16 + 4)).toBe(true);
    const [u, v] = atlasUv(atlas, rect, 16, 16);
    expect(u).toBeCloseTo((rect.x + 16) / atlas.width);
    expect(v).toBeCloseTo((rect.y + 16) / atlas.height);
  });

  it("classifies alpha", () => {
    expect(alphaClass(solid(16, [1, 2, 3, 255]))).toBe(0);
    const holes = solid(16, [1, 2, 3, 255]);
    holes.pixels[3] = 0;
    expect(alphaClass(holes)).toBe(1);
    expect(alphaClass(solid(16, [1, 2, 3, 150]))).toBe(2);
  });

  it("takes the first frame of animated strips and cycles frames", () => {
    const strip: TextureImage = { width: 2, height: 6, pixels: new Uint8ClampedArray(2 * 6 * 4).map((_, i) => Math.floor(i / 16)) };
    expect(frameImage(strip, 1).pixels[0]).toBe(1);
    expect(firstFrameIndex({ frames: [2, 0, 1] }, 3)).toBe(2);
    const animation = parseAnimation({ frametime: 2 }, 3);
    if (!animation) throw new Error("animation");
    expect([0, 1, 2, 3, 4, 5, 6].map((tick) => animationFrameAt(animation, tick))).toEqual([0, 0, 1, 1, 2, 2, 0]);
    const custom = parseAnimation({ frames: [{ index: 1, time: 3 }, 0] }, 2);
    if (!custom) throw new Error("custom");
    expect([0, 2, 3].map((tick) => animationFrameAt(custom, tick))).toEqual([1, 1, 0]);
  });
});

describe("procedural textures", () => {
  it("is stable per material and differs between materials", () => {
    expect(proceduralTexture("stone", "all", 0x707070)).toEqual(proceduralTexture("stone", "all", 0x707070));
    expect(proceduralTexture("stone", "all", 0x707070)).not.toEqual(proceduralTexture("andesite", "all", 0x707070));
  });

  it("classifies materials", () => {
    const expectations: [string, string][] = [
      ["stone", "stone"],
      ["cobblestone_stairs", "stone"],
      ["dirt", "dirt"],
      ["grass_block", "grass"],
      ["sand", "sand"],
      ["oak_planks", "planks"],
      ["spruce_stairs", "planks"],
      ["oak_log", "log"],
      ["birch_leaves", "leaves"],
      ["glass", "glass"],
      ["red_stained_glass_pane", "glass"],
      ["white_wool", "wool"],
      ["black_concrete", "concrete"],
      ["diamond_ore", "ore"],
      ["water", "water"],
      ["lava", "lava"],
      ["iron_block", "metal"],
      ["poppy", "plant"],
      ["wall_torch", "torch"],
      ["crafting_table", "planks"],
      ["sponge", "generic"],
    ];
    for (const [name, cls] of expectations) expect([name, materialClass(name)]).toEqual([name, cls]);
  });

  it("cuts holes into leaves and keeps glass clear", () => {
    expect(alphaClass({ width: 16, height: 16, pixels: proceduralTexture("oak_leaves", "all", 0x4a7a20) })).toBe(1);
    expect(alphaClass({ width: 16, height: 16, pixels: proceduralTexture("glass", "all", 0xffffff) })).toBe(1);
    expect(alphaClass({ width: 16, height: 16, pixels: proceduralTexture("blue_stained_glass", "all", 0x3344aa) })).toBe(2);
    expect(alphaClass({ width: 16, height: 16, pixels: proceduralTexture("water", "all", 0) })).toBe(2);
    expect(alphaClass({ width: 16, height: 16, pixels: proceduralTexture("stone", "all", 0x777777) })).toBe(0);
  });
});

describe("tints", () => {
  it("samples the colormap at the plains climate", () => {
    expect(colormapCoordinates(plainsClimate)).toEqual({ x: 50, y: 173 });
    const pixels = new Uint8ClampedArray(256 * 256 * 4);
    const i = (173 * 256 + 50) * 4;
    pixels.set([0x91, 0xbd, 0x59, 255], i);
    expect(sampleColormap({ width: 256, height: 256, pixels }, plainsClimate, 0)).toBe(0x91bd59);
    expect(sampleColormap(null, plainsClimate, 0x123456)).toBe(0x123456);
  });

  it("colours blocks like the game", () => {
    const colours = { grass: plainsGrass, foliage: plainsFoliage };
    expect(blockTint(parseBlockData("minecraft:grass_block[snowy=false]"), colours)).toBe(plainsGrass);
    expect(blockTint(parseBlockData("minecraft:oak_leaves"), colours)).toBe(plainsFoliage);
    expect(blockTint(parseBlockData("minecraft:spruce_leaves"), colours)).toBe(spruceFoliage);
    expect(blockTint(parseBlockData("minecraft:water[level=0]"), colours)).toBe(waterColour);
    expect(blockTint(parseBlockData("minecraft:redstone_wire[power=15]"), colours)).toBe(redstoneColour(15));
    expect(redstoneColour(0)).toBe(0x4c0000);
    expect(redstoneColour(15)).toBe(0xff3200);
    expect(blockTint(parseBlockData("minecraft:stone"), colours)).toBe(0xffffff);
  });
});
