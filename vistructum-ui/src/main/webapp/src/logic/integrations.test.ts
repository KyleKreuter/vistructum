import { describe, expect, it } from "vitest";
import { findingIntegrations } from "./integrations";

describe("findingIntegrations", () => {
  it("lists the integrations the finding can show", () => {
    expect(findingIntegrations({ rolledBackAt: null }, true, "LibertyBans")).toEqual(["coreprotect", "libertybans"]);
    expect(findingIntegrations({ rolledBackAt: null }, false, "LibertyBans")).toEqual(["libertybans"]);
    expect(findingIntegrations({ rolledBackAt: "2026-09-30T10:00:00Z" }, false, null)).toEqual(["coreprotect"]);
    expect(findingIntegrations({ rolledBackAt: null }, false, null)).toEqual([]);
  });
});
