import { describe, expect, it } from "vitest";
import type { Punishment } from "@/api/types";
import { expires, expiryLabel, inForce, punishmentTypeLabel } from "./punishments";

const now = Date.parse("2026-09-30T12:00:00Z");

function punishment(overrides: Partial<Punishment>): Punishment {
  return { type: "BAN", reason: null, operator: null, issuedAt: "2026-09-01T10:00:00Z", expiresAt: null, active: true, ...overrides };
}

describe("inForce", () => {
  it("counts active permanent bans", () => {
    expect(inForce(punishment({}), now)).toBe(true);
  });

  it("ignores expired and lifted punishments", () => {
    expect(inForce(punishment({ expiresAt: "2026-09-29T12:00:00Z" }), now)).toBe(false);
    expect(inForce(punishment({ active: false }), now)).toBe(false);
  });

  it("never counts warnings and kicks", () => {
    expect(inForce(punishment({ type: "WARN" }), now)).toBe(false);
    expect(inForce(punishment({ type: "KICK" }), now)).toBe(false);
  });
});

describe("expires", () => {
  it("applies to bans, mutes and temporary warnings", () => {
    expect(expires(punishment({}))).toBe(true);
    expect(expires(punishment({ type: "MUTE" }))).toBe(true);
    expect(expires(punishment({ type: "WARN", expiresAt: "2026-10-01T12:00:00Z" }))).toBe(true);
    expect(expires(punishment({ type: "WARN" }))).toBe(false);
    expect(expires(punishment({ type: "KICK", expiresAt: "2026-10-01T12:00:00Z" }))).toBe(false);
  });
});

describe("labels", () => {
  it("names each type", () => {
    expect(["BAN", "MUTE", "WARN", "KICK"].map((type) => punishmentTypeLabel(type as Punishment["type"]))).toEqual(["Ban", "Mute", "Warning", "Kick"]);
  });

  it("describes the expiry", () => {
    expect(expiryLabel(punishment({}))).toBe("Permanent");
    expect(expiryLabel(punishment({ type: "KICK" }))).toBe("–");
    expect(expiryLabel(punishment({ type: "WARN" }))).toBe("–");
    expect(expiryLabel(punishment({ expiresAt: "2026-10-01T12:00:00Z" }))).toMatch(/^01 Oct 2026/);
  });
});
