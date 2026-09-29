import type { ActivityItem, Evidence, FindingDetail, FindingSummary, Heatmap, Palette, PlayerRef, ScanCause, Scene, Source, Verdict } from "@/api/types";
import { colourOf, isAir, materialOf, rgbTriple } from "@/logic/blocks";
import { appliedCount, buildTimeline } from "@/logic/timeline";
import { cellIndex, dynamicStates, indexVolume } from "@/logic/volume";
import { builder, findingBox, syntheticEvidence, watcher } from "./evidence";
import scenesUrl from "./fixtures/scenes.bin?url";

interface FixtureScene {
  id: number;
  width: number;
  height: number;
  window: Scene["window"];
  palette: string[];
  x: number;
  z: number;
  y: number;
  score: number;
  model: string;
  verdict: Verdict | null;
  blocks: number[];
  heights: number[];
  luminance: number[];
  heat: { top: number; left: number; size: number; values: number[] };
}

interface Fixture {
  scenes: FixtureScene[];
  palette: Palette;
}

export interface MockFinding extends FindingDetail {
  scene: Scene | null;
  heatmap: Heatmap | null;
  evidence: Evidence | null;
  thumbnail: boolean;
  shareToken: string | null;
  shareActive: boolean;
}

export interface MockDb {
  me: { player: string; name: string; expiresAt: string; canShare: boolean };
  findings: MockFinding[];
  players: PlayerRef[];
  palette: Palette;
  activity: ActivityItem[];
  scans: { id: number; world: string; cause: ScanCause; totalTiles: number; startedAt: number; tileMs: number; findings: number }[];
  shares: Map<string, number>;
}

export function publicUrl(token: string): string {
  return `${location.origin}/review/e/${token}`;
}

export const staff = { uuid: "0f3a9c55-2d1e-4b7a-9c8e-6a5b4d3c2e10", name: "kyleonaut" };

const extraPlayers: PlayerRef[] = [
  { uuid: "c2d4e6f8-1a3b-4c5d-8e7f-9a0b1c2d3e4f", name: "Mira_Builds" },
  { uuid: "7e6d5c4b-3a29-4817-8f6e-5d4c3b2a1908", name: "Ferrox" },
  { uuid: "1b2c3d4e-5f60-4718-a9b0-c1d2e3f40516", name: "quietpine" },
  { uuid: "9a8b7c6d-5e4f-4321-b0a9-8c7d6e5f4a3b", name: null },
  { uuid: "4d5e6f70-8192-4a3b-8c4d-5e6f7a8b9c0d", name: "TheLantern" },
];

const reviewers = ["kyleonaut", "Staff_Anna", "mod_jonas"];
const worlds = ["world", "world", "world", "world_nether", "creative"];

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

async function loadFixture(): Promise<Fixture> {
  const response = await fetch(scenesUrl);
  const stream = response.body;
  if (!stream) throw new Error("fixture");
  const text = await new Response(stream.pipeThrough(new DecompressionStream("deflate"))).text();
  return JSON.parse(text) as Fixture;
}

function fixtureScene(fixture: FixtureScene): { scene: Scene; heatmap: Heatmap } {
  const blocks = fixture.blocks.map((value) => (value === 255 ? -1 : value));
  const values = new Array<number>(fixture.width * fixture.height).fill(0);
  const { top, left, size } = fixture.heat;
  for (let r = 0; r < size; r++) {
    for (let c = 0; c < size; c++) {
      const row = top + r;
      const col = left + c;
      if (row >= 0 && col >= 0 && row < fixture.height && col < fixture.width) values[row * fixture.width + col] = fixture.heat.values[r * size + c];
    }
  }
  return {
    scene: {
      source: "fullscan",
      width: fixture.width,
      height: fixture.height,
      window: fixture.window,
      palette: fixture.palette,
      blocks,
      heights: fixture.heights,
      luminance: fixture.luminance,
    },
    heatmap: { width: fixture.width, height: fixture.height, values },
  };
}

