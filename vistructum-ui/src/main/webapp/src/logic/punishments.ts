import type { Punishment, PunishmentType } from "@/api/types";
import { formatDateTime } from "./format";

export interface PunishmentStatus {
  type: "BAN" | "MUTE";
  label: string;
}

const typeLabels: Record<PunishmentType, string> = { BAN: "Ban", MUTE: "Mute", WARN: "Warning", KICK: "Kick" };
const statusVerbs = { BAN: "Banned", MUTE: "Muted" } as const;

export function punishmentTypeLabel(type: PunishmentType): string {
  return typeLabels[type];
}

export function inForce(punishment: Punishment, now: number = Date.now()): boolean {
  if (!punishment.active || (punishment.type !== "BAN" && punishment.type !== "MUTE")) return false;
  return !punishment.expiresAt || new Date(punishment.expiresAt).getTime() > now;
}

function endOf(punishment: Punishment): number {
  return punishment.expiresAt ? new Date(punishment.expiresAt).getTime() : Number.POSITIVE_INFINITY;
}

export function activeStatuses(items: Punishment[], now: number = Date.now()): PunishmentStatus[] {
  return (["BAN", "MUTE"] as const).flatMap((type) => {
    const longest = items
      .filter((punishment) => punishment.type === type && inForce(punishment, now))
      .reduce<Punishment | null>((best, punishment) => (!best || endOf(punishment) > endOf(best) ? punishment : best), null);
    if (!longest) return [];
    const label = longest.expiresAt ? `${statusVerbs[type]} until ${formatDateTime(longest.expiresAt)}` : statusVerbs[type];
    return [{ type, label }];
  });
}

export function expires(punishment: Punishment): boolean {
  return punishment.type === "BAN" || punishment.type === "MUTE" || (punishment.type === "WARN" && !!punishment.expiresAt);
}

export function expiryLabel(punishment: Punishment): string {
  if (!expires(punishment)) return "–";
  return punishment.expiresAt ? formatDateTime(punishment.expiresAt) : "Permanent";
}
