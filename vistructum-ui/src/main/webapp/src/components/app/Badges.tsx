import { Share2, Video } from "lucide-react";
import type { FindingSummary, Source } from "@/api/types";
import { Badge } from "@/components/ui/badge";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { formatDateTime } from "@/logic/format";
import { cn } from "@/lib/utils";

export function VerdictBadge({ review, className }: { review: FindingSummary["review"]; className?: string }) {
  if (!review) {
    return (
      <Badge variant="outline" className={cn("border-open/50 bg-open/10 text-foreground", className)}>
        <span className="size-1.5 rounded-full bg-open" />
        Open
      </Badge>
    );
  }
  const confirmed = review.verdict === "CONFIRMED";
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <Badge
          variant="outline"
          className={cn(confirmed ? "border-confirmed/50 bg-confirmed/10" : "border-false-alarm/50 bg-false-alarm/10", "text-foreground", className)}
        >
          <span className={cn("size-1.5 rounded-full", confirmed ? "bg-confirmed" : "bg-false-alarm")} />
          {confirmed ? "Confirmed" : "False alarm"}
        </Badge>
      </TooltipTrigger>
      <TooltipContent>
        {review.reviewer}, {formatDateTime(review.reviewedAt)}
      </TooltipContent>
    </Tooltip>
  );
}

export function SourceBadge({ source }: { source: Source }) {
  return (
    <Badge variant="secondary" className="font-mono text-[11px] font-normal">
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
