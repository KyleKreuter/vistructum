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

export function hasFlag(value: number, flag: number): boolean {
  return (value & flag) === flag;
}

export function prepareTrack(recording: Recording): Track {
  const frames = [...recording.frames].sort((a, b) => a.t - b.t);
  const distance = new Float64Array(frames.length);
  for (let i = 1; i < frames.length; i++) {
    distance[i] = distance[i - 1] + Math.hypot(frames[i].x - frames[i - 1].x, frames[i].z - frames[i - 1].z);
  }
  return { player: recording.player, playerName: recording.playerName, frames, distance };
}

export function frameIndexAt(frames: MotionFrame[], time: number): number {
  let low = 0;
  let high = frames.length;
  while (low < high) {
    const mid = (low + high) >> 1;
    if (frames[mid].t <= time) low = mid + 1;
    else high = mid;
  }
  return low - 1;
}

export function wrapDegrees(angle: number): number {
  const wrapped = ((angle + 180) % 360 + 360) % 360 - 180;
  return wrapped === -180 ? 180 : wrapped;
}

export function lerp(a: number, b: number, f: number): number {
  return a + (b - a) * f;
}

export function lerpAngle(a: number, b: number, f: number): number {
  return wrapDegrees(a + wrapDegrees(b - a) * f);
}

export function yawOfDirection(dx: number, dz: number): number {
  return wrapDegrees((Math.atan2(-dx, dz) * 180) / Math.PI);
}

export function bodyYawFor(headYaw: number, dx: number, dz: number, speed: number): number {
  if (speed < 0.3) return headYaw;
  let movement = yawOfDirection(dx, dz);
  if (Math.abs(wrapDegrees(movement - headYaw)) > 100) movement = wrapDegrees(movement + 180);
  const offset = Math.max(-50, Math.min(50, wrapDegrees(movement - headYaw)));
  return wrapDegrees(headYaw + offset);
}

function horizontalVelocity(frames: MotionFrame[], index: number): { dx: number; dz: number; speed: number } {
  const a = frames[Math.max(0, index)];
  const b = frames[Math.min(frames.length - 1, index + 1)];
  const dt = (b.t - a.t) / 1000;
  if (dt <= 0) return { dx: 0, dz: 0, speed: 0 };
  const dx = (b.x - a.x) / dt;
  const dz = (b.z - a.z) / dt;
  return { dx, dz, speed: Math.hypot(dx, dz) };
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
  const index = frameIndexAt(frames, time);
  if (index < 0) return poseFromFrame(track, 0, 0, time);
  if (index >= frames.length - 1) return poseFromFrame(track, frames.length - 1, 0, time);
  const a = frames[index];
  const b = frames[index + 1];
  const f = b.t > a.t ? Math.min(1, Math.max(0, (time - a.t) / (b.t - a.t))) : 0;
  return poseFromFrame(track, index, f, time);
}

function poseFromFrame(track: Track, index: number, f: number, time: number): Pose {
  const { frames, distance } = track;
  const a = frames[index];
  const b = frames[Math.min(frames.length - 1, index + 1)];
  const velocity = horizontalVelocity(frames, index);
  const yaw = lerpAngle(a.yaw, b.yaw, f);
  const moving = index < frames.length - 1 && time <= b.t;
  const speed = moving ? velocity.speed : 0;
  return {
    x: lerp(a.x, b.x, f),
    y: lerp(a.y, b.y, f),
    z: lerp(a.z, b.z, f),
    yaw,
    bodyYaw: bodyYawFor(yaw, velocity.dx, velocity.dz, speed),
    pitch: Math.max(-90, Math.min(90, lerp(a.pitch, b.pitch, f))),
    onGround: hasFlag(a.flags, flags.onGround),
    sneaking: hasFlag(a.flags, flags.sneaking),
    sprinting: hasFlag(a.flags, flags.sprinting),
    swimming: hasFlag(a.flags, flags.swimming),
    gliding: hasFlag(a.flags, flags.gliding),
    swinging: hasFlag(a.flags, flags.swinging),
    swingProgress: swingProgressAt(frames, index, time),
    speed,
    walkDistance: lerp(distance[index], distance[Math.min(frames.length - 1, index + 1)], f),
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
