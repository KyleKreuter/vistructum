import { describe, expect, it } from "vitest";
import { parseBlockData, isWaterlogged } from "./blockData";
import { conditionMatches, resolveBlockstate } from "./blockstates";
import { fallbackPlacements } from "./fallbackModels";
import { fixtureBlockstates } from "./fixtures";

describe("block data", () => {
  it("parses name and properties", () => {
    const state = parseBlockData("minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]");
    expect(state).toEqual({ name: "oak_stairs", properties: { facing: "north", half: "bottom", shape: "straight", waterlogged: "false" } });
  });

  it("accepts bare names and detects waterlogging", () => {
    expect(parseBlockData("stone")).toEqual({ name: "stone", properties: {} });
    expect(isWaterlogged(parseBlockData("minecraft:oak_slab[type=bottom,waterlogged=true]"))).toBe(true);
    expect(isWaterlogged(parseBlockData("minecraft:seagrass"))).toBe(true);
    expect(isWaterlogged(parseBlockData("minecraft:oak_slab[type=bottom,waterlogged=false]"))).toBe(false);
  });
});

describe("blockstate resolution", () => {
  it("resolves the empty variant", () => {
    expect(resolveBlockstate(fixtureBlockstates.stone, {})).toEqual([{ model: "block/stone", x: 0, y: 0, uvlock: false }]);
  });

  it("picks the first entry of a weighted list", () => {
    expect(resolveBlockstate(fixtureBlockstates.oak_slab, { type: "bottom", waterlogged: "false" })).toEqual([{ model: "block/oak_slab", x: 0, y: 0, uvlock: false }]);
  });

  it("keeps rotations and uvlock of slabs top, bottom and double", () => {
    expect(resolveBlockstate(fixtureBlockstates.oak_slab, { type: "top" })[0]).toMatchObject({ x: 180, uvlock: true });
    expect(resolveBlockstate(fixtureBlockstates.oak_slab, { type: "double" })[0].model).toBe("block/stone");
  });

  it("falls back to the first variant when nothing matches", () => {
    expect(resolveBlockstate(fixtureBlockstates.oak_log, {})[0]).toMatchObject({ model: "block/oak_log", x: 90, y: 90 });
  });

  it("collects every matching multipart case of a fence", () => {
    const parts = resolveBlockstate(fixtureBlockstates.oak_fence, { north: "true", east: "false", south: "true", west: "false", waterlogged: "false" });
    expect(parts.map((part) => [part.model, part.y])).toEqual([
      ["block/oak_fence_post", 0],
      ["block/oak_fence_side", 0],
      ["block/oak_fence_side", 180],
    ]);
  });

  it("supports OR, AND, alternatives and negation in conditions", () => {
    const properties = { north: "low", east: "none", up: "true" };
    expect(conditionMatches({ north: "low|tall" }, properties)).toBe(true);
    expect(conditionMatches({ east: "low|tall" }, properties)).toBe(false);
    expect(conditionMatches({ OR: [{ east: "tall" }, { up: "true" }] }, properties)).toBe(true);
    expect(conditionMatches({ AND: [{ north: "low" }, { east: "tall" }] }, properties)).toBe(false);
    expect(conditionMatches({ east: "!low" }, properties)).toBe(true);
    expect(conditionMatches({ missing: "true" }, properties)).toBe(false);
  });
});

describe("fallback placements", () => {
  const stairs = (facing: string, half: string, shape: string) =>
    fallbackPlacements(parseBlockData(`minecraft:oak_stairs[facing=${facing},half=${half},shape=${shape},waterlogged=false]`))[0];

  it("rotates straight stairs by facing", () => {
    expect(stairs("east", "bottom", "straight")).toMatchObject({ model: "fallback/stairs/oak_stairs", x: 0, y: 0, uvlock: true });
    expect(stairs("north", "bottom", "straight")).toMatchObject({ y: 270 });
    expect(stairs("south", "top", "straight")).toMatchObject({ x: 180, y: 90 });
  });

  it("handles inner and outer stair corners like the game", () => {
    expect(stairs("east", "bottom", "inner_left")).toMatchObject({ model: "fallback/inner_stairs/oak_stairs", y: 270 });
    expect(stairs("east", "bottom", "outer_right")).toMatchObject({ model: "fallback/outer_stairs/oak_stairs", y: 0 });
    expect(stairs("east", "top", "inner_right")).toMatchObject({ model: "fallback/inner_stairs/oak_stairs", x: 180, y: 90 });
    expect(stairs("north", "top", "outer_left")).toMatchObject({ model: "fallback/outer_stairs/oak_stairs", x: 180, y: 270 });
  });

  it("builds fences from a post and connected arms", () => {
    const parts = fallbackPlacements(parseBlockData("minecraft:oak_fence[east=true,north=false,south=false,waterlogged=false,west=true]"));
    expect(parts.map((part) => `${part.model}@${part.y}`)).toEqual(["fallback/fence_post/oak_fence@0", "fallback/fence_side/oak_fence@90", "fallback/fence_side/oak_fence@270"]);
  });

  it("maps slabs, torches, plants and liquids", () => {
    expect(fallbackPlacements(parseBlockData("minecraft:stone_slab[type=top]"))[0].model).toBe("fallback/slab_top/stone_slab");
    expect(fallbackPlacements(parseBlockData("minecraft:stone_slab[type=double]"))[0].model).toBe("fallback/cube/stone_slab");
    expect(fallbackPlacements(parseBlockData("minecraft:wall_torch[facing=north]"))[0]).toMatchObject({ model: "fallback/wall_torch/wall_torch", y: 270 });
    expect(fallbackPlacements(parseBlockData("minecraft:wheat[age=7]"))[0].model).toBe("fallback/cross/wheat");
    expect(fallbackPlacements(parseBlockData("minecraft:poppy"))[0].model).toBe("fallback/cross/poppy");
    expect(fallbackPlacements(parseBlockData("minecraft:water[level=0]"))).toEqual([]);
    expect(fallbackPlacements(parseBlockData("minecraft:oak_log[axis=x]"))[0]).toMatchObject({ x: 90, y: 90 });
  });
});