function evidenceScene(evidence: Evidence, palette: Palette): { scene: Scene; heatmap: Heatmap } {
  const timeline = buildTimeline(evidence);
  const indexed = indexVolume(evidence.before, timeline.changes);
  const cells = Int32Array.from(indexed.cells);
  const finalStates = dynamicStates(indexed, appliedCount(timeline, timeline.end));
  indexed.dynamicCells.forEach((cell, i) => {
    cells[cell] = finalStates[i];
  });
  const { sizeX, sizeY, sizeZ, minX, minZ } = evidence.before;
  const keys: string[] = [];
  const keyIndex = new Map<string, number>();
  const blocks: number[] = [];
  const tops: number[] = [];
  const luminance: number[] = [];
  const heat: number[] = [];
  for (let dz = 0; dz < sizeZ; dz++) {
    for (let dx = 0; dx < sizeX; dx++) {
      let top = -1;
      for (let dy = sizeY - 1; dy >= 0; dy--) {
        const state = indexed.states[cells[cellIndex(evidence.before, dx, dy, dz)]];
        if (!isAir(state) && !state.includes("short_grass") && !state.includes("poppy")) {
          top = dy;
          const material = materialOf(state);
          if (!keyIndex.has(material)) {
            keyIndex.set(material, keys.length);
            keys.push(material);
          }
          blocks.push(keyIndex.get(material) ?? 0);
          const [r, g, b] = rgbTriple(colourOf(material, palette));
          luminance.push(Math.round(0.299 * r + 0.587 * g + 0.114 * b));
          break;
        }
      }
      if (top < 0) {
        blocks.push(-1);
        luminance.push(0);
      }
      tops.push(Math.max(0, top));
      const x = minX + dx;
      const z = minZ + dz;
      const inside = x >= findingBox.minX && x <= findingBox.maxX && z >= findingBox.minZ && z <= findingBox.maxZ;
      const dark = blocks[blocks.length - 1] === keyIndex.get("minecraft:black_concrete");
      const near = x >= findingBox.minX - 1 && x <= findingBox.maxX + 1 && z >= findingBox.minZ - 1 && z <= findingBox.maxZ + 1;
      const ripple = (dx * 37 + dz * 91) % 40;
      heat.push(inside ? (dark ? 170 + ripple : 25 + ripple / 2) : near ? 8 : 0);
    }
  }
  const low = Math.min(...tops);
  return {
    scene: {
      source: "mask",
      width: sizeX,
      height: sizeZ,
      window: { top: findingBox.minZ - minZ, left: findingBox.minX - minX, bottom: findingBox.maxZ - minZ, right: findingBox.maxX - minX },
      palette: keys,
      blocks,
      heights: tops.map((value) => value - low),
      luminance,
    },
    heatmap: { width: sizeX, height: sizeZ, values: heat },
  };
}

function teleport(world: string, x: number, y: number, z: number): string {
  const command = `/tp @s ${x} ${y} ${z}`;
  return world === "world" ? command : `/execute in minecraft:${world} run tp @s ${x} ${y} ${z}`;
}

