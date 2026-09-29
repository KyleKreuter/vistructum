import { describe, expect, it } from "vitest";
import type { Scene } from "@/api/types";
import { fixtureSource } from "./fixtures";
import { rawBlock } from "./library";
import { columnBlock, terrainGrid, heatSurface } from "./terrain";

const scene: Scene = {
  source: "mask",
  width: 2,
  height: 2,
  window: { top: 0, left: 0, bottom: 1, right: 1 },
  palette: ["minecraft:stone", "minecraft:oak_slab"],
  blocks: [0, 1, -1, 0],
  heights: [64, 66, 0, 65],
  luminance: [0, 0, 0, 0],
};

describe("terrain grid", () => {
  const terrain = terrainGrid(scene);

  it("fills every column from the floor to its height", () => {
    expect(terrain.floor).toBe(63);
    expect(terrain.grid.sizeY).toBe(4);
    const at = (x: number, y: number, z: number) => terrain.grid.cells[(y * 2 + z) * 2 + x];
    expect([0, 1, 2, 3].map((y) => at(0, y, 0))).toEqual([1, 1, 0, 0]);
    expect([0, 1, 2, 3].map((y) => at(1, y, 0))).toEqual([2, 2, 2, 2]);
    expect([0, 1, 2, 3].map((y) => at(0, y, 1))).toEqual([0, 0, 0, 0]);
    expect(terrain.states[0]).toBe("minecraft:air");
  });

  it("lays a heat quad on top of every hot column", () => {
    const surface = heatSurface(terrain, { width: 2, height: 2, values: [0, 200, 0, 0] });
    expect(surface?.indices).toHaveLength(6);
    expect(surface?.positions[1]).toBeCloseTo(4, 1);
    expect(surface?.positions[0]).toBe(1);
    expect(surface?.colours[3]).toBeGreaterThan(0);
    expect(heatSurface(terrain, null)).toBeNull();
  });

  it("turns partial blocks into full columns with their textures", () => {
    const slab = columnBlock(rawBlock("minecraft:oak_slab[type=bottom]", fixtureSource), null);
    expect(slab.quads).toHaveLength(6);
    expect(new Set(slab.quads.map((quad) => quad.flush)).size).toBe(6);
    expect(slab.quads.every((quad) => quad.texture === "block/oak_planks")).toBe(true);
    const log = columnBlock(rawBlock("minecraft:oak_log[axis=y]", fixtureSource), null);
    expect(log.quads.find((quad) => quad.normal === 1)?.texture).toBe("block/oak_log_top");
  });

  it("shows the ground block below crops and plants", () => {
    const ground = rawBlock("minecraft:stone", fixtureSource);
    const wheat = columnBlock(rawBlock("minecraft:wheat[age=7]", fixtureSource), ground);
    expect(wheat.state.name).toBe("stone");
    expect(wheat.quads).toBe(ground.quads);
  });
});
