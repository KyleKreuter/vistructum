import type { MotionFrame, Recording } from "@/api/types";

export const flags = {
  onGround: 1,
  sneaking: 2,
  sprinting: 4,
  swimming: 8,
  gliding: 16,
  swinging: 32,
} as const;

export const swingDurationMs = 300;
const visibilityGraceMs = 1500;

export interface Track {
  player: string;
  playerName: string;
  frames: MotionFrame[];
  time: Float64Array;
  x: Float64Array;
  y: Float64Array;
  z: Float64Array;
  yaw: Float64Array;
  pitch: Float64Array;
  vx: Float64Array;
  vz: Float64Array;
  run: Int32Array;
  teleport: Uint8Array;
  distance: Float64Array;
}

export interface Pose {
  x: number;
  y: number;
  z: number;
  yaw: number;
  bodyYaw: number;
  pitch: number;
  onGround: boolean;
  sneaking: boolean;
  sprinting: boolean;
  swimming: boolean;
  gliding: boolean;
  swinging: boolean;
  swingProgress: number;
  speed: number;
  walkDistance: number;
  mainHand: string;
}

const runGapMs = 250;
const teleportDistance = 4;
const timeSmoothing = { radius: 4, sigma: 2 };
const positionSmoothing = { radius: 3, sigma: 1.5 };
const heightSmoothing = { radius: 2, sigma: 1 };
const angleSmoothing = { radius: 2, sigma: 1 };
const velocitySpan = 2;

export function hasFlag(value: number, flag: number): boolean {
  return (value & flag) === flag;
}

function smooth(values: ArrayLike<number>, run: Int32Array, { radius, sigma }: { radius: number; sigma: number }): Float64Array {
  const result = new Float64Array(values.length);
  for (let i = 0; i < values.length; i++) {
    let sum = 0;
    let weights = 0;
    for (let j = Math.max(0, i - radius); j <= Math.min(values.length - 1, i + radius); j++) {
      if (run[j] !== run[i]) continue;
      const weight = Math.exp(-((j - i) ** 2) / (2 * sigma * sigma));
      sum += values[j] * weight;
      weights += weight;
    }
    result[i] = sum / weights;
  }
  return result;
}

function unwrapDegrees(angles: number[]): number[] {
  const unwrapped = angles.slice();
  for (let i = 1; i < unwrapped.length; i++) unwrapped[i] = unwrapped[i - 1] + wrapDegrees(angles[i] - angles[i - 1]);
  return unwrapped;
}

export function prepareTrack(recording: Recording): Track {
  const frames = [...recording.frames].sort((a, b) => a.t - b.t);
  const count = frames.length;
  const run = new Int32Array(count);
  const teleport = new Uint8Array(count);
  for (let i = 1; i < count; i++) {
    const jump = Math.hypot(frames[i].x - frames[i - 1].x, frames[i].y - frames[i - 1].y, frames[i].z - frames[i - 1].z);
    teleport[i - 1] = jump > teleportDistance ? 1 : 0;
    run[i] = run[i - 1] + (frames[i].t - frames[i - 1].t > runGapMs || teleport[i - 1] ? 1 : 0);
  }
  const time = smooth(frames.map((frame) => frame.t), run, timeSmoothing);
  for (let i = 1; i < count; i++) {
    if (run[i] === run[i - 1] && time[i] <= time[i - 1]) time[i] = time[i - 1] + 0.001;
  }
  const x = smooth(frames.map((frame) => frame.x), run, positionSmoothing);
  const y = smooth(frames.map((frame) => frame.y), run, heightSmoothing);
  const z = smooth(frames.map((frame) => frame.z), run, positionSmoothing);
  const yaw = smooth(unwrapDegrees(frames.map((frame) => frame.yaw)), run, angleSmoothing);
  const pitch = smooth(frames.map((frame) => frame.pitch), run, angleSmoothing);
  const vx = new Float64Array(count);
  const vz = new Float64Array(count);
  for (let i = 0; i < count; i++) {
    let low = i;
    let high = i;
    while (low > 0 && i - low < velocitySpan && run[low - 1] === run[i]) low--;
    while (high < count - 1 && high - i < velocitySpan && run[high + 1] === run[i]) high++;
    const dt = (time[high] - time[low]) / 1000;
    if (dt <= 0) continue;
    vx[i] = (x[high] - x[low]) / dt;
    vz[i] = (z[high] - z[low]) / dt;
  }
  const distance = new Float64Array(count);
  for (let i = 1; i < count; i++) {
    distance[i] = distance[i - 1] + (teleport[i - 1] ? 0 : Math.hypot(x[i] - x[i - 1], z[i] - z[i - 1]));
  }
  return { player: recording.player, playerName: recording.playerName, frames, time, x, y, z, yaw, pitch, vx, vz, run, teleport, distance };
}

