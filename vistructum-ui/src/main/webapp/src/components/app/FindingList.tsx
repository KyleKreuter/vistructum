import { useCallback, useMemo, type ReactNode } from "react";
import { useSearchParams } from "react-router";
import type { FindingFilter, FindingState, Paging } from "@/api/types";
import { useFindings } from "@/api/queries";
import { FindingFilters } from "@/components/app/FindingFilters";
import { FindingTable } from "@/components/app/FindingTable";
import { Pagination } from "@/components/app/Pagination";
import { EmptyState, ErrorState, PageSpinner } from "@/components/app/States";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { listParams, parseFilter, parsePaging } from "@/logic/filters";
import { pageCount } from "@/logic/pagination";

const playerDefaultState: FindingState = "any";

export function FindingList({ player, header }: { player?: string; header: (total: number | undefined) => ReactNode }) {
  const [params, setParams] = useSearchParams();
  const filter = useMemo<FindingFilter>(() => {
    const parsed = parseFilter(params);
    if (!player) return parsed;
    return { ...parsed, player, state: params.has("state") ? parsed.state : playerDefaultState };
  }, [params, player]);
  const paging = useMemo(() => parsePaging(params), [params]);
  const findings = useFindings(filter, paging);
  const items = findings.data?.items ?? [];
  const total = findings.data?.total;
  const playerName = items.flatMap((item) => item.players).find((entry) => entry.uuid === filter.player)?.name ?? null;
  const suffix = `?${listParams(filter, paging).toString()}`;

  const write = useCallback(
    (next: FindingFilter, nextPaging: Paging, replace: boolean) => {
      const search = listParams(next, nextPaging);
      if (player) search.delete("player");
      setParams(search, { replace });
    },
    [setParams, player],
  );
  const change = useCallback((next: FindingFilter) => write(next, { page: 1, pageSize: paging.pageSize }, true), [write, paging.pageSize]);
  const turn = useCallback((next: Paging) => write(filter, next, false), [write, filter]);

  return (
    <div className="flex min-h-0 flex-1 flex-col gap-4">
      {header(total)}
      <FindingFilters filter={filter} onChange={change} playerName={playerName} hidePlayer={!!player} defaultState={player ? playerDefaultState : undefined} />
      {findings.isPending ? (
        <PageSpinner />
      ) : findings.isError ? (
        <ErrorState error={findings.error} onRetry={() => void findings.refetch()} />
      ) : items.length === 0 && (total ?? 0) > 0 ? (
        <EmptyState title={`Page ${paging.page} is empty`}>
          <Button variant="outline" size="sm" className="mt-2" onClick={() => turn({ page: pageCount(total ?? 0, paging.pageSize), pageSize: paging.pageSize })}>
            Go to the last page
          </Button>
        </EmptyState>
      ) : items.length === 0 ? (
        <EmptyState title="No findings">Nothing matches this filter.</EmptyState>
      ) : (
        <div className={cn("flex min-h-0 flex-1 flex-col gap-3", findings.isPlaceholderData && "opacity-60 transition-opacity")}>
          <FindingTable key={`${paging.page}-${paging.pageSize}`} items={items} linkSuffix={suffix} fill className="flex-1" />
          <Pagination paging={paging} total={total ?? items.length} onChange={turn} />
        </div>
      )}
    </div>
  );
}
