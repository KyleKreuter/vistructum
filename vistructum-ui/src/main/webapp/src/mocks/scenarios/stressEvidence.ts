import type { BlockChange, Evidence, MotionFrame, Recording } from "@/api/types";
import { seededRandom } from "@/logic/mc/random";
import { flags, yawOfDirection } from "@/logic/motion";
import { playerLabel } from "@/logic/format";
import type { MockFinding } from "../data";

export const stressVolume = {
  size: 64,
  inner: { x: 56, y: 24, z: 56 },
  margin: { x: 4, y: 20, z: 4 },
};

const frameStep = 100;
const leadMs = 30_000;
const buildMs = 240_000;
const maxRecordings = 12;

const terrainStates = [
  "minecraft:air",
  "minecraft:stone",
  "minecraft:dirt",
  "minecraft:grass_block[snowy=false]",
  "minecraft:oak_log[axis=y]",
  "minecraft:oak_leaves[distance=1,persistent=false,waterlogged=false]",
  "minecraft:water[level=0]",
  "minecraft:gravel",
  "minecraft:short_grass",
  "minecraft:coal_ore",
];

const buildStates = [
  "minecraft:stone_bricks",
  "minecraft:oak_planks",
  "minecraft:cobblestone",
  "minecraft:glass",
  "minecraft:white_concrete",
  "minecraft:black_concrete",
  "minecraft:green_wool",
  "minecraft:sandstone",
  "minecraft:glowstone",
  "minecraft:white_stained_glass",
  "minecraft:spruce_log[axis=x]",
  "minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]",
];

const air = 0;
const stone = 1;
const dirt = 2;
const grass = 3;
const log = 4;
const leaves = 5;
const water = 6;
const gravel = 7;
const shortGrass = 8;
const ore = 9;

function round(value: number): number {
  return Math.round(value * 1000) / 1000;
}

