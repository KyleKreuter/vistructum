import { describe, expect, it } from "vitest";
import { defaultFilter, filterParams, findingsQuery, listParams, matchesState, parseFilter, parsePaging, sinceInstant } from "./filters";

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
    const query = new URLSearchParams(findingsQuery({ state: "open", source: "", world: "", player: "", since: "2026-09-01" }, { page: 3, pageSize: 500 }));
    expect(query.get("since")).toBe("2026-09-01T00:00:00.000Z");
    expect(query.get("page")).toBe("3");
    expect(query.get("pageSize")).toBe("100");
    expect(query.has("before")).toBe(false);
    expect(query.has("limit")).toBe(false);
    expect(query.has("source")).toBe(false);
    expect(sinceInstant("2026-13-45")).toBeNull();
  });

  it("parses and sanitises paging", () => {
    expect(parsePaging(new URLSearchParams(""))).toEqual({ page: 1, pageSize: 25 });
    expect(parsePaging(new URLSearchParams("page=4&pageSize=50"))).toEqual({ page: 4, pageSize: 50 });
    expect(parsePaging(new URLSearchParams("page=0&pageSize=30"))).toEqual({ page: 1, pageSize: 25 });
    expect(parsePaging(new URLSearchParams("page=2.5&pageSize=abc"))).toEqual({ page: 1, pageSize: 25 });
  });

  it("omits default paging from the list URL", () => {
    expect(listParams(defaultFilter, { page: 1, pageSize: 25 }).toString()).toBe("state=open");
    const params = listParams(defaultFilter, { page: 3, pageSize: 100 });
    expect(parsePaging(params)).toEqual({ page: 3, pageSize: 100 });
    expect(parseFilter(params)).toEqual(defaultFilter);
  });

  it("matches states", () => {
    expect(matchesState("open", null)).toBe(true);
    expect(matchesState("open", "CONFIRMED")).toBe(false);
    expect(matchesState("reviewed", "FALSE_ALARM")).toBe(true);
    expect(matchesState("false_alarm", "CONFIRMED")).toBe(false);
    expect(matchesState("any", null)).toBe(true);
  });
});
