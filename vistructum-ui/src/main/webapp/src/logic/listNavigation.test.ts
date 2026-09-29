import { describe, expect, it } from "vitest";
import { firstOpen, locate, locateNeighbour, locateNextOpen, neighbour, type ListEntry, type LoadedPage } from "./listNavigation";

const entries: ListEntry[] = [
  { id: 50, open: true },
  { id: 40, open: false },
  { id: 30, open: true },
  { id: 20, open: true },
];

function pagesOf(all: ListEntry[], size: number) {
  const requested: number[] = [];
  const pageCount = Math.max(1, Math.ceil(all.length / size));
  const load = (page: number): Promise<LoadedPage> => {
    requested.push(page);
    return Promise.resolve({ entries: all.slice((page - 1) * size, page * size), pageCount });
  };
  return { load, requested };
}

describe("list navigation", () => {
  it("moves through a list ordered by descending id", () => {
    expect(neighbour(entries, 40, 1)).toBe(30);
    expect(neighbour(entries, 40, -1)).toBe(50);
    expect(neighbour(entries, 50, -1)).toBeNull();
    expect(neighbour(entries, 20, 1)).toBeNull();
    expect(neighbour(entries, 35, 1)).toBe(30);
    expect(neighbour(entries, 35, -1)).toBe(40);
  });

  it("finds the first open finding below or anywhere", () => {
    expect(firstOpen(entries, 50, new Set(), true)).toBe(30);
    expect(firstOpen(entries, 30, new Set([20]), true)).toBeNull();
    expect(firstOpen(entries, 30, new Set([20]), false)).toBe(50);
    expect(firstOpen([{ id: 1, open: true }], 1, new Set(), false)).toBeNull();
  });

  it("crosses page boundaries to the neighbour", async () => {
    const { load, requested } = pagesOf(entries, 2);
    expect(await locateNeighbour(1, 40, 1, load)).toEqual({ id: 30, page: 2 });
    expect(requested).toEqual([1, 2]);
    expect(await locateNeighbour(2, 30, -1, load)).toEqual({ id: 40, page: 1 });
    expect(await locateNeighbour(2, 20, 1, load)).toBeNull();
    expect(await locateNeighbour(1, 50, -1, load)).toBeNull();
  });

  it("finds the next open finding on later pages and wraps around", async () => {
    const { load } = pagesOf(entries, 1);
    expect(await locateNextOpen(1, 50, new Set(), load)).toEqual({ id: 30, page: 3 });
    expect(await locateNextOpen(3, 30, new Set([20]), load)).toEqual({ id: 50, page: 1 });
    expect(await locateNextOpen(1, 50, new Set([30, 20]), load)).toBeNull();
  });

  it("steps back from a page beyond the end", async () => {
    const { load, requested } = pagesOf(entries, 2);
    expect(await locate(5, -1, load, (page) => page[0]?.id ?? null)).toEqual({ id: 30, page: 2 });
    expect(requested).toEqual([5, 2]);
  });
});
