import type { ActivityItem, Box, FindingSummary, Heatmap, PlayerRef, Scene, Source, Verdict } from "@/api/types";
import { seededRandom } from "@/logic/mc/random";
import { expiresIn, fixtureScene, mockPalette, publicUrl, staff, teleport, texturesConfigured, type Fixture, type MockDb, type MockFinding, type MockScan } from "../data";
import { stressEvidence, stressVolume } from "./stressEvidence";

export const stressSeed = 0x5eed2026;
export const stressFindingCount = 50_000;
export const stressPlayerCount = 2_000;
export const stressFirstId = 9_950_001;
export const stressPublicToken = "stress-public-evidence";

const worldBorder = 29_999_000;
const dayMs = 86_400_000;
const historyDays = 420;

const syllables = ["ka", "zu", "mi", "ro", "tex", "vor", "lin", "quo", "dra", "fen", "gul", "hax", "ix", "jor", "nex", "pyr", "sly", "thu", "wex", "yol"];

const longNames = [
  "The_Unreasonably_Long_Player_Name_For_Layout_Checks",
  "WWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWW",
  "Maximilian_Alexander_von_Blockhausen_der_Dritte",
  "xX_Definitely_Not_A_Griefer_Just_Building_Xx",
];

const longWorlds = [
  "creative_plots_extremely_long_world_name_for_overflow_checks_2026",
  "minigames_bedwars_arena_with_a_ridiculously_long_identifier_v12",
  "survival_season_seven_the_great_expansion_resource_world_nether",
];

const reviewerNames = [
  "kyleonaut",
  "Staff_Anna",
  "mod_jonas",
  "Senior_Moderator_With_A_Very_Long_Display_Name",
  "WWWWWWWWWWWWWWWW",
  ...Array.from({ length: 25 }, (_, index) => `helper_${String(index + 1).padStart(2, "0")}`),
];

function hex(rnd: () => number, length: number): string {
  let text = "";
  for (let i = 0; i < length; i++) text += Math.floor(rnd() * 16).toString(16);
  return text;
}

function uuid(rnd: () => number): string {
  return `${hex(rnd, 8)}-${hex(rnd, 4)}-4${hex(rnd, 3)}-a${hex(rnd, 3)}-${hex(rnd, 12)}`;
}

function playerName(rnd: () => number, index: number): string | null {
  const roll = rnd();
  if (roll < 0.03) return null;
  if (roll < 0.08) return `${longNames[index % longNames.length]}_${index}`;
  if (roll < 0.12) return "W".repeat(16);
  let name = "";
  while (name.length < 6 + rnd() * 8) name += syllables[Math.floor(rnd() * syllables.length)];
  name = name.charAt(0).toUpperCase() + name.slice(1);
  if (rnd() < 0.5) name += `_${Math.floor(rnd() * 1000)}`;
  return name.slice(0, 16);
}

export function stressPlayers(): PlayerRef[] {
  const rnd = seededRandom(stressSeed);
  return Array.from({ length: stressPlayerCount }, (_, index) => ({ uuid: uuid(rnd), name: playerName(rnd, index) }));
}

export function stressWorlds(): string[] {
  const generated = Array.from({ length: 34 }, (_, index) => `survival_s${index + 1}_${["overworld", "nether", "end", "resource"][index % 4]}`);
  return ["world", "world_nether", "world_the_end", ...longWorlds, ...generated];
}

function findingRandom(id: number): () => number {
  return seededRandom(stressSeed ^ Math.imul(id, 2654435761));
}

function pick<T>(rnd: () => number, values: readonly T[]): T {
  return values[Math.floor(rnd() * values.length)];
}

function involvedPlayers(rnd: () => number, players: PlayerRef[], evidence: boolean): PlayerRef[] {
  const roll = rnd();
  const count = evidence ? 2 + Math.floor(rnd() * 14) : roll < 0.2 ? 0 : roll < 0.9 ? 1 + Math.floor(rnd() * 2) : roll < 0.98 ? 3 + Math.floor(rnd() * 6) : 20 + Math.floor(rnd() * 41);
  const chosen = new Map<string, PlayerRef>();
  while (chosen.size < count) {
    const player = pick(rnd, players);
    chosen.set(player.uuid, player);
  }
  return [...chosen.values()];
}

function coordinate(rnd: () => number): number {
  const roll = rnd();
  if (roll < 0.15) return -worldBorder + Math.floor(rnd() * 2000);
  if (roll < 0.3) return worldBorder - Math.floor(rnd() * 2000);
  return Math.floor((rnd() * 2 - 1) * worldBorder);
}

function findingBox(rnd: () => number, evidence: boolean): Box {
  const minX = coordinate(rnd);
  const minZ = coordinate(rnd);
  if (evidence) {
    const minY = -40 + Math.floor(rnd() * 200);
    const { inner } = stressVolume;
    return { minX, minY, minZ, maxX: minX + inner.x - 1, maxY: minY + inner.y - 1, maxZ: minZ + inner.z - 1 };
  }
  const minY = -64 + Math.floor(rnd() * 360);
  const sizeX = 3 + Math.floor(rnd() * 60);
  const sizeZ = 3 + Math.floor(rnd() * 60);
  return { minX, minY, minZ, maxX: minX + sizeX, maxY: minY + 1 + Math.floor(rnd() * 20), maxZ: minZ + sizeZ };
}

