import { describe, expect, it } from "vitest";
import { bakeModel, defaultUv, type RawQuad } from "./bake";
import { DOWN, EAST, NORTH, SOUTH, UP, WEST } from "./direction";
import { fixtureModels } from "./fixtures";
import { missingTexture, resolveModel, resolveTexture } from "./models";

const lookup = (id: string) => fixtureModels[id];

function bounds(quad: RawQuad): { min: number[]; max: number[] } {
  const min = [Infinity, Infinity, Infinity];
  const max = [-Infinity, -Infinity, -Infinity];
  for (let i = 0; i < 4; i++) {
    for (let k = 0; k < 3; k++) {
      min[k] = Math.min(min[k], quad.positions[i * 3 + k]);
      max[k] = Math.max(max[k], quad.positions[i * 3 + k]);
    }
  }
  return { min, max };
}

describe("model resolution", () => {
  it("follows the parent chain and resolves texture variables", () => {
    const model = resolveModel("minecraft:block/stone", lookup);
    expect(model?.elements).toHaveLength(1);
    expect(model?.elements[0].faces.every((face) => face?.texture === "block/stone")).toBe(true);
    expect(model?.display.thirdperson_righthand?.scale).toEqual([0.375, 0.375, 0.375]);
    expect(model?.ambientOcclusion).toBe(true);
  });

  it("reads ambient occlusion from the chain", () => {
    expect(resolveModel("block/wheat_stage7", lookup)?.ambientOcclusion).toBe(false);
  });

  it("marks generated item models", () => {
    const model = resolveModel("item/stick", lookup);
    expect(model?.generated).toBe(true);
    expect(model?.textures.layer0).toBe("item/stick");
  });

  it("returns missing for unknown texture variables and null for unknown models", () => {
    expect(resolveTexture("#nothing", {})).toBe(missingTexture);
    expect(resolveTexture("#a", { a: "#b", b: "minecraft:block/dirt" })).toBe("block/dirt");
    expect(resolveModel("block/unknown", lookup)).toBeNull();
  });
});

describe("baking", () => {
  it("derives default uvs from the element bounds", () => {
    expect(defaultUv(UP, [0, 0, 0], [16, 8, 16])).toEqual([0, 0, 16, 16]);
    expect(defaultUv(NORTH, [0, 0, 0], [16, 8, 16])).toEqual([0, 8, 16, 16]);
    expect(defaultUv(EAST, [4, 0, 2], [12, 16, 6])).toEqual([10, 0, 14, 16]);
  });

  it("bakes a full cube with outward winding and flush cullfaces", () => {
    const model = resolveModel("block/stone", lookup);
    if (!model) throw new Error("model");
    const quads = bakeModel(model, { x: 0, y: 0, uvlock: false });
    expect(quads.map((quad) => quad.normal)).toEqual([DOWN, UP, NORTH, SOUTH, WEST, EAST]);
    expect(quads.map((quad) => quad.flush)).toEqual([DOWN, UP, NORTH, SOUTH, WEST, EAST]);
    expect(quads.map((quad) => quad.cullface)).toEqual([DOWN, UP, NORTH, SOUTH, WEST, EAST]);
    const up = quads[1];
    expect(Array.from(up.uvs)).toEqual([0, 0, 0, 16, 16, 16, 16, 0]);
  });

  it("turns a slab upside down and keeps the top face inside the block", () => {
    const model = resolveModel("block/oak_slab", lookup);
    if (!model) throw new Error("model");
    const bottom = bakeModel(model, { x: 0, y: 0, uvlock: false });
    const top = bakeModel(model, { x: 180, y: 0, uvlock: true });
    expect(bottom.find((quad) => quad.normal === UP)?.flush).toBe(-1);
    const flipped = top.find((quad) => quad.normal === DOWN);
    expect(flipped?.flush).toBe(-1);
    expect(bounds(flipped as RawQuad).min[1]).toBeCloseTo(0.5);
    expect(top.find((quad) => quad.normal === UP)?.cullface).toBe(UP);
  });

  it("rotates fence arms around the vertical axis with their cullface", () => {
    const model = resolveModel("block/oak_fence_side", lookup);
    if (!model) throw new Error("model");
    const east = bakeModel(model, { x: 0, y: 90, uvlock: true });
    const arm = east.find((quad) => quad.cullface >= 0);
    expect(arm?.normal).toBe(EAST);
    expect(arm?.cullface).toBe(EAST);
    expect(bounds(arm as RawQuad).max[0]).toBeCloseTo(1);
    const top = east.find((quad) => quad.normal === UP) as RawQuad;
    expect(Math.max(...Array.from(top.uvs))).toBeLessThanOrEqual(16);
  });

  it("tilts a wall torch and faces it by the variant rotation", () => {
    const model = resolveModel("block/wall_torch", lookup);
    if (!model) throw new Error("model");
    const east = bakeModel(model, { x: 0, y: 0, uvlock: false });
    const top = east.find((quad) => quad.texture === "block/torch" && quad.normalVector[1] > 0.5) as RawQuad;
    expect(top.axisAligned).toBe(false);
    expect(bounds(top).min[0]).toBeGreaterThan(0.1);
    const north = bakeModel(model, { x: 0, y: 270, uvlock: false });
    const northTop = north.find((quad) => quad.normalVector[1] > 0.5) as RawQuad;
    expect(bounds(northTop).max[2]).toBeLessThan(0.9);
    expect(bounds(northTop).min[2]).toBeGreaterThan(0.5);
  });

  it("rescales a 45 degree cross so it spans the block diagonal", () => {
    const cross = { elements: [{ from: [0.8, 0, 8], to: [15.2, 16, 8], rotation: { origin: [8, 8, 8], axis: "y", angle: 45, rescale: true }, faces: { north: { texture: "#t" } } }], textures: { t: "block/x" } };
    const model = resolveModel("cross", (id) => (id === "cross" ? cross : undefined));
    if (!model) throw new Error("model");
    const [quad] = bakeModel(model, { x: 0, y: 0, uvlock: false });
    const { min, max } = bounds(quad);
    expect(min[0]).toBeCloseTo(0.05, 2);
    expect(max[0]).toBeCloseTo(0.95, 2);
    expect(min[2]).toBeCloseTo(0.05, 2);
    expect(max[2]).toBeCloseTo(0.95, 2);
  });
});
