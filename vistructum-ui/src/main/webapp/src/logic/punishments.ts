import type { Punishment, PunishmentType } from "@/api/types";
import { formatDateTime } from "./format";

const typeLabels: Record<PunishmentType, string> = { BAN: "Ban", MUTE: "Mute", WARN: "Warning", KICK: "Kick" };

export function punishmentTypeLabel(type: PunishmentType): string {
  return typeLabels[type];
}

export function inForce(punishment: Punishment, now: number = Date.now()): boolean {
  if (!punishment.active || (punishment.type !== "BAN" && punishment.type !== "MUTE")) return false;
  return !punishment.expiresAt || new Date(punishment.expiresAt).getTime() > now;
}

export function expires(punishment: Punishment): boolean {
  return punishment.type === "BAN" || punishment.type === "MUTE" || (punishment.type === "WARN" && !!punishment.expiresAt);
}

export function expiryLabel(punishment: Punishment): string {
  if (!expires(punishment)) return "–";
  return punishment.expiresAt ? formatDateTime(punishment.expiresAt) : "Permanent";
}
