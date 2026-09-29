import { describe, expect, it } from "vitest";
import { changedCells, dirtySections, sectionCoordinates, sectionIndex, sectionLayout } from "./sections";

const size = { sizeX: 40, sizeY: 20, sizeZ: 16 };
const cell = (x: number, y: number, z: number) => (y * size.sizeZ + z) * size.sizeX + x;

describe("section tracking", () => {
  const layout = sectionLayout(size);

  it("splits the volume into sections of sixteen", () => {
    expect(layout).toEqual({ countX: 3, countY: 2, countZ: 1, total: 6 });
    expect(sectionCoordinates(layout, sectionIndex(layout, 2, 1, 0))).toEqual([2, 1, 0]);
  });

  it("marks only the owning section for an interior change", () => {
    expect([...dirtySections(size, [cell(5, 5, 5)])]).toEqual([sectionIndex(layout, 0, 0, 0)]);
  });

  it("marks neighbouring sections for a change on a border", () => {
    const dirty = dirtySections(size, [cell(15, 15, 3)]);
    expect([...dirty].sort()).toEqual([sectionIndex(layout, 0, 0, 0), sectionIndex(layout, 1, 0, 0), sectionIndex(layout, 0, 1, 0), sectionIndex(layout, 1, 1, 0)].sort());
  });

  it("lists changed cells", () => {
    expect(changedCells(Int32Array.from([1, 2, 3]), Int32Array.from([1, 5, 3]))).toEqual([1]);
  });
});
