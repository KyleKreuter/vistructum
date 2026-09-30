import { describe, expect, it } from "vitest";
import { canRollBack } from "./blockLog";

const confirmed = { verdict: "CONFIRMED" as const, reviewer: "kyleonaut", reviewedAt: "2026-09-01T10:00:00Z" };
const falseAlarm = { verdict: "FALSE_ALARM" as const, reviewer: "kyleonaut", reviewedAt: "2026-09-01T10:00:00Z" };
const builder = { uuid: "c2d4e6f8-1a3b-4c5d-8e7f-9a0b1c2d3e4f", name: "Mira_Builds" };

describe("canRollBack", () => {
  it("allows confirmed findings with builders", () => {
    expect(canRollBack({ review: confirmed, players: [builder] })).toBe(true);
  });

  it("rejects findings without builders or without a confirmation", () => {
    expect(canRollBack({ review: confirmed, players: [] })).toBe(false);
    expect(canRollBack({ review: falseAlarm, players: [builder] })).toBe(false);
    expect(canRollBack({ review: null, players: [builder] })).toBe(false);
  });
});
