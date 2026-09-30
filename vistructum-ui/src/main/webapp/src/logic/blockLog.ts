import type { FindingSummary } from "@/api/types";

export function canRollBack(finding: Pick<FindingSummary, "review" | "players" | "rolledBackAt">): boolean {
  return finding.review?.verdict === "CONFIRMED" && finding.players.length > 0 && !finding.rolledBackAt;
}

export function canAttribute(finding: Pick<FindingSummary, "players" | "hasEvidence">): boolean {
  return finding.players.length === 0 || !finding.hasEvidence;
}

export function rollbackMessage(restored: number, skipped: number): string {
  const blocks = (count: number) => (count === 1 ? "1 block" : `${count} blocks`);
  const main = restored === 0 ? "No block needed a rollback." : `Rolled back ${blocks(restored)}.`;
  return skipped === 0 ? main : `${main} Skipped ${blocks(skipped)} that others changed since.`;
}

export function attributionMessage(names: string[]): string {
  return names.length > 2 ? `Found ${names.length} builders.` : `Builders: ${names.join(", ")}`;
}
