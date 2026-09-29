import { describe, expect, it } from "vitest";
import { pageCount, pageRange, pageSlots } from "./pagination";

describe("pagination", () => {
  it("counts at least one page", () => {
    expect(pageCount(0, 25)).toBe(1);
    expect(pageCount(119, 25)).toBe(5);
    expect(pageCount(125, 25)).toBe(5);
    expect(pageCount(126, 25)).toBe(6);
  });

  it("lists every page when there are few", () => {
    expect(pageSlots(1, 1)).toEqual([1]);
    expect(pageSlots(3, 7)).toEqual([1, 2, 3, 4, 5, 6, 7]);
  });

  it("collapses distant pages into gaps", () => {
    expect(pageSlots(1, 20)).toEqual([1, 2, 3, 4, 5, "gap", 20]);
    expect(pageSlots(4, 20)).toEqual([1, 2, 3, 4, 5, "gap", 20]);
    expect(pageSlots(5, 20)).toEqual([1, "gap", 4, 5, 6, "gap", 20]);
    expect(pageSlots(17, 20)).toEqual([1, "gap", 16, 17, 18, 19, 20]);
    expect(pageSlots(20, 20)).toEqual([1, "gap", 16, 17, 18, 19, 20]);
  });

  it("describes the visible range", () => {
    expect(pageRange(1, 25, 119)).toEqual({ from: 1, to: 25 });
    expect(pageRange(5, 25, 119)).toEqual({ from: 101, to: 119 });
    expect(pageRange(6, 25, 119)).toEqual({ from: 0, to: 0 });
    expect(pageRange(1, 25, 0)).toEqual({ from: 0, to: 0 });
  });
});
