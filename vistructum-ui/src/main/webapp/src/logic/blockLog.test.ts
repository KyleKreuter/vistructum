import { describe, expect, it } from "vitest";
import { attributionMessage, canAttribute, canRollBack, rollbackMessage } from "./blockLog";

const confirmed = { verdict: "CONFIRMED" as const, reviewer: "kyleonaut", reviewedAt: "2026-09-01T10:00:00Z" };
const falseAlarm = { verdict: "FALSE_ALARM" as const, reviewer: "kyleonaut", reviewedAt: "2026-09-01T10:00:00Z" };
const builder = { uuid: "c2d4e6f8-1a3b-4c5d-8e7f-9a0b1c2d3e4f", name: "Mira_Builds" };

describe("canRollBack", () => {
  it("allows confirmed findings with builders", () => {
    expect(canRollBack({ review: confirmed, players: [builder], rolledBackAt: null })).toBe(true);
  });

  it("allows one rollback only", () => {
    expect(canRollBack({ review: confirmed, players: [builder], rolledBackAt: "2026-09-02T10:00:00Z" })).toBe(false);
  });

  it("rejects findings without builders or without a confirmation", () => {
    expect(canRollBack({ review: confirmed, players: [], rolledBackAt: null })).toBe(false);
    expect(canRollBack({ review: falseAlarm, players: [builder], rolledBackAt: null })).toBe(false);
    expect(canRollBack({ review: null, players: [builder], rolledBackAt: null })).toBe(false);
  });
});

describe("canAttribute", () => {
  it("allows a search while builders or evidence are missing", () => {
    expect(canAttribute({ players: [], hasEvidence: true })).toBe(true);
    expect(canAttribute({ players: [builder], hasEvidence: false })).toBe(true);
    expect(canAttribute({ players: [builder], hasEvidence: true })).toBe(false);
  });
});

describe("rollbackMessage", () => {
  it("names restored and skipped blocks", () => {
    expect(rollbackMessage(48, 0)).toBe("Rolled back 48 blocks.");
    expect(rollbackMessage(1, 1)).toBe("Rolled back 1 block. Skipped 1 block that others changed since.");
    expect(rollbackMessage(0, 3)).toBe("No block needed a rollback. Skipped 3 blocks that others changed since.");
  });
});

describe("attributionMessage", () => {
  it("names up to two builders and counts more", () => {
    expect(attributionMessage(["Mira_Builds"])).toBe("Builders: Mira_Builds");
    expect(attributionMessage(["Mira_Builds", "Tom"])).toBe("Builders: Mira_Builds, Tom");
    expect(attributionMessage(["Mira_Builds", "Tom", "Ana"])).toBe("Found 3 builders.");
  });
});
