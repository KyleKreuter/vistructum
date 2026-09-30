import { lazy, Suspense, useCallback, useState } from "react";
import { useParams } from "react-router";
import { ApiError, urls } from "@/api/client";
import { usePlayer } from "@/api/queries";
import { FindingList } from "@/components/app/FindingList";
import { PlayerFace } from "@/components/app/PlayerFace";
import { PunishmentBadges, PunishmentHistoryCard } from "@/components/app/Punishments";
import { EmptyState, ErrorState, PageSpinner } from "@/components/app/States";
import { Card, CardContent } from "@/components/ui/card";
import { isUuid } from "@/logic/filters";
import { shortUuid } from "@/logic/format";

const SkinPreview = lazy(() => import("@/features/player/SkinPreview"));

export default function PlayerPage() {
  const uuid = useParams().uuid ?? "";
  const valid = isUuid(uuid);
  const player = usePlayer(uuid, valid);
  const [missingSkin, setMissingSkin] = useState<string | null>(null);
  const onMissing = useCallback(() => setMissingSkin(uuid), [uuid]);

  if (!valid) return <EmptyState title="This is not a player id" />;
  if (player.isPending) return <PageSpinner />;
  if (player.isError || !player.data) {
    return player.error instanceof ApiError && player.error.status === 404 ? (
      <EmptyState title="Unknown player">No finding involves this player.</EmptyState>
    ) : (
      <ErrorState error={player.error} onRetry={() => void player.refetch()} />
    );
  }
  const info = player.data;

  return (
    <div className="grid gap-4 lg:min-h-0 lg:flex-1 lg:grid-cols-[300px_minmax(0,1fr)]">
      <div className="space-y-4 lg:min-h-0 lg:overflow-y-auto">
        <Card className="gap-4 py-4">
          <CardContent className="space-y-4 px-4">
            <div className="flex items-center gap-3">
              <PlayerFace uuid={uuid} name={info.name} size={40} />
              <div className="min-w-0">
                <h1 className="truncate text-lg font-semibold">{info.name ?? "Unknown name"}</h1>
                <p className="truncate text-xs text-muted-foreground" title={uuid}>
                  {shortUuid(uuid)}
                </p>
                <PunishmentBadges uuid={uuid} className="mt-1" />
              </div>
            </div>
            {missingSkin === uuid ? (
              <div className="flex h-72 items-center justify-center text-sm text-muted-foreground">No skin available</div>
            ) : (
              <Suspense fallback={<PageSpinner className="h-72" />}>
                <SkinPreview url={urls.skin(uuid)} onMissing={onMissing} />
              </Suspense>
            )}
          </CardContent>
        </Card>
        <PunishmentHistoryCard uuid={uuid} />
      </div>
      <div className="flex min-w-0 flex-col lg:min-h-0">
        <FindingList
          player={uuid}
          header={(total) => (
            <h2 className="text-base font-semibold">
              Findings with {info.name ?? shortUuid(uuid)}
              {total !== undefined && <span className="ml-2 text-sm font-normal text-muted-foreground tabular-nums">{total.toLocaleString("en-GB")}</span>}
            </h2>
          )}
        />
      </div>
    </div>
  );
}