function stressScans(now: number, worlds: string[]): MockScan[] {
  return Array.from({ length: 14 }, (_, index) => ({
    id: 90_000 + index,
    world: worlds[(index * 7) % worlds.length],
    cause: index % 3 === 0 ? "MANUAL" : "DAILY",
    totalTiles: 2_560_000 - index * 97_331,
    startedAt: index < 9 ? now - (index + 1) * 13 * 60_000 : now + (index - 8) * 45 * 60_000,
    tileMs: 1 + (index % 4),
    findings: 1_250 * (index + 1),
  }));
}

interface SceneSet {
  scene: Scene;
  heatmap: Heatmap;
}

export function createStressDb(fixture: Fixture, now = Date.now()): MockDb {
  const players = stressPlayers();
  const worlds = stressWorlds();
  const scenes: SceneSet[] = fixture.scenes.map(fixtureScene);
  const findings: MockFinding[] = new Array<MockFinding>(stressFindingCount);
  const activity: ActivityItem[] = [];
  const shares = new Map<string, number>();
  const spacing = (historyDays * dayMs) / stressFindingCount;
  for (let n = 0; n < stressFindingCount; n++) {
    const id = stressFirstId + n;
    const rnd = findingRandom(id);
    const created = now - (stressFindingCount - n) * spacing - rnd() * spacing;
    const source: Source = rnd() < 0.4 ? "mask" : "fullscan";
    const hasEvidence = source === "mask" && rnd() < 0.1;
    const world = rnd() < 0.5 ? "world" : pick(rnd, worlds);
    const box = findingBox(rnd, hasEvidence);
    const age = (now - created) / (historyDays * dayMs);
    let review: FindingSummary["review"] = null;
    if (rnd() < 0.3 + 0.65 * age) {
      const verdict: Verdict = rnd() < 0.45 ? "CONFIRMED" : "FALSE_ALARM";
      const reviewedAt = new Date(Math.min(now - 60_000, created + rnd() * 3 * dayMs)).toISOString();
      const reviewer = pick(rnd, reviewerNames);
      review = { verdict, reviewer, reviewedAt };
      activity.push({ at: reviewedAt, actor: reviewer, kind: verdict, findingId: id });
    }
    let shareToken: string | null = null;
    let shareActive = false;
    let sharedSince: string | null = null;
    if (hasEvidence && review?.verdict === "CONFIRMED" && rnd() < 0.25) {
      shareToken = `stress-${id}`;
      const sharedAt = new Date(Math.min(now - 30_000, Date.parse(review.reviewedAt) + 3600_000)).toISOString();
      activity.push({ at: sharedAt, actor: review.reviewer, kind: "SHARED", findingId: id });
      shareActive = rnd() < 0.8;
      if (shareActive) sharedSince = sharedAt;
      else activity.push({ at: new Date(Math.min(now - 20_000, Date.parse(sharedAt) + dayMs)).toISOString(), actor: review.reviewer, kind: "UNSHARED", findingId: id });
      shares.set(shareToken, id);
    }
    const sceneSet = scenes[id % scenes.length];
    const centre = { x: Math.floor((box.minX + box.maxX) / 2), y: box.maxY + 20, z: Math.floor((box.minZ + box.maxZ) / 2) };
    const involved = involvedPlayers(rnd, players, hasEvidence);
    findings[stressFindingCount - 1 - n] = {
      id,
      source,
      world,
      box,
      score: 0.5 + rnd() * 0.4999,
      votes: source === "fullscan" ? 1 + Math.floor(rnd() * 12) : 1,
      detail:
        id % 97 === 0
          ? "Live check after block placement near a chunk border with many overlapping regions, merged from several consecutive detections of the same structure"
          : source === "fullscan"
            ? `Tile ${box.minX >> 4},${box.minZ >> 4}`
            : "Live check after block placement",
      modelVersion: source === "fullscan" ? `scan-v${4 + (id % 3)}` : "bf-scan-3",
      createdAt: new Date(created).toISOString(),
      players: involved,
      review,
      hasEvidence,
      hasTerrain: source === "fullscan",
      sharedSince,
      shareUrl: shareActive && shareToken ? publicUrl(shareToken) : null,
      teleport: teleport(world, centre.x, centre.y, centre.z),
      scene: sceneSet.scene,
      heatmap: id % 11 === 3 ? null : sceneSet.heatmap,
      evidence: null,
      thumbnail: id % 17 !== 5,
      shareToken,
      shareActive,
    };
  }
  const newestShared = findings.find((finding) => finding.shareActive);
  if (newestShared) shares.set(stressPublicToken, newestShared.id);
  activity.sort((a, b) => b.at.localeCompare(a.at));
  return {
    me: { player: staff.uuid, name: staff.name, expiresAt: expiresIn(now), canShare: true },
    findings,
    players: [...players, staff],
    palette: mockPalette(fixture),
    activity,
    scans: stressScans(now, worlds),
    shares,
    status: {
      trackedChanges: 1_284_736_912,
      recordingEnabled: true,
      texturesAvailable: texturesConfigured,
      inference: {
        mode: "REMOTE",
        available: true,
        models: [
          { kind: "fullscan", version: "scan-v6-2026-09-28-large-context-experimental" },
          { kind: "mask", version: "bf-scan-3" },
          { kind: "mask-secondary-ensemble-member", version: "bf-scan-3-distilled-int8" },
        ],
        detail: "Remote inference at https://inference.internal.example.org:8443/v2/models with a very long endpoint path for layout checks",
      },
    },
    evidenceOf: (finding) => (finding.hasEvidence ? stressEvidence(finding) : null),
  };
}
