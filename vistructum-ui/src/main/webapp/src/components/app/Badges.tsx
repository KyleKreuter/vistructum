import { Share2, Video } from "lucide-react";
import type { FindingSummary, Source } from "@/api/types";
import { Badge } from "@/components/ui/badge";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { formatDateTime } from "@/logic/format";

export function VerdictBadge({ review, className }: { review: FindingSummary["review"]; className?: string }) {
  if (!review) {
    return (
      <Badge variant="amber" className={className}>
        Open
      </Badge>
    );
  }
  const confirmed = review.verdict === "CONFIRMED";
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <Badge variant={confirmed ? "rose" : "green"} className={className}>
          {confirmed ? "Confirmed" : "False alarm"}
        </Badge>
      </TooltipTrigger>
      <TooltipContent>
        {review.reviewer}, {formatDateTime(review.reviewedAt)}
      </TooltipContent>
    </Tooltip>
  );
}

export function RolledBackBadge({ finding, className }: { finding: Pick<FindingSummary, "rolledBackAt" | "rolledBackBy">; className?: string }) {
  if (!finding.rolledBackAt) return null;
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <Badge variant="slate" className={className}>
          Rolled back
        </Badge>
      </TooltipTrigger>
      <TooltipContent>
        CoreProtect rollback by {finding.rolledBackBy}, {formatDateTime(finding.rolledBackAt)}
      </TooltipContent>
    </Tooltip>
  );
}

export function SourceBadge({ source }: { source: Source }) {
  return (
    <Badge variant={source === "mask" ? "blue" : "violet"} className="text-[11px]">
      {source === "mask" ? "live" : "fullscan"}
    </Badge>
  );
}

export function IndicatorIcons({ finding }: { finding: Pick<FindingSummary, "hasEvidence" | "sharedSince" | "shareUrl"> }) {
  return (
    <span className="inline-flex items-center gap-1.5 text-muted-foreground">
      {finding.hasEvidence && (
        <Tooltip>
          <TooltipTrigger asChild>
            <Video className="size-4" aria-label="Has evidence" />
          </TooltipTrigger>
          <TooltipContent>Evidence recording available</TooltipContent>
        </Tooltip>
      )}
      {finding.shareUrl && (
        <Tooltip>
          <TooltipTrigger asChild>
            <Share2 className="size-4 text-primary" aria-label="Shared" />
          </TooltipTrigger>
          <TooltipContent>{finding.sharedSince ? `Public link active since ${formatDateTime(finding.sharedSince)}` : "Public link active"}</TooltipContent>
        </Tooltip>
      )}
    </span>
  );
}