function lastIndexAtOrBefore(count: number, timeOf: (index: number) => number, time: number): number {
  let low = 0;
  let high = count;
  while (low < high) {
    const mid = (low + high) >> 1;
    if (timeOf(mid) <= time) low = mid + 1;
    else high = mid;
  }
  return low - 1;
}

export function frameIndexAt(frames: MotionFrame[], time: number): number {
  return lastIndexAtOrBefore(frames.length, (index) => frames[index].t, time);
}

export function wrapDegrees(angle: number): number {
  const wrapped = ((angle + 180) % 360 + 360) % 360 - 180;
  return wrapped === -180 ? 180 : wrapped;
}

export function lerp(a: number, b: number, f: number): number {
  return a + (b - a) * f;
}

export function yawOfDirection(dx: number, dz: number): number {
  return wrapDegrees((Math.atan2(-dx, dz) * 180) / Math.PI);
}

function smoothstep(edge0: number, edge1: number, value: number): number {
  const f = Math.min(1, Math.max(0, (value - edge0) / (edge1 - edge0)));
  return f * f * (3 - 2 * f);
}

export function bodyYawFor(headYaw: number, dx: number, dz: number, speed: number): number {
  const weight = smoothstep(0.2, 0.6, speed);
  if (weight === 0) return headYaw;
  let movement = yawOfDirection(dx, dz);
  if (Math.abs(wrapDegrees(movement - headYaw)) > 100) movement = wrapDegrees(movement + 180);
  const offset = Math.max(-50, Math.min(50, wrapDegrees(movement - headYaw)));
  return wrapDegrees(headYaw + offset * weight);
}

export function swingProgressAt(frames: MotionFrame[], index: number, time: number): number {
  if (index < 0 || !hasFlag(frames[index].flags, flags.swinging)) return 0;
  let start = index;
  while (start > 0 && hasFlag(frames[start - 1].flags, flags.swinging) && frames[start].t - frames[start - 1].t < swingDurationMs) start--;
  const elapsed = Math.max(0, time - frames[start].t);
  return (elapsed % swingDurationMs) / swingDurationMs;
}

export function poseAt(track: Track, time: number): Pose | null {
  const { frames } = track;
  if (!frames.length) return null;
  if (time < frames[0].t - visibilityGraceMs || time > frames[frames.length - 1].t + visibilityGraceMs) return null;
  const index = lastIndexAtOrBefore(frames.length, (i) => track.time[i], time);
  if (index < 0) return poseFromFrame(track, 0, 0, time);
  if (index >= frames.length - 1) return poseFromFrame(track, frames.length - 1, 0, time);
  const span = track.time[index + 1] - track.time[index];
  const f = track.teleport[index] || span <= 0 ? 0 : Math.min(1, Math.max(0, (time - track.time[index]) / span));
  return poseFromFrame(track, index, f, time);
}

function velocityAt(track: Track, index: number, f: number): { dx: number; dz: number } {
  const next = Math.min(track.frames.length - 1, index + 1);
  if (track.run[next] === track.run[index]) {
    return { dx: lerp(track.vx[index], track.vx[next], f), dz: lerp(track.vz[index], track.vz[next], f) };
  }
  const dt = (track.time[next] - track.time[index]) / 1000;
  if (dt <= 0 || track.teleport[index]) return { dx: 0, dz: 0 };
  return { dx: (track.x[next] - track.x[index]) / dt, dz: (track.z[next] - track.z[index]) / dt };
}

function poseFromFrame(track: Track, index: number, f: number, time: number): Pose {
  const { frames, distance } = track;
  const next = Math.min(frames.length - 1, index + 1);
  const moving = index < frames.length - 1 && time <= track.time[next];
  const velocity = moving ? velocityAt(track, index, f) : { dx: 0, dz: 0 };
  const speed = Math.hypot(velocity.dx, velocity.dz);
  const yaw = wrapDegrees(lerp(track.yaw[index], track.yaw[next], f));
  const frameIndex = Math.max(0, frameIndexAt(frames, time));
  const a = frames[frameIndex];
  return {
    x: lerp(track.x[index], track.x[next], f),
    y: lerp(track.y[index], track.y[next], f),
    z: lerp(track.z[index], track.z[next], f),
    yaw,
    bodyYaw: bodyYawFor(yaw, velocity.dx, velocity.dz, speed),
    pitch: Math.max(-90, Math.min(90, lerp(track.pitch[index], track.pitch[next], f))),
    onGround: hasFlag(a.flags, flags.onGround),
    sneaking: hasFlag(a.flags, flags.sneaking),
    sprinting: hasFlag(a.flags, flags.sprinting),
    swimming: hasFlag(a.flags, flags.swimming),
    gliding: hasFlag(a.flags, flags.gliding),
    swinging: hasFlag(a.flags, flags.swinging),
    swingProgress: swingProgressAt(frames, frameIndex, time),
    speed,
    walkDistance: lerp(distance[index], distance[next], f),
    mainHand: a.mainHand,
  };
}

