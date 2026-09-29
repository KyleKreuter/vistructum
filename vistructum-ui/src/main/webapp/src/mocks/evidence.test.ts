import { describe, expect, it } from "vitest";
import { appliedCount, buildTimeline } from "@/logic/timeline";
import { flags, poseAt, prepareTrack } from "@/logic/motion";
import { dynamicStates, indexVolume } from "@/logic/volume";
import { builder, findingBox, relativeEvidence, syntheticEvidence, watcher } from "./evidence";

describe("synthetic live-check evidence", () => {
  const evidence = syntheticEvidence(7);
  const timeline = buildTimeline(evidence);

  it("places about forty blocks over about ninety seconds", () => {
    const places = evidence.changes.filter((change) => change.action === "PLACE");
    expect(places.length).toBeGreaterThanOrEqual(38);
    expect(places.length).toBeLessThanOrEqual(45);
    expect(evidence.changes.some((change) => change.action === "BREAK")).toBe(true);
    const span = timeline.changes[timeline.changes.length - 1].t - timeline.changes[0].t;
    expect(span).toBeGreaterThan(80_000);
    expect(span).toBeLessThan(100_000);
    expect(timeline.changes[0].t - timeline.start).toBeGreaterThanOrEqual(25_000);
  });

  it("records a builder and a watcher with jumps and sneaking", () => {
    expect(evidence.recordings.map((recording) => recording.player)).toEqual([builder.uuid, watcher.uuid]);
    const builderFrames = evidence.recordings[0].frames;
    expect(builderFrames.some((frame) => (frame.flags & flags.onGround) === 0)).toBe(true);
    expect(builderFrames.some((frame) => (frame.flags & flags.sneaking) !== 0)).toBe(true);
    expect(builderFrames.some((frame) => (frame.flags & flags.swinging) !== 0)).toBe(true);
    expect(evidence.changes.every((change) => change.player === builder.uuid)).toBe(true);
  });

  it("keeps every change inside the before volume", () => {
    const indexed = indexVolume(evidence.before, timeline.changes);
    expect([...indexed.changeCells].every((cell) => cell >= 0)).toBe(true);
    const final = dynamicStates(indexed, appliedCount(timeline, timeline.end));
    expect([...final].filter((state) => indexed.states[state].includes("black_concrete"))).toHaveLength(25);
    for (const change of evidence.changes) {
      if (change.x === findingBox.maxX + 2) continue;
      expect(change.x).toBeGreaterThanOrEqual(findingBox.minX);
      expect(change.x).toBeLessThanOrEqual(findingBox.maxX);
    }
  });

  it("interpolates the builder next to the blocks it places", () => {
    const track = prepareTrack(evidence.recordings[0]);
    for (const change of timeline.changes) {
      const pose = poseAt(track, change.t);
      expect(pose).not.toBeNull();
      expect(Math.hypot((pose?.x ?? 0) - change.x - 0.5, (pose?.z ?? 0) - change.z - 0.5)).toBeLessThan(6.5);
    }
  });

  it("makes coordinates relative for the public page", () => {
    const relative = relativeEvidence(evidence);
    expect(relative.before.minX).toBe(0);
    expect(relative.changes[0].x).toBe(evidence.changes[0].x - evidence.before.minX);
    expect(relative.recordings[1].frames[0].z).toBeCloseTo(evidence.recordings[1].frames[0].z - evidence.before.minZ);
  });
});
