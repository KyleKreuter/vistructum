import { describe, expect, it } from "vitest";
import type { MotionFrame } from "@/api/types";
import { bodyYawFor, flags, followCameraOffset, frameIndexAt, poseAt, prepareTrack, skeletonFor, swingProgressAt, wrapDegrees, yawOfDirection } from "./motion";

const frame = (t: number, x: number, z: number, extra: Partial<MotionFrame> = {}): MotionFrame => ({
  t,
  x,
  y: 64,
  z,
  yaw: 0,
  pitch: 0,
  flags: flags.onGround,
  mainHand: "minecraft:air",
  ...extra,
});

describe("angles", () => {
  it("wraps degrees", () => {
    expect(wrapDegrees(190)).toBe(-170);
    expect(wrapDegrees(-190)).toBe(170);
    expect(wrapDegrees(180)).toBe(180);
    expect(wrapDegrees(-180)).toBe(180);
  });

  it("uses the Minecraft yaw convention", () => {
    expect(yawOfDirection(0, 1)).toBeCloseTo(0);
    expect(yawOfDirection(-1, 0)).toBeCloseTo(90);
    expect(yawOfDirection(1, 0)).toBeCloseTo(-90);
    expect(Math.abs(yawOfDirection(0, -1))).toBeCloseTo(180);
  });

  it("turns the body towards the movement within limits", () => {
    expect(bodyYawFor(0, 0, 1, 0.1)).toBe(0);
    expect(bodyYawFor(0, -1, 0, 4)).toBe(50);
    expect(bodyYawFor(0, 0, -1, 4)).toBe(0);
  });
});

describe("pose interpolation", () => {
  const track = prepareTrack({
    player: "p",
    playerName: "P",
    frames: [
      frame(1000, 0, 0),
      frame(2000, 4, 0, { yaw: 90 }),
      frame(3000, 4, 3, { yaw: 90, flags: flags.sneaking | flags.onGround }),
      frame(3100, 4, 3, { flags: flags.swinging }),
      frame(3200, 4, 3, { flags: flags.swinging }),
    ],
  });

  it("finds frame indices", () => {
    expect(frameIndexAt(track.frames, 999)).toBe(-1);
    expect(frameIndexAt(track.frames, 1000)).toBe(0);
    expect(frameIndexAt(track.frames, 2500)).toBe(1);
    expect(frameIndexAt(track.frames, 5000)).toBe(4);
  });

  it("accumulates walked distance", () => {
    [0, 4, 7, 7, 7].forEach((expected, index) => expect(track.distance[index]).toBeCloseTo(expected));
  });

  it("interpolates position, yaw and speed", () => {
    const pose = poseAt(track, 1500);
    expect(pose).not.toBeNull();
    expect(pose?.x).toBeCloseTo(2);
    expect(pose?.yaw).toBeCloseTo(45);
    expect(pose?.speed).toBeCloseTo(4);
    expect(pose?.walkDistance).toBeCloseTo(2);
    expect(pose?.onGround).toBe(true);
  });

  it("takes flags from the earlier frame", () => {
    expect(poseAt(track, 3050)?.sneaking).toBe(true);
    expect(poseAt(track, 3150)?.swinging).toBe(true);
  });

  it("hides players outside their recording", () => {
    expect(poseAt(track, -1000)).toBeNull();
    expect(poseAt(track, 10000)).toBeNull();
    expect(poseAt(track, 3500)?.speed).toBe(0);
  });

  it("measures swing progress from the start of the swing", () => {
    expect(swingProgressAt(track.frames, 3, 3150)).toBeCloseTo(50 / 300);
    expect(swingProgressAt(track.frames, 4, 3250)).toBeCloseTo(150 / 300);
    expect(swingProgressAt(track.frames, 1, 2500)).toBe(0);
  });
});

describe("smoothing", () => {
  const jittered = prepareTrack({
    player: "p",
    playerName: "P",
    frames: Array.from({ length: 40 }, (_, i) => frame(i * 50 + (i % 2 ? 12 : -12), i * 0.2 + (i % 3 === 0 ? 0.08 : 0), 0)),
  });

  it("keeps the walking speed steady despite tick jitter", () => {
    const speeds = Array.from({ length: 60 }, (_, i) => poseAt(jittered, 400 + i * 16)?.speed ?? 0);
    expect(Math.max(...speeds) - Math.min(...speeds)).toBeLessThan(0.6);
    speeds.forEach((speed) => expect(speed).toBeCloseTo(4, 0));
  });

  it("moves forward monotonically", () => {
    const xs = Array.from({ length: 60 }, (_, i) => poseAt(jittered, 400 + i * 16)?.x ?? 0);
    xs.slice(1).forEach((x, i) => expect(x).toBeGreaterThanOrEqual(xs[i]));
  });

  it("snaps across teleports instead of sliding", () => {
    const teleported = prepareTrack({ player: "p", playerName: "P", frames: [frame(0, 0, 0), frame(50, 0.2, 0), frame(100, 30, 0), frame(150, 30.2, 0)] });
    expect(poseAt(teleported, 75)?.x).toBeLessThan(1);
    expect(teleported.distance[3]).toBeLessThan(1);
  });

  it("blends the body towards the head yaw when slowing down", () => {
    expect(bodyYawFor(0, -1, 0, 0.4)).toBeCloseTo(25);
  });
});

describe("skeleton", () => {
  const base = {
    x: 0,
    y: 0,
    z: 0,
    yaw: 30,
    bodyYaw: 10,
    pitch: 45,
    onGround: true,
    sneaking: false,
    sprinting: false,
    swimming: false,
    gliding: false,
    swinging: false,
    swingProgress: 0,
    speed: 0,
    walkDistance: 0,
    mainHand: "minecraft:air",
  };

  it("stands still at rest", () => {
    const skeleton = skeletonFor(base);
    expect(skeleton.rightLeg.rx).toBeCloseTo(0);
    expect(skeleton.head.rx).toBeCloseTo(Math.PI / 4);
    expect(skeleton.head.ry).toBeCloseTo((-20 * Math.PI) / 180);
    expect(skeleton.rotationY).toBeCloseTo((-10 * Math.PI) / 180);
  });

  it("swings legs in opposition while walking", () => {
    const skeleton = skeletonFor({ ...base, speed: 4.3, walkDistance: 0.6 });
    expect(skeleton.rightLeg.rx).toBeCloseTo(-skeleton.leftLeg.rx);
    expect(Math.abs(skeleton.rightLeg.rx)).toBeGreaterThan(0.3);
    expect(Math.sign(skeleton.rightArm.rx)).toBe(-Math.sign(skeleton.rightLeg.rx));
  });

  it("leans forward when sneaking and raises the arm when swinging", () => {
    expect(skeletonFor({ ...base, sneaking: true }).body.rx).toBeGreaterThan(0.4);
    expect(skeletonFor({ ...base, swinging: true, swingProgress: 0.5 }).rightArm.rx).toBeLessThan(-1.2);
  });

  it("places the follow camera behind the player", () => {
    const [x, y, z] = followCameraOffset({ yaw: 0, sneaking: false }, 5, 2);
    expect(x).toBeCloseTo(0);
    expect(z).toBeCloseTo(-5);
    expect(y).toBeCloseTo(3.62);
  });
});
