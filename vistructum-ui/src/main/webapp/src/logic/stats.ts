import type { Stats } from "@/api/types";
import { dayRange, precision } from "./format";

export interface DayRow {
  day: string;
  mask: number;
  fullscan: number;
  confirmed: number;
  falseAlarms: number;
}

export type RangeKey = "7d" | "30d" | "90d" | "365d";

export const ranges: { key: RangeKey; label: string; days: number }[] = [
  { key: "7d", label: "7 days", days: 7 },
  { key: "30d", label: "30 days", days: 30 },
  { key: "90d", label: "90 days", days: 90 },
  { key: "365d", label: "1 year", days: 365 },
];

export function rangeBounds(key: RangeKey, now: Date): { from: Date; to: Date } {
  const days = ranges.find((range) => range.key === key)?.days ?? 30;
  const to = new Date(now.getTime());
  const from = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() - (days - 1)));
  return { from, to };
}

export function dayRows(stats: Pick<Stats, "days">, from: Date, to: Date): DayRow[] {
  const rows = new Map<string, DayRow>(dayRange(from, to).map((day) => [day, { day, mask: 0, fullscan: 0, confirmed: 0, falseAlarms: 0 }]));
  for (const entry of stats.days) {
    const row = rows.get(entry.day) ?? { day: entry.day, mask: 0, fullscan: 0, confirmed: 0, falseAlarms: 0 };
    if (entry.source === "mask") row.mask += entry.created;
    else row.fullscan += entry.created;
    row.confirmed += entry.confirmed;
    row.falseAlarms += entry.falseAlarms;
    rows.set(entry.day, row);
  }
  return [...rows.values()].sort((a, b) => a.day.localeCompare(b.day));
}

export function totals(rows: DayRow[]): { created: number; confirmed: number; falseAlarms: number } {
  return rows.reduce(
    (sum, row) => ({
      created: sum.created + row.mask + row.fullscan,
      confirmed: sum.confirmed + row.confirmed,
      falseAlarms: sum.falseAlarms + row.falseAlarms,
    }),
    { created: 0, confirmed: 0, falseAlarms: 0 },
  );
}

export function reviewerRows(stats: Pick<Stats, "reviewers">): { reviewer: string; confirmed: number; falseAlarms: number; total: number; precision: number | null }[] {
  return stats.reviewers
    .map((row) => ({ ...row, total: row.confirmed + row.falseAlarms, precision: precision(row.confirmed, row.falseAlarms) }))
    .sort((a, b) => b.total - a.total || a.reviewer.localeCompare(b.reviewer));
}

export function weekRows(rows: DayRow[]): DayRow[] {
  const weeks: DayRow[] = [];
  rows.forEach((row, index) => {
    if (index % 7 === 0) weeks.push({ ...row });
    else {
      const week = weeks[weeks.length - 1];
      week.mask += row.mask;
      week.fullscan += row.fullscan;
      week.confirmed += row.confirmed;
      week.falseAlarms += row.falseAlarms;
    }
  });
  return weeks;
}

export function compactCount(value: number): string {
  return new Intl.NumberFormat("en-GB", { notation: "compact", maximumFractionDigits: 1 }).format(value);
}

export function axisWidth(rows: DayRow[]): number {
  const highest = rows.reduce((max, row) => Math.max(max, row.mask + row.fullscan, row.confirmed, row.falseAlarms), 0);
  return Math.max(28, compactCount(highest).length * 8 + 12);
}
