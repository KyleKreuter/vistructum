import type { BlockChange, Evidence, MotionFrame, Recording } from "@/api/types";
import { flags, yawOfDirection } from "@/logic/motion";

export const builder = { uuid: "5f1c7a2e-8b4d-4e39-9a61-2c7d3b0e9f14", name: "Blockwright" };
export const watcher = { uuid: "a83e0d57-1c2b-4f6a-8e90-7b5d4c3a2f18", name: "Onlooker_7" };

export const pattern = [
  "#..####",
  "#..#...",
  "#..#...",
  "#######",
  "...#..#",
  "...#..#",
  "####..#",
];

const centre = { x: 1203, y: 64, z: -341 };
const margin = 4;
const frameStep = 100;
const leadMs = 30000;
const walkSpeed = 4.3;
const sneakSpeed = 1.3;

function random(seed: number): () => number {
  let state = seed >>> 0;
  return () => {
    state = (state + 0x6d2b79f5) >>> 0;
    let t = state;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

interface Target {
  x: number;
  y: number;
  z: number;
  block: string;
  action: "PLACE" | "BREAK";
}

function buildTargets(): Target[] {
  const symbol: Target[] = [];
  const filler: Target[] = [];
  pattern.forEach((line, row) => {
    [...line].forEach((cell, col) => {
      const x = centre.x - 3 + col;
      const z = centre.z - 3 + row;
      if (cell === "#") symbol.push({ x, y: centre.y, z, block: "minecraft:black_concrete", action: "PLACE" });
      else if ((row + col) % 3 !== 1) filler.push({ x, y: centre.y, z, block: "minecraft:white_concrete", action: "PLACE" });
    });
  });
  const angle = (t: Target) => Math.atan2(t.z - centre.z, t.x - centre.x);
  const ordered = [...symbol.sort((a, b) => angle(a) - angle(b)), ...filler.sort((a, b) => angle(a) - angle(b))];
  const mistake = { x: centre.x + 5, y: centre.y, z: centre.z + 1, block: "minecraft:black_concrete", action: "PLACE" as const };
  ordered.splice(9, 0, mistake, { ...mistake, action: "BREAK", block: "minecraft:air" });
  return ordered;
}

export const volumeOrigin = { minX: centre.x - 3 - margin, minY: centre.y - margin, minZ: centre.z - 3 - margin };
export const volumeSize = { sizeX: 7 + 2 * margin, sizeY: 1 + 2 * margin, sizeZ: 7 + 2 * margin };
export const findingBox = {
  minX: centre.x - 3,
  minY: centre.y,
  minZ: centre.z - 3,
  maxX: centre.x + 3,
  maxY: centre.y,
  maxZ: centre.z + 3,
};

function beforeVolume(): Evidence["before"] {
  const palette = [
    "minecraft:air",
    "minecraft:stone",
    "minecraft:dirt",
    "minecraft:grass_block[snowy=false]",
    "minecraft:short_grass",
    "minecraft:oak_log[axis=y]",
    "minecraft:oak_leaves[distance=1,persistent=false,waterlogged=false]",
    "minecraft:water[level=0]",
    "minecraft:glass",
    "minecraft:stone_slab[type=bottom,waterlogged=false]",
    "minecraft:poppy",
    "minecraft:gravel",
  ];
  const { sizeX, sizeY, sizeZ } = volumeSize;
  const cells = new Array<number>(sizeX * sizeY * sizeZ).fill(0);
  const set = (dx: number, dy: number, dz: number, state: number) => {
    if (dx < 0 || dy < 0 || dz < 0 || dx >= sizeX || dy >= sizeY || dz >= sizeZ) return;
    cells[(dy * sizeZ + dz) * sizeX + dx] = state;
  };
  const surface = margin - 1;
  const rnd = random(7);
  for (let dx = 0; dx < sizeX; dx++) {
    for (let dz = 0; dz < sizeZ; dz++) {
      for (let dy = 0; dy < surface; dy++) set(dx, dy, dz, dy < surface - 2 ? 1 : 2);
      set(dx, surface, dz, 3);
      if (rnd() < 0.08) set(dx, surface + 1, dz, rnd() < 0.8 ? 4 : 10);
    }
  }
  for (let dx = 0; dx < 3; dx++) for (let dz = sizeZ - 3; dz < sizeZ; dz++) set(dx, surface, dz, 7);
  set(3, surface, sizeZ - 1, 11);
  set(3, surface, sizeZ - 2, 11);
  for (let dy = surface + 1; dy < surface + 5; dy++) set(sizeX - 2, dy, 1, 5);
  for (let dx = sizeX - 4; dx < sizeX; dx++) {
    for (let dz = 0; dz < 4; dz++) {
      for (let dy = surface + 4; dy < surface + 6; dy++) {
        if (!(dx === sizeX - 2 && dz === 1 && dy === surface + 4)) set(dx, dy, dz, 6);
      }
    }
  }
  set(1, surface + 1, 2, 8);
  set(1, surface + 1, 3, 8);
  set(2, surface + 1, 2, 9);
  set(sizeX - 2, surface + 1, sizeZ - 3, 9);
  const { minX, minY, minZ } = volumeOrigin;
  const clearFrom = { x: centre.x - 3 - minX, z: centre.z - 3 - minZ };
  for (let dx = clearFrom.x - 1; dx <= clearFrom.x + 7; dx++) {
    for (let dz = clearFrom.z - 1; dz <= clearFrom.z + 7; dz++) set(dx, surface + 1, dz, 0);
  }
  return { minX, minY, minZ, sizeX, sizeY, sizeZ, palette, cells };
}

interface Waypoint {
  x: number;
  z: number;
}

function standingSpot(target: Target, previous: Waypoint): Waypoint {
  const dx = target.x + 0.5 - centre.x - 0.5;
  const dz = target.z + 0.5 - centre.z - 0.5;
  const length = Math.hypot(dx, dz) || 1;
  const radius = 5.2;
  const ideal = { x: centre.x + 0.5 + (dx / length) * radius, z: centre.z + 0.5 + (dz / length) * radius };
  const lean = 0.25;
  return { x: ideal.x * (1 - lean) + previous.x * lean, z: ideal.z * (1 - lean) + previous.z * lean };
}

function lookAt(from: { x: number; y: number; z: number }, to: { x: number; y: number; z: number }): { yaw: number; pitch: number } {
  const dx = to.x - from.x;
  const dy = to.y - from.y;
  const dz = to.z - from.z;
  const yaw = yawOfDirection(dx, dz);
  const pitch = (-Math.atan2(dy, Math.hypot(dx, dz)) * 180) / Math.PI;
  return { yaw, pitch };
}

interface BuilderPlan {
  frames: MotionFrame[];
  changes: BlockChange[];
}

function planBuilder(start: number, targets: Target[]): BuilderPlan {
  const rnd = random(42);
  const frames: MotionFrame[] = [];
  const changes: BlockChange[] = [];
  const buildEnd = start + leadMs + 88000;
  let t = start;
  let position = { x: volumeOrigin.minX + 0.5, y: centre.y, z: volumeOrigin.minZ + volumeSize.sizeZ - 0.5 };
  let yaw = -135;
  let pitch = 5;
  let hand = "minecraft:air";
  let jumpStart = -1;
  const push = (extra: number, sneaking: boolean) => {
    let y = centre.y;
    let onGround = true;
    if (jumpStart >= 0) {
      const s = (t - jumpStart) / 1000 + frameStep / 1000;
      const height = 5 * s - 9.4 * s * s;
      if (height > 0) {
        y += height;
        onGround = false;
      } else jumpStart = -1;
    }
    position = { ...position, y };
    const value = (onGround ? flags.onGround : 0) | (sneaking ? flags.sneaking : 0) | extra;
    frames.push({ t, x: position.x, y, z: position.z, yaw, pitch, flags: value, mainHand: hand });
    t += frameStep;
  };
  const walkTo = (goal: Waypoint, sneaking: boolean, allowJump: boolean, focus?: { x: number; y: number; z: number }) => {
    const speed = sneaking ? sneakSpeed : walkSpeed;
    let guard = 0;
    while (Math.hypot(goal.x - position.x, goal.z - position.z) > 0.05 && guard++ < 400) {
      const dx = goal.x - position.x;
      const dz = goal.z - position.z;
      const distance = Math.hypot(dx, dz);
      const stepLength = Math.min(distance, (speed * frameStep) / 1000);
      position = { ...position, x: position.x + (dx / distance) * stepLength, z: position.z + (dz / distance) * stepLength };
      const target = focus ? lookAt({ ...position, y: position.y + 1.62 }, focus) : { yaw: yawOfDirection(dx, dz), pitch: 8 };
      yaw = yaw + Math.max(-40, Math.min(40, ((target.yaw - yaw + 540) % 360) - 180));
      pitch = pitch + (target.pitch - pitch) * 0.5;
      if (allowJump && jumpStart < 0 && rnd() < 0.035) jumpStart = t;
      push(0, sneaking);
    }
  };
  const idle = (duration: number, sneaking: boolean, focus?: { x: number; y: number; z: number }) => {
    const until = t + duration;
    while (t < until) {
      if (focus) {
        const target = lookAt({ ...position, y: position.y + 1.62 }, focus);
        yaw = yaw + Math.max(-30, Math.min(30, ((target.yaw - yaw + 540) % 360) - 180));
        pitch = pitch + (target.pitch - pitch) * 0.6;
      }
      push(0, sneaking);
    }
  };
  walkTo({ x: centre.x - 6.5, z: centre.z + 5.5 }, false, true);
  idle(1500, false, { x: centre.x + 0.5, y: centre.y, z: centre.z + 0.5 });
  walkTo({ x: centre.x + 6.5, z: centre.z + 6 }, false, true);
  walkTo({ x: centre.x + 6, z: centre.z - 6 }, false, true);
  walkTo({ x: centre.x - 5.5, z: centre.z - 5.5 }, false, true);
  if (t < start + leadMs - 400) idle(start + leadMs - 400 - t, false, { x: centre.x + 0.5, y: centre.y, z: centre.z + 0.5 });
  const slot = (buildEnd - t) / targets.length;
  targets.forEach((target, index) => {
    const sneaking = index >= 12 && index < 22;
    const slotEnd = start + leadMs - 400 + slot * (index + 1);
    const block = { x: target.x + 0.5, y: target.y + 0.5, z: target.z + 0.5 };
    hand = target.action === "PLACE" ? target.block : hand;
    walkTo(standingSpot(target, position), sneaking, !sneaking && index % 5 === 2, block);
    const aim = Math.max(200, slotEnd - t - 500);
    idle(aim, sneaking, block);
    const at = t;
    changes.push({
      t: at,
      player: builder.uuid,
      playerName: builder.name,
      action: target.action,
      x: target.x,
      y: target.y,
      z: target.z,
      blockData: target.action === "PLACE" ? target.block : "minecraft:air",
    });
    for (let i = 0; i < 3; i++) push(flags.swinging, sneaking);
  });
  hand = "minecraft:black_concrete";
  walkTo({ x: centre.x - 7, z: centre.z + 2 }, false, false);
  idle(3000, false, { x: centre.x + 0.5, y: centre.y, z: centre.z + 0.5 });
  return { frames, changes };
}

function planWatcher(start: number, end: number, builderFrames: MotionFrame[]): MotionFrame[] {
  const frames: MotionFrame[] = [];
  let position = { x: centre.x + 7.5, y: centre.y, z: centre.z - 6.5 };
  let yaw = 45;
  let pitch = 10;
  let jumpStart = -1;
  let index = 0;
  for (let t = start + 2000; t <= end; t += frameStep) {
    while (index < builderFrames.length - 1 && builderFrames[index].t < t) index++;
    const focus = builderFrames[index];
    const elapsed = t - start;
    let moving = false;
    if (elapsed > 55000 && elapsed < 58000) {
      position = { ...position, x: position.x - 0.3, z: position.z + 0.1 };
      moving = true;
    }
    if (elapsed > 95000 && elapsed < 97500) {
      position = { ...position, z: position.z + 0.33 };
      moving = true;
    }
    if (elapsed > 70000 && elapsed < 70100) jumpStart = t;
    let y = centre.y;
    let onGround = true;
    if (jumpStart >= 0) {
      const s = (t - jumpStart) / 1000 + frameStep / 1000;
      const height = 5 * s - 9.4 * s * s;
      if (height > 0) {
        y += height;
        onGround = false;
      } else jumpStart = -1;
    }
    const sneaking = elapsed > 80000 && elapsed < 86000;
    const target = lookAt({ x: position.x, y: y + 1.62, z: position.z }, { x: focus.x, y: focus.y + 1.4, z: focus.z });
    const delta = ((target.yaw - yaw + 540) % 360) - 180;
    yaw = yaw + delta * (moving ? 0.6 : 0.25);
    pitch = pitch + (target.pitch - pitch) * 0.3;
    frames.push({
      t,
      x: position.x,
      y,
      z: position.z,
      yaw: ((yaw + 540) % 360) - 180,
      pitch,
      flags: (onGround ? flags.onGround : 0) | (sneaking ? flags.sneaking : 0),
      mainHand: "minecraft:air",
    });
  }
  return frames;
}

export function syntheticEvidence(findingId: number, start = Date.UTC(2026, 8, 28, 19, 42, 10)): Evidence {
  const targets = buildTargets();
  const plan = planBuilder(start, targets);
  const end = plan.frames[plan.frames.length - 1].t;
  const recordings: Recording[] = [
    { player: builder.uuid, playerName: builder.name, frames: plan.frames },
    { player: watcher.uuid, playerName: watcher.name, frames: planWatcher(start, end, plan.frames) },
  ];
  return { findingId, before: beforeVolume(), changes: plan.changes, recordings };
}

export function relativeEvidence(evidence: Evidence): Evidence {
  const { minX, minY, minZ } = evidence.before;
  return {
    ...evidence,
    before: { ...evidence.before, minX: 0, minY: 0, minZ: 0 },
    changes: evidence.changes.map((change) => ({ ...change, x: change.x - minX, y: change.y - minY, z: change.z - minZ })),
    recordings: evidence.recordings.map((recording) => ({
      ...recording,
      frames: recording.frames.map((frame) => ({ ...frame, x: frame.x - minX, y: frame.y - minY, z: frame.z - minZ })),
    })),
  };
}