export function stressEvidence(finding: MockFinding): Evidence {
  const rnd = seededRandom(Math.imul(finding.id, 40503) ^ 0x9e3779b9);
  const { size, margin } = stressVolume;
  const minX = finding.box.minX - margin.x;
  const minY = finding.box.minY - margin.y;
  const minZ = finding.box.minZ - margin.z;
  const phaseX = rnd() * 6;
  const phaseZ = rnd() * 6;
  const ground = (dx: number, dz: number) => 18 + Math.round(2.5 * Math.sin(dx / 7 + phaseX) + 2.5 * Math.cos(dz / 9 + phaseZ));
  const cells = new Array<number>(size * size * size).fill(air);
  const index = (dx: number, dy: number, dz: number) => (dy * size + dz) * size + dx;
  const inside = (dx: number, dy: number, dz: number) => dx >= 0 && dy >= 0 && dz >= 0 && dx < size && dy < size && dz < size;
  for (let dz = 0; dz < size; dz++) {
    for (let dx = 0; dx < size; dx++) {
      const top = ground(dx, dz);
      for (let dy = 0; dy < top; dy++) cells[index(dx, dy, dz)] = dy < top - 3 ? (rnd() < 0.02 ? ore : stone) : dirt;
      const low = top <= 14;
      cells[index(dx, top, dz)] = low ? (rnd() < 0.5 ? water : gravel) : grass;
      if (!low && rnd() < 0.1) cells[index(dx, top + 1, dz)] = shortGrass;
    }
  }
  for (let tree = 0; tree < 14; tree++) {
    const tx = 2 + Math.floor(rnd() * (size - 4));
    const tz = 2 + Math.floor(rnd() * (size - 4));
    const base = ground(tx, tz) + 1;
    const height = 4 + Math.floor(rnd() * 3);
    for (let dy = -2; dy <= 1; dy++) {
      for (let lx = -2; lx <= 2; lx++) {
        for (let lz = -2; lz <= 2; lz++) {
          const y = base + height + dy;
          if (inside(tx + lx, y, tz + lz) && Math.abs(lx) + Math.abs(lz) < 4 - Math.max(0, dy)) cells[index(tx + lx, y, tz + lz)] = leaves;
        }
      }
    }
    for (let dy = 0; dy < height; dy++) if (inside(tx, base + dy, tz)) cells[index(tx, base + dy, tz)] = log;
  }

  const recorded = finding.players.slice(0, maxRecordings);
  const end = Date.parse(finding.createdAt) - 1500;
  const start = end - buildMs - leadMs;
  const count = 2500 + Math.floor(rnd() * 1500);
  const times = Array.from({ length: count }, () => start + leadMs + Math.floor(rnd() * buildMs)).sort((a, b) => a - b);
  const placed: { x: number; y: number; z: number }[] = [];
  const changes: BlockChange[] = times.map((t) => {
    const player = recorded[Math.floor(rnd() * recorded.length)];
    const base = { t, player: player.uuid, playerName: playerLabel(player) };
    if (placed.length > 0 && rnd() < 0.3) {
      const slot = Math.floor(rnd() * placed.length);
      const target = placed[slot];
      placed[slot] = placed[placed.length - 1];
      placed.pop();
      return { ...base, action: "BREAK", ...target, blockData: "minecraft:air" };
    }
    const dx = margin.x + Math.floor(rnd() * stressVolume.inner.x);
    const dz = margin.z + Math.floor(rnd() * stressVolume.inner.z);
    const dy = Math.min(size - 1, ground(dx, dz) + 1 + Math.floor(rnd() * 20));
    const target = { x: minX + dx, y: minY + dy, z: minZ + dz };
    placed.push(target);
    return { ...base, action: "PLACE", ...target, blockData: buildStates[Math.floor(rnd() * buildStates.length)] };
  });

  const recordings: Recording[] = recorded.map((player) => {
    const own = changes.filter((change) => change.player === player.uuid);
    const swings = new Set(own.map((change) => Math.round((change.t - start) / frameStep)));
    const frames: MotionFrame[] = [];
    let x = minX + 2 + rnd() * (size - 4);
    let z = minZ + 2 + rnd() * (size - 4);
    let goal = { x, z };
    let sneaking = false;
    let sprinting = false;
    let hand = "minecraft:air";
    let next = 0;
    for (let t = start, step = 0; t <= end + 3000; t += frameStep, step++) {
      while (next < own.length && own[next].t <= t) {
        if (own[next].action === "PLACE") hand = own[next].blockData;
        next++;
      }
      if (Math.hypot(goal.x - x, goal.z - z) < 0.3) {
        goal = { x: minX + 1 + rnd() * (size - 2), z: minZ + 1 + rnd() * (size - 2) };
        sneaking = rnd() < 0.15;
        sprinting = !sneaking && rnd() < 0.3;
      }
      const speed = sneaking ? 1.3 : sprinting ? 5.6 : 4.3;
      const dx = goal.x - x;
      const dz = goal.z - z;
      const distance = Math.hypot(dx, dz) || 1;
      const length = Math.min(distance, (speed * frameStep) / 1000);
      x += (dx / distance) * length;
      z += (dz / distance) * length;
      const yaw = yawOfDirection(dx, dz);
      const column = ground(Math.min(size - 1, Math.max(0, Math.floor(x - minX))), Math.min(size - 1, Math.max(0, Math.floor(z - minZ))));
      const swinging = swings.has(step);
      frames.push({
        t,
        x: round(x),
        y: minY + column + 1,
        z: round(z),
        yaw: round(yaw),
        pitch: swinging ? 35 : round(5 + 10 * Math.sin(step / 20)),
        flags: flags.onGround | (sneaking ? flags.sneaking : 0) | (sprinting ? flags.sprinting : 0) | (swinging ? flags.swinging : 0),
        mainHand: hand,
      });
    }
    return { player: player.uuid, playerName: playerLabel(player), frames };
  });

  return {
    findingId: finding.id,
    before: { minX, minY, minZ, sizeX: size, sizeY: size, sizeZ: size, palette: [...terrainStates, ...buildStates], cells },
    changes,
    recordings,
  };
}
