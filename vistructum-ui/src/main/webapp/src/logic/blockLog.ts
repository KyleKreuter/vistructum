import type { FindingSummary } from "@/api/types";

export function canRollBack(finding: Pick<FindingSummary, "review" | "players">): boolean {
  return finding.review?.verdict === "CONFIRMED" && finding.players.length > 0;
}
