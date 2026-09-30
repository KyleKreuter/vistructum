import { History, Plug, Undo2, UserSearch } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { errorMessage } from "@/api/client";
import { useAttribution, useRollback } from "@/api/queries";
import type { FindingDetail } from "@/api/types";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { canRollBack } from "@/logic/blockLog";
import { formatDateTime } from "@/logic/format";

export function IntegrationsPanel({ finding, blockLog, canRollback }: { finding: FindingDetail; blockLog: boolean; canRollback: boolean }) {
  return (
    <Card className="gap-3 py-4">
      <CardHeader className="px-4">
        <CardTitle className="flex items-center gap-2 text-sm">
          <Plug className="size-4" /> Integrations
        </CardTitle>
      </CardHeader>
      <CardContent className="px-4 text-sm">
        <CoreProtectSection finding={finding} blockLog={blockLog} canRollback={canRollback} />
      </CardContent>
    </Card>
  );
}

function CoreProtectSection({ finding, blockLog, canRollback }: { finding: FindingDetail; blockLog: boolean; canRollback: boolean }) {
  const attribution = useAttribution(finding.id);
  const rollback = useRollback(finding.id);
  const [confirming, setConfirming] = useState(false);
  const busy = attribution.isPending || rollback.isPending;
  const rollbackable = canRollBack(finding);

  const attribute = () =>
    attribution.mutate(undefined, {
      onSuccess: (summary) => {
        const names = summary.players.map((player) => player.name ?? player.uuid.slice(0, 8));
        if (names.length) toast.success(`Builders: ${names.join(", ")}`);
        else toast.info("CoreProtect logged no builders for this box.");
      },
      onError: (error) => toast.error(errorMessage(error)),
    });

  const roll = () =>
    rollback.mutate(undefined, {
      onSuccess: (result) => {
        setConfirming(false);
        toast.success(result.changes === 1 ? "Rolled back 1 block change." : `Rolled back ${result.changes} block changes.`);
      },
      onError: (error) => {
        setConfirming(false);
        toast.error(errorMessage(error));
      },
    });

  return (
    <section className="space-y-2">
      <h3 className="flex items-center gap-2 text-xs font-medium text-muted-foreground">
        <History className="size-3.5" /> CoreProtect
      </h3>
      {finding.rolledBackAt && (
        <p className="text-xs">
          Rolled back by {finding.rolledBackBy}, {formatDateTime(finding.rolledBackAt)}
        </p>
      )}
      {!blockLog ? (
        <p className="text-xs text-muted-foreground">CoreProtect is not available right now.</p>
      ) : confirming ? (
        <div className="space-y-2">
          <p className="text-xs text-muted-foreground">
            CoreProtect reverts every block the listed builders placed or broke in this box since their first logged change. The finding and its evidence stay.
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
          <Button size="sm" variant="outline" onClick={attribute} disabled={busy}>
            <UserSearch /> Find builders
          </Button>
          {canRollback && (
            <Button
              size="sm"
              variant="outline"
              className="text-destructive"
              onClick={() => setConfirming(true)}
              disabled={busy || !rollbackable}
              title={rollbackable ? undefined : "Only confirmed findings with builders can be rolled back."}
            >
              <Undo2 /> Roll back
            </Button>
          )}
        </div>
      )}
    </section>
  );
}
