import { useCallback, useMemo } from "react";
import { useSearchParams } from "react-router";
import type { FindingFilter, Paging } from "@/api/types";
import { useFindings } from "@/api/queries";
import { FindingFilters } from "@/components/app/FindingFilters";
import { FindingTable } from "@/components/app/FindingTable";
import { Pagination } from "@/components/app/Pagination";
import { EmptyState, ErrorState, PageHeader, PageSpinner } from "@/components/app/States";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { listParams, parseFilter, parsePaging } from "@/logic/filters";
import { pageCount } from "@/logic/pagination";

export default function FindingsPage() {
  const [params, setParams] = useSearchParams();
  const filter = useMemo(() => parseFilter(params), [params]);
  const paging = useMemo(() => parsePaging(params), [params]);
  const findings = useFindings(filter, paging);
  const items = findings.data?.items ?? [];
  const total = findings.data?.total;
  const playerName = items.flatMap((item) => item.players).find((player) => player.uuid === filter.player)?.name ?? null;
  const suffix = `?${listParams(filter, paging).toString()}`;

  const change = useCallback(
    (next: FindingFilter) => setParams(listParams(next, { page: 1, pageSize: paging.pageSize }), { replace: true }),
    [setParams, paging.pageSize],
  );
  const turn = useCallback((next: Paging) => setParams(listParams(filter, next)), [setParams, filter]);

  return (
    <div className="flex min-h-0 flex-1 flex-col gap-4">
      <PageHeader
        title="Findings"
        description={total === undefined ? "Loading…" : `${total.toLocaleString("en-GB")} ${total === 1 ? "finding" : "findings"} match the filter`}
      />
      <FindingFilters filter={filter} onChange={change} playerName={playerName} />
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
