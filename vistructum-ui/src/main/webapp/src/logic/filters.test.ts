import { describe, expect, it } from "vitest";
import { filterParams, findingsQuery, matchesState, parseFilter, sinceInstant } from "./filters";
import { neighbour, nextOpen } from "./listNavigation";

describe("finding filters", () => {
  it("parses and sanitises URL parameters", () => {
    const filter = parseFilter(new URLSearchParams("state=bogus&source=mask&world=%20world_nether%20&player=nope&since=2026-09-01"));
    expect(filter).toEqual({ state: "open", source: "mask", world: "world_nether", player: "", since: "2026-09-01" });
    const uuid = "5F1C7A2E-8B4D-4E39-9A61-2C7D3B0E9F14";
    expect(parseFilter(new URLSearchParams(`state=any&player=${uuid}`)).player).toBe(uuid.toLowerCase());
  });

  it("round-trips through URL parameters", () => {
    const filter = { state: "confirmed" as const, source: "fullscan" as const, world: "world", player: "", since: "" };
    expect(parseFilter(filterParams(filter))).toEqual(filter);
  });

  it("builds the API query", () => {
    const query = new URLSearchParams(findingsQuery({ state: "open", source: "", world: "", player: "", since: "2026-09-01" }, 42, 500));
    expect(query.get("since")).toBe("2026-09-01T00:00:00.000Z");
    expect(query.get("before")).toBe("42");
    expect(query.get("limit")).toBe("100");
    expect(query.has("source")).toBe(false);
    expect(sinceInstant("2026-13-45")).toBeNull();
  });

  it("matches states", () => {
    expect(matchesState("open", null)).toBe(true);
    expect(matchesState("open", "CONFIRMED")).toBe(false);
    expect(matchesState("reviewed", "FALSE_ALARM")).toBe(true);
    expect(matchesState("false_alarm", "CONFIRMED")).toBe(false);
    expect(matchesState("any", null)).toBe(true);
  });
});

describe("list navigation", () => {
  const entries = [
    { id: 50, open: true },
    { id: 40, open: false },
    { id: 30, open: true },
    { id: 20, open: true },
  ];

  it("moves through a list ordered by descending id", () => {
    expect(neighbour(entries, 40, 1)).toBe(30);
    expect(neighbour(entries, 40, -1)).toBe(50);
    expect(neighbour(entries, 50, -1)).toBeNull();
    expect(neighbour(entries, 20, 1)).toBeNull();
    expect(neighbour(entries, 35, 1)).toBe(30);
    expect(neighbour(entries, 35, -1)).toBe(40);
  });

  it("finds the next open finding and wraps around", () => {
    expect(nextOpen(entries, 50)).toBe(30);
    expect(nextOpen(entries, 30, new Set([20]))).toBe(50);
    expect(nextOpen([{ id: 1, open: true }], 1)).toBeNull();
  });
});
