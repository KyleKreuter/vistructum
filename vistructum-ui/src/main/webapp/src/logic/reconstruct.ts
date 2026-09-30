import type { BlockChange, MotionFrame, Recording } from "@/api/types";
import { flags, swingDurationMs, yawOfDirection } from "./motion";

interface Point {
  x: number;
  y: number;
  z: number;
}

interface Burst {
  t: number;
  target: Point;
  feetY: number;
  hand: string;
}

const burstWindowMs = 50;
const reachBlocks = 4.5;
const standDistance = 2.5;
const minStandDistance = 1;
const eyeHeight = 1.62;
const walkSpeed = 4.3;
const walkStepBlocks = 0.8;
const arrivalLeadMs = 150;
const air = "minecraft:air";

function materialOf(blockData: string): string {
  const bracket = blockData.indexOf("[");
  return (bracket < 0 ? blockData : blockData.slice(0, bracket)).toLowerCase();
}

function burstsOf(changes: BlockChange[], initialHand: string): Burst[] {
  const bursts: Burst[] = [];
  let group: BlockChange[] = [];
  let hand = initialHand;
  const flush = () => {
    if (!group.length) return;
    const count = group.length;
    const target = {
      x: group.reduce((sum, change) => sum + change.x + 0.5, 0) / count,
      y: group.reduce((sum, change) => sum + change.y + 0.5, 0) / count,
      z: group.reduce((sum, change) => sum + change.z + 0.5, 0) / count,
    };
    for (const change of group) if (change.action === "PLACE") hand = materialOf(change.blockData);
    const feetY = Math.min(...group.map((change) => change.y + (change.action === "BREAK" ? 1 : 0)));
    bursts.push({ t: group[0].t, target, feetY, hand });
    group = [];
  };
  for (const change of changes) {
    if (group.length && change.t - group[0].t > burstWindowMs) flush();
    group.push(change);
  }
  flush();
  return bursts;
}

function horizontal(from: Point, to: Point): number {
  return Math.hypot(to.x - from.x, to.z - from.z);
}

function withinReach(stand: Point, target: Point): boolean {
  const flat = horizontal(stand, target);
  return flat >= minStandDistance && Math.hypot(flat, target.y - (stand.y + eyeHeight)) <= reachBlocks;
}

function standFor(target: Point, feetY: number, away: Point): Point {
  let dx = away.x - target.x;
  let dz = away.z - target.z;
  const length = Math.hypot(dx, dz);
  if (length < 1e-6) {
    dx = 0;
    dz = 1;
  } else {
    dx /= length;
    dz /= length;
  }
  return { x: target.x + dx * standDistance, y: feetY, z: target.z + dz * standDistance };
}

function frameAt(t: number, stand: Point, target: Point, hand: string, swinging: boolean): MotionFrame {
  const dy = target.y - (stand.y + eyeHeight);
  return {
    t,
    x: stand.x,
    y: stand.y,
    z: stand.z,
    yaw: yawOfDirection(target.x - stand.x, target.z - stand.z),
    pitch: (-Math.atan2(dy, Math.max(horizontal(stand, target), 1e-6)) * 180) / Math.PI,
    flags: flags.onGround | (swinging ? flags.swinging : 0),
    mainHand: hand,
  };
}

function walk(frames: MotionFrame[], from: Point, to: Point, departure: number, arrival: number, target: Point, hand: string) {
  const steps = Math.ceil(Math.hypot(to.x - from.x, to.y - from.y, to.z - from.z) / walkStepBlocks);
  for (let step = 1; step < steps; step++) {
    const f = step / steps;
    const point = { x: from.x + (to.x - from.x) * f, y: from.y + (to.y - from.y) * f, z: from.z + (to.z - from.z) * f };
    frames.push(frameAt(departure + (arrival - departure) * f, point, target, hand, false));
  }
}

function framesFor(changes: BlockChange[]): MotionFrame[] {
  const bursts = burstsOf(changes, air);
  const count = bursts.length;
  const centre = {
    x: bursts.reduce((sum, burst) => sum + burst.target.x, 0) / count,
    y: 0,
    z: bursts.reduce((sum, burst) => sum + burst.target.z, 0) / count,
  };
  const frames: MotionFrame[] = [];
  let stand: Point | null = null;
  let previous: Burst | null = null;
  for (const burst of bursts) {
    const next: Point = stand && withinReach(stand, burst.target) ? stand : standFor(burst.target, burst.feetY, stand ?? centre);
    if (stand && previous) {
      const settled = previous.t + swingDurationMs;
      const arrival = burst.t - arrivalLeadMs;
      if (arrival > settled) {
        frames.push(frameAt(settled, stand, previous.target, previous.hand, false));
        const walkMs = (Math.hypot(next.x - stand.x, next.y - stand.y, next.z - stand.z) / walkSpeed) * 1000;
        const departure = Math.max(settled, arrival - walkMs);
        if (departure > settled) frames.push(frameAt(departure, stand, burst.target, previous.hand, false));
        walk(frames, stand, next, departure, arrival, burst.target, previous.hand);
        frames.push(frameAt(arrival, next, burst.target, previous.hand, false));
      }
    }
    frames.push(frameAt(burst.t, next, burst.target, burst.hand, true));
    stand = next;
    previous = burst;
  }
  if (stand && previous) frames.push(frameAt(previous.t + swingDurationMs, stand, previous.target, previous.hand, false));
  return frames;
}

export function reconstructRecordings(changes: BlockChange[], recorded: ReadonlySet<string> = new Set()): Recording[] {
  const byPlayer = new Map<string, BlockChange[]>();
  for (const change of [...changes].sort((a, b) => a.t - b.t)) {
    if (recorded.has(change.player)) continue;
    const list = byPlayer.get(change.player);
    if (list) list.push(change);
    else byPlayer.set(change.player, [change]);
  }
  return [...byPlayer.values()].map((list) => ({ player: list[0].player, playerName: list[0].playerName, frames: framesFor(list) }));
}
