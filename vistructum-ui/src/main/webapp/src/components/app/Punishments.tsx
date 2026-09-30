import { errorMessage } from "@/api/client";
import { Info } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router";
import { useMe, usePlayersPunishments, usePunishments } from "@/api/queries";
import type { PlayerRef, Punishment } from "@/api/types";
import { Badge } from "@/components/ui/badge";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { formatDateTime, playerLabel } from "@/logic/format";
import { activeStatuses, expires, expiryLabel, inForce, punishmentTypeLabel } from "@/logic/punishments";
import { cn } from "@/lib/utils";

const typeVariants = { BAN: "rose", MUTE: "amber", WARN: "violet", KICK: "slate" } as const;

export function PunishmentBadges({ uuid, className }: { uuid: string; className?: string }) {
  const me = useMe();
  const history = usePunishments(uuid, !!me.data?.punishments);
  const statuses = history.data ? activeStatuses(history.data.items) : [];
  if (!statuses.length) return null;
  return (
    <span className={cn("flex flex-wrap gap-1", className)}>
      {statuses.map((status) => (
        <Badge key={status.type} variant={typeVariants[status.type]}>
          {status.label}
        </Badge>
      ))}
    </span>
  );
}

const libertyBansLogo = "https://cdn.modrinth.com/data/PgXAUxLZ/icon.png";

function LibertyBansHeading() {
  return (
    <h3 className="flex items-center gap-2 text-xs font-medium text-muted-foreground">
      <img src={libertyBansLogo} alt="" className="size-4 rounded-sm" referrerPolicy="no-referrer" loading="lazy" /> LibertyBans
    </h3>
  );
}

export function PunishmentHistory({ uuid }: { uuid: string }) {
  const history = usePunishments(uuid, true);
  return (
    <section className="space-y-2">
      <LibertyBansHeading />
      {history.isPending ? (
        <p className="text-muted-foreground">Loading…</p>
      ) : history.isError ? (
        <p className="text-destructive">{errorMessage(history.error)}</p>
      ) : !history.data.items.length ? (
        <p className="text-muted-foreground">No punishments.</p>
      ) : (
        <div className="rounded-lg border">
          <Table containerClassName="max-h-72 overflow-y-auto">
            <TableHeader className="sticky top-0 bg-card">
              <TableRow>
                <TableHead>Type</TableHead>
                <TableHead className="w-full">Reason</TableHead>
                <TableHead>Staff</TableHead>
                <TableHead>Issued</TableHead>
                <TableHead>Expires</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {history.data.items.map((punishment, index) => (
                <PunishmentRow key={index} punishment={punishment} />
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </section>
  );
}

export function FindingPunishments({ players }: { players: PlayerRef[] }) {
  const histories = usePlayersPunishments(
    players.map((player) => player.uuid),
    true,
  );
  const entries = players
    .flatMap((player, index) => (histories[index].data?.items ?? []).map((punishment) => ({ player, punishment })))
    .sort((a, b) => (b.punishment.issuedAt ?? "").localeCompare(a.punishment.issuedAt ?? ""));
  const failed = histories.find((history) => history.isError);
  return (
    <section className="space-y-2">
      <LibertyBansHeading />
      {!players.length ? (
        <p className="text-muted-foreground">No players recorded.</p>
      ) : histories.some((history) => history.isPending) ? (
        <p className="text-muted-foreground">Loading…</p>
      ) : failed && !entries.length ? (
        <p className="text-destructive">{errorMessage(failed.error)}</p>
      ) : !entries.length ? (
        <p className="text-muted-foreground">No punishments.</p>
      ) : (
        <ul className="max-h-56 divide-y overflow-y-auto rounded-lg border">
          {entries.map(({ player, punishment }, index) => (
            <FindingPunishmentEntry key={index} player={player} punishment={punishment} />
          ))}
        </ul>
      )}
    </section>
  );
}

function FindingPunishmentEntry({ player, punishment }: { player: PlayerRef; punishment: Punishment }) {
  const current = inForce(punishment);
  const [details, setDetails] = useState(false);
  return (
    <li className={cn("flex items-center gap-2 px-3 py-2", !current && "text-muted-foreground")}>
      <Badge variant={current ? typeVariants[punishment.type] : "slate"}>{punishmentTypeLabel(punishment.type)}</Badge>
      <Link to={`/players/${player.uuid}`} className="min-w-0 truncate font-medium hover:underline" title={playerLabel(player)}>
        {playerLabel(player)}
      </Link>
      <Tooltip open={details} onOpenChange={setDetails}>
        <TooltipTrigger asChild>
          <button
            type="button"
            aria-label="Punishment details"
            className="ml-auto shrink-0 rounded-sm text-muted-foreground hover:text-foreground"
            onPointerDown={(event) => event.preventDefault()}
            onClick={() => setDetails((open) => !open)}
          >
            <Info className="size-3.5" />
          </button>
        </TooltipTrigger>
        <TooltipContent side="left" className="max-w-64">
          <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-0.5">
            <dt className="opacity-70">Reason</dt>
            <dd className={cn("break-words", !punishment.reason && "italic")}>{punishment.reason ?? "No reason"}</dd>
            <dt className="opacity-70">Staff</dt>
            <dd>{punishment.operator ?? "Unknown"}</dd>
            <dt className="opacity-70">Issued</dt>
            <dd className="tabular-nums">{formatDateTime(punishment.issuedAt)}</dd>
            {expires(punishment) && (
              <>
                <dt className="opacity-70">Expires</dt>
                <dd className="tabular-nums">{expiryLabel(punishment)}</dd>
              </>
            )}
            {(punishment.type === "BAN" || punishment.type === "MUTE") && (
              <>
                <dt className="opacity-70">Status</dt>
                <dd>{current ? "Active" : "Ended"}</dd>
              </>
            )}
          </dl>
        </TooltipContent>
      </Tooltip>
    </li>
  );
}

function PunishmentRow({ punishment }: { punishment: Punishment }) {
  const current = inForce(punishment);
  return (
    <TableRow className={cn(!current && "text-muted-foreground")}>
      <TableCell>
        <span className="flex items-center gap-2">
          <Badge variant={current ? typeVariants[punishment.type] : "slate"}>{punishmentTypeLabel(punishment.type)}</Badge>
          {current && <span className="text-xs font-medium">Active</span>}
        </span>
      </TableCell>
      <TableCell className={cn("max-w-0 truncate", !punishment.reason && "italic")} title={punishment.reason ?? undefined}>
        {punishment.reason ?? "No reason"}
      </TableCell>
      <TableCell>{punishment.operator ?? "Unknown"}</TableCell>
      <TableCell className="tabular-nums">{formatDateTime(punishment.issuedAt)}</TableCell>
      <TableCell className="tabular-nums">{expiryLabel(punishment)}</TableCell>
    </TableRow>
  );
}
