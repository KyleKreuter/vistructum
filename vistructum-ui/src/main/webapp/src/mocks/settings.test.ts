import { describe, expect, it } from "vitest";
import { defaultSettings, formatFailures, parseFailures, parseLatency, parseScenario, resolveSettings, settingsQuery } from "./settings";

describe("mock settings", () => {
  it("parses scenarios and falls back to the default", () => {
    expect(parseScenario("stress")).toBe("stress");
    expect(parseScenario(" EMPTY ")).toBe("empty");
    expect(parseScenario("huge")).toBe("default");
    expect(parseScenario(null)).toBe("default");
  });

  it("parses latency in milliseconds", () => {
    expect(parseLatency("800")).toBe(800);
    expect(parseLatency("0")).toBe(0);
    expect(parseLatency("")).toBeNull();
    expect(parseLatency("-5")).toBeNull();
    expect(parseLatency("fast")).toBeNull();
  });

  it("parses failing endpoints with a status and drops unknown entries", () => {
    expect(parseFailures("findings:500,me:401")).toEqual({ findings: 500, me: 401 });
    expect(parseFailures("evidence")).toEqual({ evidence: 500 });
    expect(parseFailures("nothing:500,stats:418")).toEqual({});
    expect(formatFailures({ me: 401, findings: 500 })).toBe("me:401,findings:500");
  });

  it("prefers url parameters over the stored settings", () => {
    const stored = { scenario: "stress" as const, latency: 800, failures: { stats: 503 as const } };
    expect(resolveSettings(new URLSearchParams(""), stored)).toEqual(stored);
    expect(resolveSettings(new URLSearchParams("scenario=empty&fail="), stored)).toEqual({ scenario: "empty", latency: 800, failures: {} });
    expect(settingsQuery(defaultSettings).toString()).toBe("scenario=default");
  });
});
