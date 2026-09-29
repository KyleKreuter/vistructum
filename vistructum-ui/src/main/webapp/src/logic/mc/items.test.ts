import { describe, expect, it } from "vitest";
import { DOWN, EAST, NORTH, SOUTH, UP, WEST } from "./direction";
import { fixtureSource } from "./fixtures";
import { extrudeSprite, itemModelRef, itemTint, resolveItem } from "./items";
import { plainsFoliage, plainsGrass } from "./tint";

const colours = { grass: plainsGrass, foliage: plainsFoliage };

describe("item definitions", () => {
  it("reads plain, conditional and special item models", () => {
    expect(itemModelRef({ model: { type: "minecraft:model", model: "minecraft:item/diamond_sword" } })).toEqual({ model: "item/diamond_sword", tints: [] });
    expect(
      itemModelRef({ model: { type: "minecraft:condition", property: "minecraft:using_item", on_false: { type: "minecraft:model", model: "minecraft:item/bow" }, on_true: {} } }),
    ).toEqual({ model: "item/bow", tints: [] });
    expect(itemModelRef({ model: { type: "minecraft:select", cases: [{ model: { type: "minecraft:model", model: "minecraft:item/clock_00" } }] } })?.model).toBe("item/clock_00");
    expect(itemModelRef({ model: { type: "minecraft:special", base: "minecraft:item/chest", model: {} } })?.model).toBe("item/chest");
  });

  it("evaluates constant and grass tints", () => {
    expect(itemTint({ type: "minecraft:constant", value: -12012264 }, colours)).toBe(0x48b518);
    expect(itemTint({ type: "minecraft:grass", temperature: 0.5, downfall: 1 }, colours)).toBe(plainsGrass);
  });
});

describe("item rendering", () => {
  it("renders generated items as sprites and blocks as block models", () => {
    const stick = resolveItem("minecraft:stick", fixtureSource, colours);
    expect(stick?.kind).toBe("sprite");
    const stone = resolveItem("minecraft:stone", { ...fixtureSource, item: () => ({ model: { type: "minecraft:model", model: "minecraft:block/stone" } }) }, colours);
    expect(stone?.kind).toBe("block");
    expect(stone?.display.rotation).toEqual([75, 45, 0]);
  });

  it("falls back without assets", () => {
    expect(resolveItem("minecraft:diamond_pickaxe", null, colours)?.kind).toBe("sprite");
    expect(resolveItem("minecraft:oak_planks", null, colours)?.kind).toBe("block");
    expect(resolveItem("minecraft:air", null, colours)).toBeNull();
  });

  it("extrudes a sprite with front, back and pixel edges", () => {
    const pixels = new Uint8ClampedArray(2 * 2 * 4);
    pixels[3] = 255;
    const quads = extrudeSprite({ width: 2, height: 2, pixels }, "item/x", -1);
    expect(quads.map((quad) => quad.normal).sort()).toEqual([DOWN, UP, NORTH, SOUTH, WEST, EAST].sort());
    const west = quads.find((quad) => quad.normal === WEST);
    expect(Math.min(...Array.from(west?.positions ?? []).filter((_, i) => i % 3 === 1))).toBeCloseTo(0.5);
  });
});
