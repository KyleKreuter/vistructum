const dateTime = new Intl.DateTimeFormat("en-GB", {
  year: "numeric",
  month: "short",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
});

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return "–";
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? iso : dateTime.format(date);
}

export function formatRelative(iso: string | null | undefined, now: number = Date.now()): string {
  if (!iso) return "–";
  const time = new Date(iso).getTime();
  if (Number.isNaN(time)) return iso;
  const seconds = Math.round((now - time) / 1000);
  const abs = Math.abs(seconds);
  const suffix = seconds >= 0 ? "ago" : "from now";
  if (abs < 45) return seconds >= 0 ? "just now" : "in a moment";
  const units: [number, string][] = [
    [60, "min"],
    [3600, "h"],
    [86400, "d"],
  ];
  const minutes = Math.round(abs / units[0][0]);
  if (minutes < 60) return `${minutes} ${units[0][1]} ${suffix}`;
  const hours = Math.round(abs / units[1][0]);
  if (hours < 24) return `${hours} ${units[1][1]} ${suffix}`;
  return `${Math.round(abs / units[2][0])} ${units[2][1]} ${suffix}`;
}

export function formatScore(score: number): string {
  return `${Math.round(score * 100)}%`;
}

export function formatPercent(value: number | null): string {
  return value === null ? "–" : `${Math.round(value * 100)}%`;
}

export function precision(confirmed: number, falseAlarms: number): number | null {
  const total = confirmed + falseAlarms;
  return total === 0 ? null : confirmed / total;
}

export function shortUuid(uuid: string): string {
  return uuid.slice(0, 8);
}

export function playerLabel(player: { uuid: string; name: string | null }): string {
  return player.name ?? shortUuid(player.uuid);
}

export function isoDay(date: Date): string {
  return date.toISOString().slice(0, 10);
}

export function dayRange(from: Date, to: Date): string[] {
  const days: string[] = [];
  const cursor = new Date(Date.UTC(from.getUTCFullYear(), from.getUTCMonth(), from.getUTCDate()));
  const end = Date.UTC(to.getUTCFullYear(), to.getUTCMonth(), to.getUTCDate());
  while (cursor.getTime() <= end && days.length < 1000) {
    days.push(isoDay(cursor));
    cursor.setUTCDate(cursor.getUTCDate() + 1);
  }
  return days;
}
