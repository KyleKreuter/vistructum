import { Gavel } from "lucide-react";
import { errorMessage } from "@/api/client";
import { useMe, usePunishments } from "@/api/queries";
import type { Punishment } from "@/api/types";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { formatDateTime } from "@/logic/format";
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

export function PunishmentHistoryCard({ uuid }: { uuid: string }) {
  const me = useMe();
  const history = usePunishments(uuid, !!me.data?.punishments);
  if (!me.data?.punishments) return null;
  return (
    <Card className="gap-3 py-4">
      <CardHeader className="px-4">
        <CardTitle className="flex items-center gap-2 text-sm">
          <Gavel className="size-4" /> Punishments
          <span className="ml-auto text-xs font-normal text-muted-foreground">{history.data?.source ?? me.data.punishments}</span>
        </CardTitle>
      </CardHeader>
      <CardContent className="px-4 text-sm">
        {history.isPending ? (
          <p className="text-muted-foreground">Loading…</p>
        ) : history.isError ? (
          <p className="text-destructive">{errorMessage(history.error)}</p>
        ) : !history.data.items.length ? (
          <p className="text-muted-foreground">No punishments.</p>
        ) : (
          <ul className="flex flex-col gap-2">
            {history.data.items.map((punishment, index) => (
              <PunishmentEntry key={index} punishment={punishment} />
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}

function PunishmentEntry({ punishment }: { punishment: Punishment }) {
  const current = inForce(punishment);
  return (
    <li className={cn("rounded-lg border px-3 py-2", !current && "text-muted-foreground")}>
      <div className="flex items-center gap-2">
        <Badge variant={current ? typeVariants[punishment.type] : "slate"}>{punishmentTypeLabel(punishment.type)}</Badge>
        {current && <span className="text-xs font-medium">Active</span>}
        <span className="ml-auto text-xs tabular-nums">{formatDateTime(punishment.issuedAt)}</span>
      </div>
      <p className={cn("mt-1 break-words", !punishment.reason && "italic")}>{punishment.reason ?? "No reason"}</p>
      <dl className="mt-1 grid grid-cols-[auto_1fr] gap-x-2 text-xs text-muted-foreground">
        <dt>Staff</dt>
        <dd className="truncate">{punishment.operator ?? "Unknown"}</dd>
        {expires(punishment) && (
          <>
            <dt>Expires</dt>
            <dd className="tabular-nums">{expiryLabel(punishment)}</dd>
          </>
        )}
      </dl>
    </li>
  );
}