export interface JointPose {
  rx: number;
  ry: number;
  rz: number;
  px: number;
  py: number;
  pz: number;
}

export interface Skeleton {
  head: JointPose;
  body: JointPose;
  rightArm: JointPose;
  leftArm: JointPose;
  rightLeg: JointPose;
  leftLeg: JointPose;
  rotationY: number;
}

const joint = (px: number, py: number, pz: number): JointPose => ({ rx: 0, ry: 0, rz: 0, px, py, pz });

export function restSkeleton(): Skeleton {
  return {
    head: joint(0, 0, 0),
    body: joint(0, -6, 0),
    rightArm: joint(-5, -2, 0),
    leftArm: joint(5, -2, 0),
    rightLeg: joint(-1.9, -12, -0.1),
    leftLeg: joint(1.9, -12, -0.1),
    rotationY: 0,
  };
}

const radians = (degrees: number) => (degrees * Math.PI) / 180;

export function skeletonFor(pose: Pose): Skeleton {
  const skeleton = restSkeleton();
  skeleton.rotationY = -radians(pose.bodyYaw);
  skeleton.head.rx = radians(pose.pitch);
  skeleton.head.ry = -radians(wrapDegrees(pose.yaw - pose.bodyYaw));
  const amplitude = Math.min(1, pose.speed / 4.3) * (pose.sprinting ? 1.1 : 0.8);
  const phase = pose.walkDistance * 2.4;
  const swing = Math.sin(phase) * amplitude;
  skeleton.rightLeg.rx = swing;
  skeleton.leftLeg.rx = -swing;
  skeleton.rightArm.rx = -swing * 0.85;
  skeleton.leftArm.rx = swing * 0.85;
  skeleton.rightArm.rz = 0.05;
  skeleton.leftArm.rz = -0.05;
  if (!pose.onGround && !pose.swimming && !pose.gliding) {
    skeleton.rightLeg.rx += 0.25;
    skeleton.leftLeg.rx -= 0.15;
    skeleton.rightArm.rz += 0.15;
    skeleton.leftArm.rz -= 0.15;
  }
  if (pose.sneaking) {
    skeleton.body.rx = 0.4538;
    skeleton.body.py = -8.1037;
    skeleton.body.pz = -2.1244;
    skeleton.head.py = -3.6183;
    skeleton.rightArm.rx += 0.4104;
    skeleton.leftArm.rx += 0.4104;
    skeleton.rightArm.py = -4.5394;
    skeleton.leftArm.py = -4.5394;
    skeleton.rightArm.pz = 0.1683;
    skeleton.leftArm.pz = 0.1683;
    skeleton.rightArm.rz = -0.1;
    skeleton.leftArm.rz = 0.1;
    skeleton.rightLeg.pz = -3.45;
    skeleton.leftLeg.pz = -3.45;
  }
  if (pose.swinging) {
    const s = Math.sin(pose.swingProgress * Math.PI);
    skeleton.rightArm.rx = -0.4 - 1.1 * s + (pose.sneaking ? 0.4104 : 0);
    skeleton.rightArm.rz = 0.1 + 0.35 * Math.sin(pose.swingProgress * Math.PI * 2) * 0.5;
    skeleton.body.ry = -0.12 * s;
  }
  return skeleton;
}

export function eyeHeight(pose: Pick<Pose, "sneaking">): number {
  return pose.sneaking ? 1.27 : 1.62;
}

export function followCameraOffset(pose: Pick<Pose, "yaw" | "sneaking">, distance = 5, height = 2.2): [number, number, number] {
  const yaw = radians(pose.yaw);
  const forwardX = -Math.sin(yaw);
  const forwardZ = Math.cos(yaw);
  return [-forwardX * distance, eyeHeight(pose) + height, -forwardZ * distance];
}
