import { expiresIn, mockPalette, staff, storedEvidence, type Fixture, type MockDb } from "../data";

export function createEmptyDb(fixture: Fixture, now = Date.now()): MockDb {
  return {
    me: { player: staff.uuid, name: staff.name, expiresAt: expiresIn(now), canShare: true, blockLog: true, canRollback: true },
    findings: [],
    players: [],
    palette: mockPalette(fixture),
    activity: [],
    scans: [],
    shares: new Map(),
    status: {
      trackedChanges: 0,
      recordingEnabled: false,
      texturesAvailable: false,
      inference: { mode: "LOCAL", available: false, models: [], detail: "No model is loaded." },
    },
    evidenceOf: storedEvidence,
  };
}
