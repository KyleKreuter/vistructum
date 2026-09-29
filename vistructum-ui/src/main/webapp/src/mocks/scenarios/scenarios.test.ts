import { beforeAll, describe, expect, it, vi } from "vitest";
import type { Fixture } from "../data";
import { createEmptyDb } from "./empty";
import { createSparseDb } from "./sparse";
import { createStressDb, stressFindingCount, stressPlayerCount, stressPlayers } from "./stress";
import { stressVolume } from "./stressEvidence";

const size = 4;
const fixture: Fixture = {
  palette: { "minecraft:stone": 0x707070 },
  scenes: [
    {
      id: 1,
      width: size,
      height: size,
      window: { top: 1, left: 1, bottom: 2, right: 2 },
      palette: ["minecraft:stone"],
      x: 0,
      y: 64,
      z: 0,
      score: 0.9,
      model: "scan-v4",
      verdict: null,
      blocks: new Array<number>(size * size).fill(0),
      heights: new Array<number>(size * size).fill(1),
      luminance: new Array<number>(size * size).fill(100),
      heat: { top: 0, left: 0, size: 1, values: [10] },
    },
  ],
};

const now = Date.UTC(2026, 8, 29, 12);

beforeAll(() => {
  vi.stubGlobal("location", { origin: "http://localhost:5173" });
});

describe("mock scenarios", () => {
  it("keeps the empty scenario empty", () => {
    const db = createEmptyDb(fixture, now);
    expect(db.findings).toHaveLength(0);
    expect(db.activity).toHaveLength(0);
    expect(db.scans).toHaveLength(0);
    expect(db.status.recordingEnabled).toBe(false);
    expect(db.status.texturesAvailable).toBe(false);
  });

  it("covers every verdict and source once in the sparse scenario", () => {
    const db = createSparseDb(fixture, now);
    expect(db.findings.map((finding) => finding.review?.verdict ?? null).sort()).toEqual(["CONFIRMED", "FALSE_ALARM", null].sort());
    expect(new Set(db.findings.map((finding) => finding.source))).toEqual(new Set(["mask", "fullscan"]));
    expect(new Set(db.findings.flatMap((finding) => finding.players.map((player) => player.uuid))).size).toBe(1);
    const quiet = db.findings.find((finding) => finding.hasEvidence);
    const evidence = quiet ? db.evidenceOf(quiet) : null;
    expect(evidence?.changes).toHaveLength(0);
    expect(evidence?.recordings).toHaveLength(0);
  });

  it("builds a deterministic stress set", () => {
    const db = createStressDb(fixture, now);
    expect(db.findings).toHaveLength(stressFindingCount);
    expect(stressPlayers()).toEqual(stressPlayers());
    expect(db.players).toHaveLength(stressPlayerCount + 1);
    expect(createStressDb(fixture, now).findings.slice(0, 50)).toEqual(db.findings.slice(0, 50));
    expect(db.findings[0].id).toBeGreaterThan(db.findings[1].id);
    expect(db.findings.some((finding) => finding.players.length >= 20)).toBe(true);
    expect(db.findings.some((finding) => finding.box.minX < -29_000_000)).toBe(true);
    expect(db.findings.some((finding) => finding.box.maxX > 29_000_000)).toBe(true);
    expect(db.activity.length).toBeGreaterThan(10_000);
  });

  it("generates stress evidence on demand at the grid maximum", () => {
    const db = createStressDb(fixture, now);
    const finding = db.findings.find((entry) => entry.hasEvidence);
    if (!finding) throw new Error("no evidence finding");
    const evidence = db.evidenceOf(finding);
    if (!evidence) throw new Error("no evidence");
    const { before } = evidence;
    expect([before.sizeX, before.sizeY, before.sizeZ]).toEqual([stressVolume.size, stressVolume.size, stressVolume.size]);
    expect(before.cells).toHaveLength(stressVolume.size ** 3);
    expect(evidence.changes.length).toBeGreaterThanOrEqual(2500);
    expect(evidence.recordings.length).toBeGreaterThanOrEqual(2);
    for (const change of evidence.changes) {
      expect(change.x - before.minX).toBeGreaterThanOrEqual(0);
      expect(change.x - before.minX).toBeLessThan(before.sizeX);
      expect(change.y - before.minY).toBeLessThan(before.sizeY);
      expect(change.z - before.minZ).toBeLessThan(before.sizeZ);
    }
    expect(db.evidenceOf(finding)).toEqual(evidence);
  });
});
