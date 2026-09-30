import type { Punishment, PunishmentType } from "@/api/types";
import { staff } from "./data";
import { builder, watcher } from "./evidence";

const day = 86_400_000;

function at(now: number, offsetDays: number): string {
  return new Date(now + offsetDays * day).toISOString();
}

export function mockPunishments(uuid: string, now: number): Punishment[] {
  if (uuid === builder.uuid) {
    return [
      { type: "BAN", reason: "Inappropriate build at spawn", operator: "kyleonaut", issuedAt: at(now, -1), expiresAt: at(now, 6), active: true },
      { type: "WARN", reason: "Offensive sign text", operator: "Staff_Anna", issuedAt: at(now, -9), expiresAt: null, active: true },
      { type: "MUTE", reason: "Spam", operator: "Console", issuedAt: at(now, -21), expiresAt: at(now, -20), active: false },
      { type: "KICK", reason: null, operator: "Staff_Anna", issuedAt: at(now, -30), expiresAt: null, active: false },
    ];
  }
  if (uuid === watcher.uuid) {
    return [
      { type: "MUTE", reason: "Harassment", operator: "kyleonaut", issuedAt: at(now, -3), expiresAt: null, active: true },
      { type: "BAN", reason: "Griefing", operator: null, issuedAt: at(now, -60), expiresAt: null, active: false },
    ];
  }
  return uuid === staff.uuid ? [] : generatedPunishments(uuid, now);
}

const reasons = ["Griefing", "Offensive build", "Spam", "Hate symbol", "Advertising", "Harassment", null];
const operators = ["kyleonaut", "Staff_Anna", "mod_jonas", "Console", null];
const types: PunishmentType[] = ["BAN", "MUTE", "WARN", "KICK"];

function seedOf(uuid: string): number {
  let hash = 2166136261;
  for (const char of uuid) hash = Math.imul(hash ^ char.charCodeAt(0), 16777619);
  return hash >>> 0;
}

function generatedPunishments(uuid: string, now: number): Punishment[] {
  let seed = seedOf(uuid);
  const next = () => {
    seed = Math.imul(seed ^ (seed >>> 15), 2246822507) + 0x9e3779b9;
    return ((seed ^ (seed >>> 13)) >>> 0) / 4294967296;
  };
  const profile = Math.floor(next() * 6);
  if (profile === 0) return [];
  const count = profile === 5 ? 18 + Math.floor(next() * 10) : 1 + Math.floor(next() * 4);
  const history: Punishment[] = [];
  let offset = -1 - next() * 3;
  for (let index = 0; index < count; index++) {
    const type = types[Math.floor(next() * types.length)];
    const permanent = type !== "KICK" && next() < 0.3;
    const length = 1 + Math.floor(next() * 30);
    const expiresAt = type === "KICK" || permanent || (type === "WARN" && next() < 0.5) ? null : at(now, offset + length);
    const lifted = next() < 0.2;
    history.push({
      type,
      reason: reasons[Math.floor(next() * reasons.length)],
      operator: operators[Math.floor(next() * operators.length)],
      issuedAt: at(now, offset),
      expiresAt,
      active: type !== "KICK" && !lifted,
    });
    offset -= 1 + next() * 20;
  }
  if (profile === 1) history[0] = { ...history[0], type: "BAN", expiresAt: at(now, 3 + next() * 20), active: true };
  if (profile === 2) history[0] = { ...history[0], type: "MUTE", expiresAt: null, active: true };
  return history;
}
