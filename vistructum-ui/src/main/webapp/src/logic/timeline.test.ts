import { describe, expect, it } from "vitest";
import type { BlockChange } from "@/api/types";
import { advance, appliedCount, buildTimeline, formatClock, markerPositions, nextSpeed, stepTime, tallies } from "./timeline";

const change = (t: number, player = "a", action: "PLACE" | "BREAK" = "PLACE"): BlockChange => ({
  t,
  player,
  playerName: player.toUpperCase(),
  action,
  x: 0,
  y: 0,
  z: 0,
  blockData: "minecraft:stone",
});

const frame = (t: number) => ({ t, x: 0, y: 0, z: 0, yaw: 0, pitch: 0, flags: 1, mainHand: "minecraft:air" });

describe("timeline", () => {
  const timeline = buildTimeline({
    changes: [change(5000), change(3000), change(4000, "b", "BREAK")],
    recordings: [{ player: "a", playerName: "A", frames: [frame(1000), frame(8000)] }],
  });

  it("spans frames and changes and sorts changes", () => {
    expect(timeline.start).toBe(1000);
    expect(timeline.end).toBe(8000);
    expect(timeline.changes.map((c) => c.t)).toEqual([3000, 4000, 5000]);
  });

  it("counts applied changes inclusively", () => {
    expect(appliedCount(timeline, 2999)).toBe(0);
    expect(appliedCount(timeline, 3000)).toBe(1);
    expect(appliedCount(timeline, 4500)).toBe(2);
    expect(appliedCount(timeline, 9000)).toBe(3);
  });

  it("steps one block change at a time", () => {
    expect(stepTime(timeline, 1000, 1)).toBe(3000);
    expect(stepTime(timeline, 3000, 1)).toBe(4000);
    expect(stepTime(timeline, 5000, 1)).toBe(5000);
    expect(stepTime(timeline, 5000, -1)).toBe(4000);
    expect(stepTime(timeline, 4500, -1)).toBe(3000);
    expect(appliedCount(timeline, stepTime(timeline, 3000, -1))).toBe(0);
  });

  it("handles empty evidence", () => {
    const empty = buildTimeline({ changes: [], recordings: [] });
    expect(empty.end - empty.start).toBe(1000);
    expect(stepTime(empty, 0, 1)).toBe(0);
  });

  it("advances and stops at the end", () => {
    expect(advance(timeline, 1000, 100, 2)).toEqual({ time: 1200, finished: false });
    expect(advance(timeline, 7990, 100, 1)).toEqual({ time: 8000, finished: true });
  });

  it("cycles speeds", () => {
    expect(nextSpeed(1, 1)).toBe(2);
    expect(nextSpeed(8, 1)).toBe(8);
    expect(nextSpeed(0.25, -1)).toBe(0.25);
    expect(nextSpeed(1, -1)).toBe(0.5);
  });

  it("places markers and tallies players", () => {
    expect(markerPositions(timeline)).toEqual([2 / 7, 3 / 7, 4 / 7]);
    const counts = tallies(timeline.changes);
    expect(counts.get("a")).toEqual({ player: "a", playerName: "A", placed: 2, broken: 0 });
    expect(counts.get("b")?.broken).toBe(1);
    expect(tallies(timeline.changes, 1).get("b")).toBeUndefined();
  });

  it("formats a clock", () => {
    expect(formatClock(0)).toBe("0:00");
    expect(formatClock(95_400)).toBe("1:35");
  });
});
