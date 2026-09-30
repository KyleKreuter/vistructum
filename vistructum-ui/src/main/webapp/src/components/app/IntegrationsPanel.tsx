import { ChevronDown, Undo2, UserSearch } from "lucide-react";
import { useState, type ReactNode } from "react";
import { toast } from "sonner";
import { errorMessage } from "@/api/client";
import { useAttribution, useMe, useRollback } from "@/api/queries";
import type { FindingDetail } from "@/api/types";
import { FindingPunishments, libertyBansLogo, PunishmentHistory } from "@/components/app/Punishments";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import type { Integration } from "@/logic/integrations";
import { attributionMessage, canAttribute, canRollBack, rollbackMessage } from "@/logic/blockLog";
import { formatDateTime } from "@/logic/format";
import { useStoredChoice } from "@/lib/useStoredChoice";
import { cn } from "@/lib/utils";

const coreProtectLogo = "https://cdn.modrinth.com/data/Lu3KuzdV/b2c4b7b0033ab09cc166f2848003ef3a02c70a83.png";

const integrationLabels: Record<Integration, { name: string; logo: string }> = {
  coreprotect: { name: "CoreProtect", logo: coreProtectLogo },
  libertybans: { name: "LibertyBans", logo: libertyBansLogo },
};

export function FindingIntegrations({
  finding,
  integrations,
  blockLog,
  canRollback,
}: {
  finding: FindingDetail;
  integrations: Integration[];
  blockLog: boolean;
  canRollback: boolean;
}) {
  const [selected, select] = useStoredChoice("vistructum-finding-integration", integrations, integrations[0]);
  return (
    <Card className="min-h-0 py-4">
      <CardContent className="flex min-h-0 flex-1 flex-col gap-3 px-4 text-sm">
        <ToggleGroup type="single" variant="outline" size="sm" value={selected} onValueChange={(value) => value && select(value)} className="shrink-0">
          {integrations.map((integration) => (
            <ToggleGroupItem key={integration} value={integration} className="gap-2">
              <img src={integrationLabels[integration].logo} alt="" className="size-4 rounded-sm" referrerPolicy="no-referrer" loading="lazy" />
              {integrationLabels[integration].name}
            </ToggleGroupItem>
          ))}
        </ToggleGroup>
        <div className="min-h-0 flex-1 overflow-y-auto max-lg:max-h-[70vh]">
          {selected === "coreprotect" ? <CoreProtectSection finding={finding} blockLog={blockLog} canRollback={canRollback} /> : <FindingPunishments players={finding.players} />}
        </div>
      </CardContent>
    </Card>
  );
}

export function PlayerIntegrationsPanel({ uuid }: { uuid: string }) {
  if (!useMe().data?.punishments) return null;
  return (
    <IntegrationsCard storageKey="vistructum-integrations-player" className="shrink-0">
      <PunishmentHistory uuid={uuid} />
    </IntegrationsCard>
  );
}

function readCollapsed(storageKey: string): boolean {
  try {
    return localStorage.getItem(storageKey) === "collapsed";
  } catch {
    return false;
  }
}

function storeCollapsed(storageKey: string, collapsed: boolean) {
  try {
    if (collapsed) localStorage.setItem(storageKey, "collapsed");
    else localStorage.removeItem(storageKey);
  } catch {
    return;
  }
}

function IntegrationsCard({ storageKey, className, children }: { storageKey: string; className?: string; children: ReactNode }) {
  const [collapsed, setCollapsed] = useState(() => readCollapsed(storageKey));
  const toggle = () => {
    setCollapsed(!collapsed);
    storeCollapsed(storageKey, !collapsed);
  };
  return (
    <Card className={cn("gap-3 py-4", className)}>
      <CardHeader className="px-4">
        <CardTitle className="text-sm">
          <button type="button" onClick={toggle} aria-expanded={!collapsed} className="flex w-full items-center gap-2 text-left">
            Integrations
            <ChevronDown className={cn("ml-auto size-4 text-muted-foreground transition-transform", collapsed && "-rotate-90")} />
          </button>
        </CardTitle>
      </CardHeader>
      {!collapsed && <CardContent className="flex flex-col gap-3 px-4 text-sm">{children}</CardContent>}
    </Card>
  );
}

function CoreProtectSection({ finding, blockLog, canRollback }: { finding: FindingDetail; blockLog: boolean; canRollback: boolean }) {
  const attribution = useAttribution(finding.id);
  const rollback = useRollback(finding.id);
  const [confirming, setConfirming] = useState(false);
  const busy = attribution.isPending || rollback.isPending;
  const rollbackable = canRollBack(finding);
  const attributable = canAttribute(finding);

  const attribute = () =>
    attribution.mutate(undefined, {
      onSuccess: (summary) => {
        const names = summary.players.map((player) => player.name ?? player.uuid.slice(0, 8));
        if (names.length) toast.success(attributionMessage(names));
        else toast.info("CoreProtect logged no builders for this box.");
      },
      onError: (error) => toast.error(errorMessage(error)),
    });

  const roll = () =>
    rollback.mutate(undefined, {
      onSuccess: (result) => {
        setConfirming(false);
        toast.success(rollbackMessage(result.restored, result.skipped));
      },
      onError: (error) => {
        setConfirming(false);
        toast.error(errorMessage(error));
      },
    });

  return (
    <section className="space-y-2">
      {!blockLog && <p className="text-xs text-muted-foreground">CoreProtect is not available right now.</p>}
      {finding.rolledBackAt && (
        <p className="text-xs">
          Rolled back by {finding.rolledBackBy}, {formatDateTime(finding.rolledBackAt)}
        </p>
      )}
      {!blockLog ? null : confirming ? (
        <div className="space-y-2">
          <p className="text-xs text-muted-foreground">
            Every block in this box that the listed builders placed or broke returns to its state before their first logged change. Blocks outside the box and blocks changed by others since stay. The finding and its evidence stay.
          </p>
          <div className="flex flex-wrap gap-2">
            <Button size="sm" variant="destructive" onClick={roll} disabled={busy}>
              <Undo2 /> Confirm rollback
            </Button>
            <Button size="sm" variant="outline" onClick={() => setConfirming(false)} disabled={busy}>
              Cancel
            </Button>
          </div>
        </div>
      ) : (
        <div className="flex flex-wrap gap-2">
          <DisabledReason reason={attributable ? null : "All builders of this finding were found already."}>
            <Button size="sm" variant="outline" onClick={attribute} disabled={busy || !attributable}>
              <UserSearch /> Find builders
            </Button>
          </DisabledReason>
          {canRollback && (
            <DisabledReason reason={rollbackable ? null : finding.rolledBackAt ? "This finding was rolled back already." : "Only confirmed findings with builders can be rolled back."}>
              <Button size="sm" variant="outline" className="text-destructive" onClick={() => setConfirming(true)} disabled={busy || !rollbackable}>
                <Undo2 /> Roll back
              </Button>
            </DisabledReason>
          )}
        </div>
      )}
    </section>
  );
}

function DisabledReason({ reason, children }: { reason: string | null; children: ReactNode }) {
  if (!reason) return children;
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <span tabIndex={0} className="inline-flex rounded-md">
          {children}
        </span>
      </TooltipTrigger>
      <TooltipContent>{reason}</TooltipContent>
    </Tooltip>
  );
}
