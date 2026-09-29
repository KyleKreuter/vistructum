import { lazy, Suspense, useCallback, useMemo, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router";
import { ApiError, urls } from "@/api/client";
import { useFindings, usePlayer } from "@/api/queries";
import type { Paging } from "@/api/types";
import { FindingTable } from "@/components/app/FindingTable";
import { Pagination } from "@/components/app/Pagination";
import { PlayerFace } from "@/components/app/PlayerFace";
import { EmptyState, ErrorState, PageSpinner } from "@/components/app/States";
import { Card, CardContent } from "@/components/ui/card";
import { cn } from "@/lib/utils";
import { defaultFilter, isUuid, listParams, parsePaging } from "@/logic/filters";
import { formatPercent, precision, shortUuid } from "@/logic/format";

const SkinPreview = lazy(() => import("@/features/player/SkinPreview"));

function Count({ label, value, to }: { label: string; value: number; to: string }) {
  return (
    <Link to={to} className="rounded-lg border p-3 hover:border-ring">
      <div className="text-xs text-muted-foreground">{label}</div>
      <div className="text-xl font-semibold tabular-nums">{value.toLocaleString("en-GB")}</div>
    </Link>
  );
}

export default function PlayerPage() {
  const uuid = useParams().uuid ?? "";
  const valid = isUuid(uuid);
  const player = usePlayer(uuid);
  const filter = useMemo(() => ({ ...defaultFilter, state: "any" as const, player: uuid }), [uuid]);
  const [params, setParams] = useSearchParams();
  const paging = useMemo(() => parsePaging(params), [params]);
  const findings = useFindings(filter, paging);
  const items = findings.data?.items ?? [];
  const [missingSkin, setMissingSkin] = useState<string | null>(null);
  const onMissing = useCallback(() => setMissingSkin(uuid), [uuid]);
  const suffix = `?${listParams(filter, paging).toString()}`;
  const turn = (next: Paging) => setParams(listParams(filter, next));
  const listFor = (state: string) => `/findings?${new URLSearchParams({ state, player: uuid }).toString()}`;

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
    <div className="grid gap-4 lg:grid-cols-[300px_minmax(0,1fr)]">
      <div className="space-y-4">
        <Card className="gap-4 py-4">
          <CardContent className="space-y-4 px-4">
            <div className="flex items-center gap-3">
              <PlayerFace uuid={uuid} name={info.name} size={40} />
              <div className="min-w-0">
                <h1 className="truncate text-lg font-semibold">{info.name ?? "Unknown name"}</h1>
                <p className="truncate text-xs text-muted-foreground" title={uuid}>
                  {shortUuid(uuid)}
                </p>
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
        <div className="grid grid-cols-2 gap-2">
          <Count label="Findings" value={info.findings.total} to={listFor("any")} />
          <Count label="Open" value={info.findings.open} to={listFor("open")} />
          <Count label="Confirmed" value={info.findings.confirmed} to={listFor("confirmed")} />
          <Count label="False alarms" value={info.findings.falseAlarms} to={listFor("false_alarm")} />
        </div>
        <p className="text-xs text-muted-foreground">
          {formatPercent(precision(info.findings.confirmed, info.findings.falseAlarms))} of the reviewed findings with this player were confirmed.
        </p>
      </div>
      <div className="min-w-0 space-y-3">
        <h2 className="text-base font-semibold">Findings with {info.name ?? shortUuid(uuid)}</h2>
        {findings.isPending ? (
          <PageSpinner />
        ) : findings.isError ? (
          <ErrorState error={findings.error} onRetry={() => void findings.refetch()} />
        ) : items.length === 0 && findings.data.total === 0 ? (
          <EmptyState title="No findings" />
        ) : (
          <div className={cn("space-y-3", findings.isPlaceholderData && "opacity-60 transition-opacity")}>
            {items.length === 0 ? (
              <EmptyState title={`Page ${paging.page} is empty`} />
            ) : (
              <FindingTable items={items} linkSuffix={suffix} />
            )}
            <Pagination paging={paging} total={findings.data.total} onChange={turn} />
          </div>
        )}
      </div>
    </div>
  );
}
