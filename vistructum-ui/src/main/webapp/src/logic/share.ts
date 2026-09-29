import type { FindingSummary } from "@/api/types";

export type ShareView = "hidden" | "link" | "activate";

export function shareView(finding: Pick<FindingSummary, "shareUrl" | "review" | "hasEvidence">, canShare: boolean): ShareView {
  if (finding.shareUrl) return "link";
  if (!canShare) return "hidden";
  return finding.review?.verdict === "CONFIRMED" && finding.hasEvidence ? "activate" : "hidden";
}
