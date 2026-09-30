import { describe, expect, it } from "vitest";
import type { BlockChange } from "@/api/types";
import { flags, hasFlag, poseAt, prepareTrack } from "./motion";
import { reconstructRecordings } from "./reconstruct";

const change = (t: number, x: number, y: number, z: number, extra: Partial<BlockChange> = {}): BlockChange => ({
  t,
  player: "p1",
  playerName: "Builder",
  action: "PLACE",
  x,
  y,
  z,
  blockData: "minecraft:oak_stairs[facing=north]",
  ...extra,
});

describe("reconstructRecordings", () => {
  it("creates one recording per player without a recording", () => {
    const recordings = reconstructRecordings(
      [change(1000, 0, 64, 0), change(2000, 1, 64, 0, { player: "p2", playerName: "Other" }), change(3000, 2, 64, 0, { player: "p3" })],
      new Set(["p3"]),
    );
    expect(recordings.map((recording) => recording.player)).toEqual(["p1", "p2"]);
    expect(recordings[1].playerName).toBe("Other");
  });

  it("swings at every change while holding the placed material", () => {
    const [recording] = reconstructRecordings([change(1000, 0, 64, 0), change(5000, 1, 64, 0, { blockData: "minecraft:stone" })]);
    const swings = recording.frames.filter((frame) => hasFlag(frame.flags, flags.swinging));
    expect(swings.map((frame) => frame.t)).toEqual([1000, 5000]);
    expect(swings.map((frame) => frame.mainHand)).toEqual(["minecraft:oak_stairs", "minecraft:stone"]);
  });

  it("stands within reach and looks at the block", () => {
    const [recording] = reconstructRecordings([change(1000, 10, 64, 10)]);
    const swing = recording.frames.find((frame) => frame.t === 1000);
    if (!swing) throw new Error("missing swing");
    const flat = Math.hypot(swing.x - 10.5, swing.z - 10.5);
    expect(flat).toBeGreaterThanOrEqual(1);
    expect(Math.hypot(flat, 64.5 - (swing.y + 1.62))).toBeLessThanOrEqual(4.5);
    const pose = poseAt(prepareTrack(recording), 1000);
    expect(pose?.pitch).toBeGreaterThan(0);
  });

  it("keeps its position while the next block is within reach", () => {
    const [recording] = reconstructRecordings([change(1000, 0, 64, 0), change(4000, 1, 64, 0)]);
    const positions = new Set(recording.frames.map((frame) => `${frame.x},${frame.z}`));
    expect(positions.size).toBe(1);
  });

  it("walks to distant blocks without teleporting", () => {
    const [recording] = reconstructRecordings([change(1000, 0, 64, 0), change(10_000, 30, 64, 0)]);
    const frames = recording.frames;
    for (let i = 1; i < frames.length; i++) {
      expect(frames[i].t).toBeGreaterThan(frames[i - 1].t);
      expect(Math.hypot(frames[i].x - frames[i - 1].x, frames[i].y - frames[i - 1].y, frames[i].z - frames[i - 1].z)).toBeLessThanOrEqual(4);
    }
    const last = frames.find((frame) => frame.t === 10_000);
    if (!last) throw new Error("missing swing");
    expect(Math.hypot(last.x - 30.5, last.z - 0.5)).toBeLessThanOrEqual(4.5);
  });

  it("groups simultaneous changes into one swing", () => {
    const [recording] = reconstructRecordings([change(1000, 0, 64, 0), change(1000, 1, 64, 0), change(1020, 0, 65, 0)]);
    expect(recording.frames.filter((frame) => hasFlag(frame.flags, flags.swinging))).toHaveLength(1);
  });

  it("stands on top of broken blocks", () => {
    const [recording] = reconstructRecordings([change(1000, 0, 63, 0, { action: "BREAK", blockData: "minecraft:air" })]);
    expect(recording.frames[0].y).toBe(64);
    expect(recording.frames[0].mainHand).toBe("minecraft:air");
  });
});