export async function createDb(now = Date.now()): Promise<MockDb> {
  const fixture = await loadFixture();
  const palette: Palette = {
    ...fixture.palette,
    "minecraft:black_concrete": 0x080a0f,
    "minecraft:white_concrete": 0xcfd5d6,
    "minecraft:grass_block": 0x7fb238,
    "minecraft:short_grass": 0x7fb238,
    "minecraft:oak_log": 0x8f7748,
    "minecraft:oak_leaves": 0x007c00,
    "minecraft:water": 0x4040ff,
    "minecraft:glass": 0xc8dce6,
    "minecraft:stone_slab": 0x707070,
    "minecraft:poppy": 0xff0000,
    "minecraft:gravel": 0xa7a7a7,
    "minecraft:dirt": 0x976d4d,
    "minecraft:stone": 0x707070,
  };
  const rnd = random(2026);
  const players = [builder, watcher, ...extraPlayers].map((player) => ({ uuid: player.uuid, name: player.name }));
  const fixtureScenes = fixture.scenes.map(fixtureScene);
  const findings: MockFinding[] = [];
  const total = 118;
  const activity: ActivityItem[] = [];
  for (let n = 0; n < total; n++) {
    const id = 1001 + n;
    const age = (total - n) * (0.5 + rnd()) * 11 * 3600_000;
    const createdAt = new Date(now - age - 3600_000);
    const fixtureIndex = n % fixture.scenes.length;
    const source: Source = rnd() < 0.35 ? "mask" : "fullscan";
    const world = worlds[Math.floor(rnd() * worlds.length)];
    const base = fixture.scenes[fixtureIndex];
    const { scene, heatmap } = fixtureScenes[fixtureIndex];
    const minX = base.x + base.window.left;
    const minZ = base.z + base.window.top;
    const box = { minX, minY: base.y - 2, minZ, maxX: minX + base.window.right - base.window.left, maxY: base.y + 3, maxZ: minZ + base.window.bottom - base.window.top };
    const playerCount = source === "mask" ? 1 + Math.floor(rnd() * 2) : Math.floor(rnd() * 3);
    const involved = [...players].sort(() => rnd() - 0.5).slice(0, playerCount);
    const reviewedChance = age / (total * 11 * 3600_000);
    let review: FindingSummary["review"] = null;
    if (rnd() < 0.25 + reviewedChance) {
      const verdict: Verdict = base.verdict === "CONFIRMED" || rnd() < 0.3 ? "CONFIRMED" : "FALSE_ALARM";
      const reviewedAt = new Date(Math.min(now - 60_000, createdAt.getTime() + rnd() * 30 * 3600_000));
      const reviewer = reviewers[Math.floor(rnd() * reviewers.length)];
      review = { verdict, reviewer, reviewedAt: reviewedAt.toISOString() };
      activity.push({ at: reviewedAt.toISOString(), actor: reviewer, kind: verdict, findingId: id });
    }
    const centre = { x: Math.floor((box.minX + box.maxX) / 2), y: Math.floor((box.minY + box.maxY) / 2), z: Math.floor((box.minZ + box.maxZ) / 2) };
    findings.push({
      id,
      source,
      world,
      box,
      score: Math.min(0.999, base.score - rnd() * 0.08),
      votes: source === "fullscan" ? 1 + Math.floor(rnd() * 4) : 1,
      detail: source === "fullscan" ? `Tile ${box.minX >> 4},${box.minZ >> 4}` : "Live check after block placement",
      modelVersion: source === "fullscan" ? base.model : "bf-scan-3",
      createdAt: createdAt.toISOString(),
      players: involved,
      review,
      hasEvidence: false,
      sharedSince: null,
      shareUrl: null,
      teleport: teleport(world, centre.x, centre.y + 20, centre.z),
      scene,
      heatmap: id % 9 === 4 ? null : heatmap,
      evidence: null,
      thumbnail: id % 13 !== 5,
      shareToken: null,
      shareActive: false,
    });
  }
  const evidenceFindings: { id: number; review: FindingSummary["review"]; token: string | null; start: number }[] = [
    { id: 1001 + total, review: null, token: null, start: now - 42 * 60_000 },
    {
      id: 1001 + total - 5,
      review: { verdict: "CONFIRMED", reviewer: "Staff_Anna", reviewedAt: new Date(now - 5 * 3600_000).toISOString() },
      token: "demo-public-evidence",
      start: now - 9 * 3600_000,
    },
  ];
  for (const entry of evidenceFindings) {
    const evidence = syntheticEvidence(entry.id, entry.start);
    const { scene, heatmap } = evidenceScene(evidence, palette);
    const centre = { x: Math.floor((findingBox.minX + findingBox.maxX) / 2), y: findingBox.minY, z: Math.floor((findingBox.minZ + findingBox.maxZ) / 2) };
    const createdAt = new Date(evidence.changes[evidence.changes.length - 1].t + 1500).toISOString();
    const finding: MockFinding = {
      id: entry.id,
      source: "mask",
      world: "world",
      box: findingBox,
      score: 0.962,
      votes: 1,
      detail: "Live check after block placement",
      modelVersion: "bf-scan-3",
      createdAt,
      players: [
        { uuid: builder.uuid, name: builder.name },
        { uuid: watcher.uuid, name: watcher.name },
      ],
      review: entry.review,
      hasEvidence: true,
      sharedSince: entry.token ? new Date(now - 4 * 3600_000).toISOString() : null,
      shareUrl: entry.token ? publicUrl(entry.token) : null,
      teleport: teleport("world", centre.x, centre.y + 10, centre.z),
      scene,
      heatmap,
      evidence,
      thumbnail: true,
      shareToken: entry.token,
      shareActive: entry.token !== null,
    };
    const index = findings.findIndex((item) => item.id === entry.id);
    for (let i = activity.length - 1; i >= 0; i--) if (activity[i].findingId === entry.id) activity.splice(i, 1);
    if (index >= 0) findings[index] = finding;
    else findings.push(finding);
    if (entry.review) activity.push({ at: entry.review.reviewedAt, actor: entry.review.reviewer, kind: "CONFIRMED", findingId: entry.id });
    if (entry.token && finding.sharedSince) activity.push({ at: finding.sharedSince, actor: "Staff_Anna", kind: "SHARED", findingId: entry.id });
  }
  findings.sort((a, b) => b.id - a.id);
  activity.sort((a, b) => b.at.localeCompare(a.at));
  return {
    me: { player: staff.uuid, name: staff.name, expiresAt: new Date(now + 12 * 3600_000).toISOString(), canShare: true },
    findings,
    players: [...players, staff],
    palette,
    activity,
    scans: [
      { id: 311, world: "world", cause: "DAILY", totalTiles: 1600, startedAt: now - 7 * 60_000, tileMs: 450, findings: 3 },
      { id: 312, world: "world_nether", cause: "MANUAL", totalTiles: 400, startedAt: now + 5 * 60_000, tileMs: 300, findings: 0 },
    ],
    shares: new Map(evidenceFindings.filter((entry) => entry.token).map((entry) => [entry.token ?? "", entry.id])),
  };
}
