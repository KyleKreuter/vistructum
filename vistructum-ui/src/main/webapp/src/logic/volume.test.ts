import { describe, expect, it } from "vitest";
import type { BlockChange, BlockVolume } from "@/api/types";
import { cellIndex, cellOf, dynamicStates, indexVolume, worldIndex } from "./volume";

const volume: BlockVolume = {
  minX: 10,
  minY: 60,
  minZ: -5,
  sizeX: 3,
  sizeY: 3,
  sizeZ: 3,
  palette: ["minecraft:air", "minecraft:stone"],
  cells: [...new Array(9).fill(1), ...new Array(9).fill(1), ...new Array(9).fill(0)],
};

const change = (t: number, action: "PLACE" | "BREAK", x: number, y: number, z: number, blockData = "minecraft:red_wool"): BlockChange => ({
  t,
  player: "p",
  playerName: "P",
  action,
  x,
  y,
  z,
  blockData,
});

describe("volume indexing", () => {
  it("follows the contract cell order", () => {
    expect(cellIndex(volume, 1, 0, 0)).toBe(1);
    expect(cellIndex(volume, 0, 0, 1)).toBe(3);
    expect(cellIndex(volume, 0, 1, 0)).toBe(9);
    expect(cellOf(volume, 22)).toEqual({ dx: 1, dy: 2, dz: 1 });
    for (let i = 0; i < 27; i++) {
      const { dx, dy, dz } = cellOf(volume, i);
      expect(cellIndex(volume, dx, dy, dz)).toBe(i);
    }
  });

  it("maps world coordinates into the volume", () => {
    expect(worldIndex(volume, 10, 60, -5)).toBe(0);
    expect(worldIndex(volume, 12, 62, -3)).toBe(26);
    expect(worldIndex(volume, 13, 60, -5)).toBe(-1);
    expect(worldIndex(volume, 10, 59, -5)).toBe(-1);
  });

  it("applies changes up to a count", () => {
    const changes = [change(1, "PLACE", 11, 62, -4), change(2, "BREAK", 11, 61, -4), change(3, "BREAK", 11, 62, -4), change(4, "PLACE", 50, 62, 0)];
    const indexed = indexVolume(volume, changes);
    expect(indexed.dynamicCells).toEqual([cellIndex(volume, 1, 1, 1), cellIndex(volume, 1, 2, 1)]);
    expect(indexed.changeCells[3]).toBe(-1);
    const wool = indexed.states.indexOf("minecraft:red_wool");
    expect([...dynamicStates(indexed, 0)]).toEqual([1, 0]);
    expect([...dynamicStates(indexed, 1)]).toEqual([1, wool]);
    expect([...dynamicStates(indexed, 2)]).toEqual([0, wool]);
    expect([...dynamicStates(indexed, 3)]).toEqual([0, 0]);
    expect([...dynamicStates(indexed, 99)]).toEqual([0, 0]);
  });
});
