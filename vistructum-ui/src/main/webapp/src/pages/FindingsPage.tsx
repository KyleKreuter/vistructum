import { useCallback, useMemo } from "react";
import { useSearchParams } from "react-router";
import type { FindingFilter } from "@/api/types";
import { useFindings } from "@/api/queries";
import { FindingFilters } from "@/components/app/FindingFilters";
import { FindingTable } from "@/components/app/FindingTable";
import { EmptyState, ErrorState, PageHeader, PageSpinner } from "@/components/app/States";
import { Button } from "@/components/ui/button";
import { filterParams, parseFilter } from "@/logic/filters";

export default function FindingsPage() {
  const [params, setParams] = useSearchParams();
  const filter = useMemo(() => parseFilter(params), [params]);
  const findings = useFindings(filter);
  const items = useMemo(() => findings.data?.pages.flatMap((page) => page.items) ?? [], [findings.data]);
  const total = findings.data?.pages[0]?.total;
  const playerName = items.flatMap((item) => item.players).find((player) => player.uuid === filter.player)?.name ?? null;
  const suffix = `?${filterParams(filter).toString()}`;

  const change = useCallback((next: FindingFilter) => setParams(filterParams(next), { replace: true }), [setParams]);

  return (
    <div className="space-y-4">
      <PageHeader
        title="Findings"
        description={total === undefined ? "Loading…" : `${total.toLocaleString("en-GB")} ${total === 1 ? "finding" : "findings"} match the filter`}
      />
      <FindingFilters filter={filter} onChange={change} playerName={playerName} />
      {findings.isPending ? (
        <PageSpinner />
      ) : findings.isError ? (
        <ErrorState error={findings.error} onRetry={() => void findings.refetch()} />
      ) : items.length === 0 ? (
        <EmptyState title="No findings">Nothing matches this filter.</EmptyState>
      ) : (
        <div className={findings.isPlaceholderData ? "opacity-60 transition-opacity" : undefined}>
          <FindingTable items={items} linkSuffix={suffix} />
          <div className="mt-3 flex items-center justify-between gap-3 text-sm text-muted-foreground">
            <span>
              Showing {items.length.toLocaleString("en-GB")} of {(total ?? items.length).toLocaleString("en-GB")}
            </span>
            {findings.hasNextPage && (
              <Button variant="outline" size="sm" onClick={() => void findings.fetchNextPage()} disabled={findings.isFetchingNextPage}>
                {findings.isFetchingNextPage ? "Loading…" : "Load more"}
              </Button>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
