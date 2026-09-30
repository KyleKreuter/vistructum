import type { Punishment } from "@/api/types";
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
  return [];
}
