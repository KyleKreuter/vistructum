import { Check, History, Link2, Link2Off, Undo2, X } from "lucide-react";
import { useMemo } from "react";
import { Link, useSearchParams } from "react-router";
import { useActivity } from "@/api/queries";
import type { ActivityKind, Paging } from "@/api/types";
import { Pagination } from "@/components/app/Pagination";
import { EmptyState, ErrorState, PageHeader, PageSpinner } from "@/components/app/States";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { parsePaging, pagingParams } from "@/logic/filters";
import { formatDateTime, formatRelative } from "@/logic/format";
import { pageCount } from "@/logic/pagination";
import { cn } from "@/lib/utils";

const kinds: Record<ActivityKind, { label: string; icon: typeof Check; tone: string }> = {
  CONFIRMED: { label: "confirmed", icon: Check, tone: "text-confirmed" },
  FALSE_ALARM: { label: "marked as false alarm", icon: X, tone: "text-false-alarm" },
  SHARED: { label: "activated the public link of", icon: Link2, tone: "text-primary" },
  UNSHARED: { label: "deactivated the public link of", icon: Link2Off, tone: "text-muted-foreground" },
  ATTRIBUTED: { label: "found the builders through CoreProtect for", icon: History, tone: "text-primary" },
  ROLLED_BACK: { label: "rolled back through CoreProtect", icon: Undo2, tone: "text-destructive" },
};

export default function ActivityPage() {
  const [params, setParams] = useSearchParams();
  const paging = useMemo(() => parsePaging(params), [params]);
  const activity = useActivity(paging);
  const items = activity.data?.items ?? [];
  const total = activity.data?.total ?? 0;
  const turn = (next: Paging) => setParams(pagingParams(next));
  return (
    <div className="space-y-4">
      <PageHeader title="Activity" description="Verdicts and public links, newest first" />
      {activity.isPending ? (
        <PageSpinner />
      ) : activity.isError ? (
        <ErrorState error={activity.error} onRetry={() => void activity.refetch()} />
      ) : items.length === 0 && total > 0 ? (
        <EmptyState title={`Page ${paging.page} is empty`}>
          <Button variant="outline" size="sm" className="mt-2" onClick={() => turn({ page: pageCount(total, paging.pageSize), pageSize: paging.pageSize })}>
            Go to the last page
          </Button>
        </EmptyState>
      ) : items.length === 0 ? (
        <EmptyState title="No activity yet" />
      ) : (
        <div className={cn("space-y-3", activity.isPlaceholderData && "opacity-60 transition-opacity")}>
          <Card className="gap-0 divide-y py-0">
            {items.map((item, index) => {
              const kind = kinds[item.kind];
              const Icon = kind.icon;
              return (
                <div key={`${item.at}-${item.findingId}-${index}`} className="flex items-center gap-3 px-4 py-2.5 text-sm">
                  <Icon className={cn("size-4 shrink-0", kind.tone)} />
                  <span className="min-w-0 flex-1 truncate">
                    <span className="font-medium">{item.actor}</span> {kind.label}{" "}
                    <Link to={`/findings/${item.findingId}?state=any`} className="hover:underline">
                      #{item.findingId}
                    </Link>
                  </span>
                  <span className="shrink-0 text-xs text-muted-foreground" title={formatDateTime(item.at)}>
                    {formatRelative(item.at)}
                  </span>
                </div>
              );
            })}
          </Card>
          <Pagination paging={paging} total={total} onChange={turn} unit="Entries" />
        </div>
      )}
    </div>
  );
}
