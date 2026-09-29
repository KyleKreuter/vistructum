import type { FindingFilter, FindingState, Source } from "@/api/types";

export const states: { value: FindingState; label: string }[] = [
  { value: "open", label: "Open" },
  { value: "reviewed", label: "Reviewed" },
  { value: "confirmed", label: "Confirmed" },
  { value: "false_alarm", label: "False alarm" },
  { value: "any", label: "All" },
];

const stateValues = new Set<string>(states.map((state) => state.value));
const sourceValues = new Set<string>(["mask", "fullscan"]);
const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export const defaultFilter: FindingFilter = { state: "open", source: "", world: "", player: "", since: "" };

export function parseFilter(params: URLSearchParams): FindingFilter {
  const state = params.get("state") ?? "";
  const source = params.get("source") ?? "";
  const player = params.get("player") ?? "";
  const since = params.get("since") ?? "";
  return {
    state: stateValues.has(state) ? (state as FindingState) : "open",
    source: sourceValues.has(source) ? (source as Source) : "",
    world: (params.get("world") ?? "").trim(),
    player: uuidPattern.test(player) ? player.toLowerCase() : "",
    since: /^\d{4}-\d{2}-\d{2}$/.test(since) ? since : "",
  };
}

export function filterParams(filter: FindingFilter): URLSearchParams {
  const params = new URLSearchParams();
  params.set("state", filter.state);
  if (filter.source) params.set("source", filter.source);
  if (filter.world) params.set("world", filter.world);
  if (filter.player) params.set("player", filter.player);
  if (filter.since) params.set("since", filter.since);
  return params;
}

export function sinceInstant(day: string): string | null {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(day)) return null;
  const date = new Date(`${day}T00:00:00Z`);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

export function findingsQuery(filter: FindingFilter, before: number | null, limit: number): string {
  const params = new URLSearchParams();
  params.set("state", filter.state);
  if (filter.source) params.set("source", filter.source);
  if (filter.world) params.set("world", filter.world);
  if (filter.player) params.set("player", filter.player);
  const since = sinceInstant(filter.since);
  if (since) params.set("since", since);
  if (before !== null) params.set("before", String(before));
  params.set("limit", String(Math.max(1, Math.min(100, limit))));
  return params.toString();
}

export function isUuid(value: string): boolean {
  return uuidPattern.test(value);
}

export function matchesState(state: FindingState, verdict: "CONFIRMED" | "FALSE_ALARM" | null): boolean {
  switch (state) {
    case "open":
      return verdict === null;
    case "reviewed":
      return verdict !== null;
    case "confirmed":
      return verdict === "CONFIRMED";
    case "false_alarm":
      return verdict === "FALSE_ALARM";
    default:
      return true;
  }
}
