import type { Box, Evidence, PlayerRef } from "@/api/types";
import { defaultInference, expiresIn, fixtureScene, mockPalette, staff, storedEvidence, teleport, texturesConfigured, type Fixture, type MockDb, type MockFinding } from "../data";

export const sparsePlayer: PlayerRef = { uuid: "3c1d9e7a-6b2f-4d8e-a5c1-7f0e2b4d6a98", name: "LonelyBuilder" };

const box: Box = { minX: 12, minY: 70, minZ: -8, maxX: 16, maxY: 71, maxZ: -4 };

function quietEvidence(findingId: number): Evidence {
  const sizeX = 9;
  const sizeY = 5;
  const sizeZ = 9;
  const cells = new Array<number>(sizeX * sizeY * sizeZ).fill(0);
  for (let dz = 0; dz < sizeZ; dz++) {
    for (let dx = 0; dx < sizeX; dx++) {
      cells[dz * sizeX + dx] = 1;
      cells[(sizeZ + dz) * sizeX + dx] = 2;
    }
  }
  return {
    findingId,
    before: { minX: box.minX - 2, minY: box.minY - 2, minZ: box.minZ - 2, sizeX, sizeY, sizeZ, palette: ["minecraft:air", "minecraft:dirt", "minecraft:grass_block[snowy=false]"], cells },
    changes: [],
    recordings: [],
  };
}

export function createSparseDb(fixture: Fixture, now = Date.now()): MockDb {
  const scenes = [0, 1, 2].map((slot) => fixtureScene(fixture.scenes[slot % fixture.scenes.length]));
  const hour = 3600_000;
  const base = {
    box,
    score: 0.912,
    votes: 1,
    sharedSince: null,
    shareUrl: null,
    shareToken: null,
    shareActive: false,
    teleport: teleport("world", 14, 90, -6),
    world: "world",
  };
  const open: MockFinding = {
    ...base,
    id: 3,
    source: "mask",
    detail: "Live check after block placement",
    modelVersion: "bf-scan-3",
    createdAt: new Date(now - 2 * hour).toISOString(),
    players: [sparsePlayer],
    review: null,
    hasEvidence: true,
    hasTerrain: false,
    scene: scenes[0].scene,
    heatmap: null,
    evidence: quietEvidence(3),
    thumbnail: false,
  };
  const confirmedAt = new Date(now - 20 * hour).toISOString();
  const confirmed: MockFinding = {
    ...base,
    id: 2,
    source: "fullscan",
    detail: "Tile 0,-1",
    modelVersion: "scan-v4",
    createdAt: new Date(now - 26 * hour).toISOString(),
    players: [],
    review: { verdict: "CONFIRMED", reviewer: staff.name, reviewedAt: confirmedAt },
    hasEvidence: false,
    hasTerrain: true,
    votes: 3,
    scene: scenes[1].scene,
    heatmap: scenes[1].heatmap,
    evidence: null,
    thumbnail: true,
  };
  const dismissedAt = new Date(now - 40 * hour).toISOString();
  const dismissed: MockFinding = {
    ...base,
    id: 1,
    source: "mask",
    detail: "Live check after block placement",
    modelVersion: "bf-scan-3",
    createdAt: new Date(now - 50 * hour).toISOString(),
    players: [sparsePlayer],
    review: { verdict: "FALSE_ALARM", reviewer: staff.name, reviewedAt: dismissedAt },
    hasEvidence: false,
    hasTerrain: false,
    scene: scenes[2].scene,
    heatmap: scenes[2].heatmap,
    evidence: null,
    thumbnail: true,
  };
  return {
    me: { player: staff.uuid, name: staff.name, expiresAt: expiresIn(now), canShare: true, blockLog: true, canRollback: true },
    findings: [open, confirmed, dismissed],
    players: [sparsePlayer, staff],
    palette: mockPalette(fixture),
    activity: [
      { at: confirmedAt, actor: staff.name, kind: "CONFIRMED", findingId: 2 },
      { at: dismissedAt, actor: staff.name, kind: "FALSE_ALARM", findingId: 1 },
    ],
    scans: [{ id: 1, world: "world", cause: "MANUAL", totalTiles: 64, startedAt: now + 10 * 60_000, tileMs: 1000, findings: 0 }],
    shares: new Map(),
    status: { trackedChanges: 12, recordingEnabled: true, texturesAvailable: texturesConfigured, inference: defaultInference },
    evidenceOf: storedEvidence,
  };
}
