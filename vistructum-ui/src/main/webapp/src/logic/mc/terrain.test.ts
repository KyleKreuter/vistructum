import { describe, expect, it } from "vitest";
import type { Terrain } from "@/api/types";
import { boxOutline, columnTops, heatSurface, terrainGrid, typicalTop } from "./terrain";

const terrain: Terrain = {
  blocks: {
    minX: 100,
    minY: 60,
    minZ: 200,
    sizeX: 2,
    sizeY: 3,
    sizeZ: 2,
    palette: ["minecraft:air", "minecraft:stone", "minecraft:oak_leaves[distance=1]"],
    cells: [1, 1, 1, 0, 0, 1, 0, 0, 2, 0, 0, 0],
  },
  sceneOrigin: { x: 99, z: 200 },
};

describe("terrain", () => {
  it("meshes the stored palette indices unchanged", () => {
    const grid = terrainGrid(terrain);
    expect([grid.sizeX, grid.sizeY, grid.sizeZ]).toEqual([2, 3, 2]);
    expect([...grid.cells]).toEqual(terrain.blocks.cells);
  });

  it("finds the highest block of every column", () => {
    const tops = columnTops(terrain);
    expect([...tops]).toEqual([2, 1, 0, -1]);
    expect(typicalTop(tops)).toBe(2);
    expect(typicalTop(new Int32Array([-1]))).toBe(0);
  });

  it("maps the finding box into volume coordinates", () => {
    expect(boxOutline(terrain, { minX: 101, minY: 60, minZ: 200, maxX: 101, maxY: 62, maxZ: 201 })).toEqual({ x0: 1, x1: 2, z0: 0, z1: 2 });
  });

  it("lays heat on the column the scene cell covers", () => {
    const tops = columnTops(terrain);
    const surface = heatSurface(terrain, tops, { width: 3, height: 2, values: [0, 200, 0, 0, 0, 0] }, 1);
    expect(surface?.indices).toHaveLength(6);
    expect(surface?.positions[0]).toBe(0);
    expect(surface?.positions[1]).toBeCloseTo(3, 1);
    expect(surface?.positions[2]).toBe(0);
    expect(heatSurface(terrain, tops, null)).toBeNull();
    expect(heatSurface({ ...terrain, sceneOrigin: null }, tops, { width: 3, height: 2, values: [0, 200, 0, 0, 0, 0] })).toBeNull();
  });
});
