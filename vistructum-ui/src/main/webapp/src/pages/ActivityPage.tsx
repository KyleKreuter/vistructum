import { Check, Link2, Link2Off, X } from "lucide-react";
import { useMemo } from "react";
import { Link } from "react-router";
import { useActivity } from "@/api/queries";
import type { ActivityKind } from "@/api/types";
import { EmptyState, ErrorState, PageHeader, PageSpinner } from "@/components/app/States";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { formatDateTime, formatRelative } from "@/logic/format";
import { cn } from "@/lib/utils";

const kinds: Record<ActivityKind, { label: string; icon: typeof Check; tone: string }> = {
  CONFIRMED: { label: "confirmed", icon: Check, tone: "text-confirmed" },
  FALSE_ALARM: { label: "marked as false alarm", icon: X, tone: "text-false-alarm" },
  SHARED: { label: "activated the public link of", icon: Link2, tone: "text-primary" },
  UNSHARED: { label: "deactivated the public link of", icon: Link2Off, tone: "text-muted-foreground" },
};

export default function ActivityPage() {
  const activity = useActivity();
  const items = useMemo(() => activity.data?.pages.flatMap((page) => page.items) ?? [], [activity.data]);
  return (
    <div className="space-y-4">
      <PageHeader title="Activity" description="Verdicts and public links, newest first" />
      {activity.isPending ? (
        <PageSpinner />
      ) : activity.isError ? (
        <ErrorState error={activity.error} onRetry={() => void activity.refetch()} />
      ) : items.length === 0 ? (
        <EmptyState title="No activity yet" />
      ) : (
        <>
          <Card className="gap-0 divide-y py-0">
            {items.map((item, index) => {
              const kind = kinds[item.kind];
              const Icon = kind.icon;
              return (
                <div key={`${item.at}-${item.findingId}-${index}`} className="flex items-center gap-3 px-4 py-2.5 text-sm">
                  <Icon className={cn("size-4 shrink-0", kind.tone)} />
                  <span className="min-w-0 flex-1 truncate">
                    <span className="font-medium">{item.actor}</span> {kind.label}{" "}
                    <Link to={`/findings/${item.findingId}?state=any`} className="font-mono hover:underline">
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
          {activity.hasNextPage && (
            <div className="flex justify-center">
              <Button variant="outline" size="sm" onClick={() => void activity.fetchNextPage()} disabled={activity.isFetchingNextPage}>
                {activity.isFetchingNextPage ? "Loading…" : "Load older"}
              </Button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
