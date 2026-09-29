import { useNavigate } from "react-router";
import type { FindingSummary } from "@/api/types";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { boxCentre } from "@/logic/coords";
import { formatDateTime, formatRelative, formatScore } from "@/logic/format";
import { IndicatorIcons, SourceBadge, VerdictBadge } from "./Badges";
import { PlayerChip } from "./PlayerFace";
import { Thumbnail } from "./Thumbnail";

export function FindingTable({ items, linkSuffix = "" }: { items: FindingSummary[]; linkSuffix?: string }) {
  const navigate = useNavigate();
  return (
    <div className="overflow-hidden rounded-lg border bg-card">
      <Table>
        <TableHeader>
          <TableRow className="hover:bg-transparent">
            <TableHead className="w-20">Finding</TableHead>
            <TableHead />
            <TableHead>Verdict</TableHead>
            <TableHead className="text-right">Score</TableHead>
            <TableHead className="hidden lg:table-cell">Location</TableHead>
            <TableHead>Players</TableHead>
            <TableHead className="text-right">Created</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {items.map((finding) => {
            const centre = boxCentre(finding.box);
            const href = `/findings/${finding.id}${linkSuffix}`;
            return (
              <TableRow
                key={finding.id}
                className="cursor-pointer"
                tabIndex={0}
                onClick={() => void navigate(href)}
                onKeyDown={(event) => {
                  if (event.key === "Enter") void navigate(href);
                }}
              >
                <TableCell className="py-2">
                  <Thumbnail id={finding.id} className="size-14" />
                </TableCell>
                <TableCell className="py-2">
                  <div className="flex flex-col gap-1">
                    <span className="font-mono text-sm font-semibold">#{finding.id}</span>
                    <span className="flex items-center gap-2">
                      <SourceBadge source={finding.source} />
                      <IndicatorIcons finding={finding} />
                    </span>
                  </div>
                </TableCell>
                <TableCell>
                  <VerdictBadge review={finding.review} />
                </TableCell>
                <TableCell className="text-right font-mono tabular-nums">
                  <div>{formatScore(finding.score)}</div>
                  <div className="text-xs text-muted-foreground">{finding.modelVersion}</div>
                </TableCell>
                <TableCell className="hidden font-mono text-xs text-muted-foreground lg:table-cell">
                  <div className="text-foreground">{finding.world}</div>
                  <div className="tabular-nums">
                    {centre.x} {centre.y} {centre.z}
                  </div>
                </TableCell>
                <TableCell className="max-w-56">
                  <div className="flex flex-col gap-1">
                    {finding.players.slice(0, 2).map((player) => (
                      <PlayerChip key={player.uuid} player={player} />
                    ))}
                    {finding.players.length > 2 && <span className="text-xs text-muted-foreground">+{finding.players.length - 2} more</span>}
                    {finding.players.length === 0 && <span className="text-xs text-muted-foreground">None recorded</span>}
                  </div>
                </TableCell>
                <TableCell className="text-right text-sm whitespace-nowrap text-muted-foreground">
                  <Tooltip>
                    <TooltipTrigger asChild>
                      <span>{formatRelative(finding.createdAt)}</span>
                    </TooltipTrigger>
                    <TooltipContent>{formatDateTime(finding.createdAt)}</TooltipContent>
                  </Tooltip>
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </div>
  );
}
