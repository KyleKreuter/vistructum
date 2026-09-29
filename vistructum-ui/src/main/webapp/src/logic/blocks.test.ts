import { describe, expect, it } from "vitest";
import { blockProperties, colourOf, isAir, isFullOpaque, isTransparent, materialOf, shapeBox, shapeOf } from "./blocks";

describe("blocks", () => {
  it("extracts the material part of block data", () => {
    expect(materialOf("minecraft:oak_stairs[facing=north,half=bottom]")).toBe("minecraft:oak_stairs");
    expect(materialOf("STONE")).toBe("minecraft:stone");
    expect(materialOf("")).toBe("minecraft:air");
  });

  it("parses block properties", () => {
    expect(blockProperties("minecraft:stone_slab[type=top,waterlogged=false]")).toEqual({ type: "top", waterlogged: "false" });
    expect(blockProperties("minecraft:stone")).toEqual({});
  });

  it("classifies shapes", () => {
    expect(shapeOf("minecraft:cave_air")).toBe("empty");
    expect(shapeOf("minecraft:stone_slab[type=bottom]")).toBe("bottomSlab");
    expect(shapeOf("minecraft:stone_slab[type=top]")).toBe("topSlab");
    expect(shapeOf("minecraft:stone_slab[type=double]")).toBe("cube");
    expect(shapeOf("minecraft:white_carpet")).toBe("thin");
    expect(shapeOf("minecraft:poppy")).toBe("small");
    expect(shapeOf("minecraft:oak_stairs[facing=north]")).toBe("cube");
    expect(shapeBox("bottomSlab")).toEqual({ height: 0.5, offset: 0.25, width: 1 });
  });

  it("recognises transparent and air blocks", () => {
    expect(isTransparent("minecraft:glass")).toBe(true);
    expect(isTransparent("minecraft:red_stained_glass_pane[east=true]")).toBe(true);
    expect(isTransparent("minecraft:water[level=0]")).toBe(true);
    expect(isTransparent("minecraft:ice")).toBe(true);
    expect(isTransparent("minecraft:packed_ice")).toBe(false);
    expect(isTransparent("minecraft:glass_bottle")).toBe(false);
    expect(isAir("minecraft:air")).toBe(true);
    expect(isFullOpaque("minecraft:stone")).toBe(true);
    expect(isFullOpaque("minecraft:glass")).toBe(false);
  });

  it("resolves colours from the palette with fallbacks", () => {
    const palette = { "minecraft:stone": 0x707070 };
    expect(colourOf("minecraft:stone", palette)).toBe(0x707070);
    expect(colourOf("minecraft:water[level=0]", palette)).toBe(0x3f76e4);
    expect(colourOf("minecraft:unknown_block", palette)).toBe(0x9e9e9e);
  });
});
