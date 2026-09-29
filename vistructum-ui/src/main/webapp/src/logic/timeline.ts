import type { BlockChange, Evidence } from "@/api/types";

export interface Timeline {
  start: number;
  end: number;
  changes: BlockChange[];
}

export const speeds = [0.25, 0.5, 1, 2, 4, 8] as const;

export function buildTimeline(evidence: Pick<Evidence, "changes" | "recordings">): Timeline {
  const changes = [...evidence.changes].sort((a, b) => a.t - b.t);
  let start = Number.POSITIVE_INFINITY;
  let end = Number.NEGATIVE_INFINITY;
  for (const change of changes) {
    start = Math.min(start, change.t);
    end = Math.max(end, change.t);
  }
  for (const recording of evidence.recordings) {
    for (const frame of recording.frames) {
      start = Math.min(start, frame.t);
      end = Math.max(end, frame.t);
    }
  }
  if (!Number.isFinite(start)) return { start: 0, end: 1000, changes };
  if (end - start < 1000) end = start + 1000;
  return { start, end, changes };
}

export function appliedCount(timeline: Timeline, time: number): number {
  let low = 0;
  let high = timeline.changes.length;
  while (low < high) {
    const mid = (low + high) >> 1;
    if (timeline.changes[mid].t <= time) low = mid + 1;
    else high = mid;
  }
  return low;
}

export function stepTime(timeline: Timeline, time: number, delta: 1 | -1): number {
  const { changes } = timeline;
  if (!changes.length) return time;
  const applied = appliedCount(timeline, time);
  if (delta > 0) return applied < changes.length ? changes[applied].t : time;
  if (applied <= 1) return Math.min(timeline.start, changes[0].t - 1);
  return changes[applied - 2].t;
}

export function clampTime(timeline: Timeline, time: number): number {
  return Math.min(timeline.end, Math.max(timeline.start, time));
}

export function advance(timeline: Timeline, time: number, elapsedMs: number, speed: number): { time: number; finished: boolean } {
  const next = time + elapsedMs * speed;
  if (next >= timeline.end) return { time: timeline.end, finished: true };
  return { time: next, finished: false };
}

export function nextSpeed(current: number, delta: 1 | -1): number {
  const index = speeds.findIndex((speed) => speed >= current);
  const base = index < 0 ? speeds.length - 1 : index;
  const target = Math.min(speeds.length - 1, Math.max(0, base + delta));
  return speeds[target];
}

export function markerPositions(timeline: Timeline): number[] {
  const span = timeline.end - timeline.start || 1;
  return timeline.changes.map((change) => (change.t - timeline.start) / span);
}

export function formatClock(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000));
  const minutes = Math.floor(total / 60);
  const seconds = total % 60;
  return `${minutes}:${seconds.toString().padStart(2, "0")}`;
}

export interface PlayerTally {
  player: string;
  playerName: string;
  placed: number;
  broken: number;
}

export function tallies(changes: BlockChange[], upTo = changes.length): Map<string, PlayerTally> {
  const result = new Map<string, PlayerTally>();
  for (let i = 0; i < Math.min(upTo, changes.length); i++) {
    const change = changes[i];
    const tally = result.get(change.player) ?? { player: change.player, playerName: change.playerName, placed: 0, broken: 0 };
    if (change.action === "PLACE") tally.placed++;
    else tally.broken++;
    result.set(change.player, tally);
  }
  return result;
}
