import { Circle, Cpu, Radio } from "lucide-react";
import { Link } from "react-router";
import { useStatus } from "@/api/queries";
import type { ScanJob } from "@/api/types";
import { ErrorState, PageHeader, PageSpinner } from "@/components/app/States";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { cn } from "@/lib/utils";
import { formatDateTime, formatRelative } from "@/logic/format";

function Dot({ on }: { on: boolean }) {
  return <Circle className={cn("size-2.5 fill-current", on ? "text-emerald-500" : "text-muted-foreground")} />;
}

function ScanRow({ scan }: { scan: ScanJob }) {
  const fraction = scan.totalTiles > 0 ? scan.doneTiles / scan.totalTiles : 0;
  return (
    <div className="space-y-2 rounded-lg border p-3">
      <div className="flex flex-wrap items-center gap-2 text-sm">
        <span>#{scan.id}</span>
        <span className="min-w-0 truncate text-muted-foreground" title={scan.world}>
          {scan.world}
        </span>
        <Badge variant="slate" className="text-[11px]">
          {scan.cause === "DAILY" ? "Daily" : "Manual"}
        </Badge>
        <Badge variant={scan.status === "FAILED" ? "rose" : "blue"} className="text-[11px]">
          {scan.status.toLowerCase()}
        </Badge>
        <span className="ml-auto text-xs text-muted-foreground" title={formatDateTime(scan.startedAt)}>
          {scan.status === "QUEUED" ? "queued" : "started"} {formatRelative(scan.startedAt)}
        </span>
      </div>
      <div className="h-2 overflow-hidden rounded-full bg-muted" role="progressbar" aria-valuemin={0} aria-valuemax={scan.totalTiles} aria-valuenow={scan.doneTiles}>
        <div className="h-full bg-primary transition-[width] duration-700" style={{ width: `${fraction * 100}%` }} />
      </div>
      <div className="flex flex-wrap gap-x-4 text-xs text-muted-foreground tabular-nums">
        <span>
          {scan.doneTiles.toLocaleString("en-GB")} / {scan.totalTiles.toLocaleString("en-GB")} tiles ({Math.floor(fraction * 100)}%)
        </span>
        <span>{scan.findings.toLocaleString("en-GB")} {scan.findings === 1 ? "finding" : "findings"}</span>
        {scan.failures > 0 && <span className="text-destructive">
            {scan.failures.toLocaleString("en-GB")} failed {scan.failures === 1 ? "tile" : "tiles"}
          </span>}
      </div>
    </div>
  );
}

export default function StatusPage() {
  const status = useStatus();
  const data = status.data;
  return (
    <div className="space-y-4">
      <PageHeader title="Status" description="Refreshes every two seconds" />
      {status.isPending ? (
        <PageSpinner />
      ) : status.isError || !data ? (
        <ErrorState error={status.error} onRetry={() => void status.refetch()} />
      ) : (
        <div className="grid grid-cols-[minmax(0,1fr)] gap-4 lg:grid-cols-[320px_minmax(0,1fr)]">
          <div className="min-w-0 space-y-4">
            <Card className="gap-3 py-4">
              <CardHeader className="px-4">
                <CardTitle className="flex items-center gap-2 text-sm">
                  <Cpu className="size-4" /> Inference
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-3 px-4 text-sm">
                <div className="flex items-center gap-2">
                  <Dot on={data.inference.available} />
                  <span>{data.inference.mode === "LOCAL" ? "Local" : "Remote"}</span>
                  <span className="text-muted-foreground">{data.inference.available ? "available" : "unavailable"}</span>
                </div>
                {data.inference.detail && <p className="text-xs [overflow-wrap:anywhere] text-muted-foreground">{data.inference.detail}</p>}
                <ul className="space-y-1">
                  {data.inference.models.map((model) => (
                    <li key={model.kind} className="flex justify-between gap-2">
                      <span className="shrink-0 text-muted-foreground">{model.kind}</span>
                      <span className="min-w-0 truncate" title={model.version}>
                        {model.version}
                      </span>
                    </li>
                  ))}
                </ul>
              </CardContent>
            </Card>
            <Card className="gap-3 py-4">
              <CardHeader className="px-4">
                <CardTitle className="flex items-center gap-2 text-sm">
                  <Radio className="size-4" /> Live checks
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-2 px-4 text-sm">
                <div className="flex items-center gap-2">
                  <Dot on={data.recordingEnabled} />
                  Recording {data.recordingEnabled ? "on" : "off"}
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Tracked changes</span>
                  <span className="tabular-nums">{data.trackedChanges.toLocaleString("en-GB")}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Open findings</span>
                  <Link to="/findings?state=open" className="tabular-nums hover:underline">
                    {data.openFindings.toLocaleString("en-GB")}
                  </Link>
                </div>
              </CardContent>
            </Card>
          </div>
          <Card className="min-w-0 gap-3 py-4">
            <CardHeader className="px-4">
              <CardTitle className="text-sm">Full scans</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3 px-4">
              {data.scans.length === 0 ? (
                <p className="text-sm text-muted-foreground">No scans are queued or running.</p>
              ) : (
                data.scans.map((scan) => <ScanRow key={scan.id} scan={scan} />)
              )}
            </CardContent>
          </Card>
        </div>
      )}
    </div>
  );
}
