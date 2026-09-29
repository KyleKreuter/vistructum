import { createDefaultDb, loadFixture, type Fixture, type MockDb } from "./data";
import { createEmptyDb } from "./scenarios/empty";
import { createSparseDb } from "./scenarios/sparse";
import { createStressDb } from "./scenarios/stress";
import type { Scenario } from "./settings";

const builders: Record<Scenario, (fixture: Fixture, now: number) => MockDb> = {
  default: createDefaultDb,
  empty: createEmptyDb,
  sparse: createSparseDb,
  stress: createStressDb,
};

export async function createDb(scenario: Scenario, now = Date.now()): Promise<MockDb> {
  return builders[scenario](await loadFixture(), now);
}
