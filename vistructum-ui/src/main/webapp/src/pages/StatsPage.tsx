import { useMemo, useState } from "react";
import { Bar, CartesianGrid, ComposedChart, Line, XAxis, YAxis } from "recharts";
import { Link } from "react-router";
import { useStats } from "@/api/queries";
import type { Paging } from "@/api/types";
import { Pagination } from "@/components/app/Pagination";
import { EmptyState, ErrorState, PageHeader, PageSpinner } from "@/components/app/States";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { ChartContainer, ChartLegend, ChartLegendContent, ChartTooltip, ChartTooltipContent, type ChartConfig } from "@/components/ui/chart";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { formatDateTime, formatPercent, formatRelative, precision } from "@/logic/format";
import { pageCount } from "@/logic/pagination";
import { axisWidth, compactCount, dayRows, rangeBounds, ranges, reviewerRows, totals, weekRows, type RangeKey } from "@/logic/stats";

const chartConfig = {
  mask: { label: "Live check", color: "var(--chart-1)" },
  fullscan: { label: "Full scan", color: "var(--chart-2)" },
  confirmed: { label: "Confirmed", color: "var(--confirmed)" },
  falseAlarms: { label: "False alarms", color: "var(--false-alarm)" },
} satisfies ChartConfig;

const sourceLabels = { mask: "Live check", fullscan: "Full scan" } as const;

function Kpi({ label, value, hint }: { label: string; value: React.ReactNode; hint?: React.ReactNode }) {
  return (
    <Card className="gap-1 py-4">
      <CardContent className="px-4">
        <div className="text-xs text-muted-foreground">{label}</div>
        <div className="text-2xl font-semibold tabular-nums">{value}</div>
        {hint && <div className="text-xs text-muted-foreground">{hint}</div>}
      </CardContent>
    </Card>
  );
}

const reviewerPageSizes = [10, 25, 50] as const;

export default function StatsPage() {
  const [range, setRange] = useState<RangeKey>("30d");
  const [now] = useState(() => new Date());
  const bounds = useMemo(() => rangeBounds(range, now), [range, now]);
  const stats = useStats(bounds.from.toISOString(), bounds.to.toISOString());
  const rows = useMemo(() => (stats.data ? dayRows(stats.data, bounds.from, bounds.to) : []), [stats.data, bounds]);
  const sum = useMemo(() => totals(rows), [rows]);
  const reviewers = useMemo(() => (stats.data ? reviewerRows(stats.data) : []), [stats.data]);
  const [reviewerPaging, setReviewerPaging] = useState<Paging>({ page: 1, pageSize: reviewerPageSizes[0] });
  const reviewerPage = Math.min(reviewerPaging.page, pageCount(reviewers.length, reviewerPaging.pageSize));
  const weekly = rows.length > 90;
  const chartRows = useMemo(() => (weekly ? weekRows(rows) : rows), [rows, weekly]);
  const tickEvery = Math.max(1, Math.ceil(chartRows.length / 12));
  const empty = sum.created === 0 && sum.confirmed === 0 && sum.falseAlarms === 0;
  const count = (value: number) => value.toLocaleString("en-GB");

  return (
    <div className="space-y-4">
      <PageHeader
        title="Statistics"
        description="Findings created and reviewed in the selected range"
        actions={
          <ToggleGroup type="single" variant="outline" size="sm" className="w-full sm:w-auto" value={range} onValueChange={(value) => value && setRange(value as RangeKey)}>
            {ranges.map((entry) => (
              <ToggleGroupItem key={entry.key} value={entry.key} className="flex-1 px-2 sm:flex-none sm:px-3">
                {entry.label}
              </ToggleGroupItem>
            ))}
          </ToggleGroup>
        }
      />
      {stats.isPending ? (
        <PageSpinner />
      ) : stats.isError || !stats.data ? (
        <ErrorState error={stats.error} onRetry={() => void stats.refetch()} />
      ) : (
        <div className={stats.isPlaceholderData ? "space-y-4 opacity-60 transition-opacity" : "space-y-4"}>
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <Kpi
              label="Open now"
              value={
                <Link to="/findings?state=open" className="hover:underline">
                  {stats.data.open.toLocaleString("en-GB")}
                </Link>
              }
              hint={stats.data.oldestOpen ? <span title={formatDateTime(stats.data.oldestOpen)}>Oldest {formatRelative(stats.data.oldestOpen)}</span> : "Nothing waiting"}
            />
            <Kpi label="Created" value={count(sum.created)} hint={`in ${ranges.find((entry) => entry.key === range)?.label}`} />
            <Kpi label="Confirmed" value={sum.confirmed.toLocaleString("en-GB")} hint={`${sum.falseAlarms.toLocaleString("en-GB")} false alarms`} />
            <Kpi label="Precision" value={formatPercent(precision(sum.confirmed, sum.falseAlarms))} hint="Confirmed of all reviewed" />
          </div>

          <Card className="gap-3">
            <CardHeader>
              <CardTitle className="text-base">Findings per {weekly ? "week" : "day"}</CardTitle>
              <CardDescription>Bars stack the created findings by source. Lines show the verdicts given in that {weekly ? "week" : "day"}.</CardDescription>
            </CardHeader>
            <CardContent>
              {empty ? (
                <EmptyState title="No findings in this range" className="h-72">
                  Nothing was created or reviewed.
                </EmptyState>
              ) : (
                <ChartContainer config={chartConfig} className="aspect-auto h-72 w-full">
                <ComposedChart data={chartRows} margin={{ left: 0, right: 8 }}>
                  <CartesianGrid vertical={false} />
                  <XAxis dataKey="day" tickLine={false} axisLine={false} interval={tickEvery - 1} tickFormatter={(day: string) => day.slice(5)} />
                  <YAxis allowDecimals={false} tickLine={false} axisLine={false} width={axisWidth(chartRows)} tickFormatter={compactCount} />
                  <ChartTooltip content={<ChartTooltipContent />} />
                  <ChartLegend content={<ChartLegendContent />} />
                  <Bar dataKey="mask" stackId="created" fill="var(--color-mask)" />
                  <Bar dataKey="fullscan" stackId="created" fill="var(--color-fullscan)" radius={[3, 3, 0, 0]} />
                  <Line dataKey="confirmed" type="monotone" stroke="var(--color-confirmed)" strokeWidth={2} dot={false} />
                  <Line dataKey="falseAlarms" type="monotone" stroke="var(--color-falseAlarms)" strokeWidth={2} dot={false} />
                </ComposedChart>
                </ChartContainer>
              )}
            </CardContent>
          </Card>

          <div className="grid grid-cols-[minmax(0,1fr)] gap-4 lg:grid-cols-2">
            <Card className="gap-3">
              <CardHeader>
                <CardTitle className="text-base">Precision per source</CardTitle>
              </CardHeader>
              <CardContent>
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Source</TableHead>
                      <TableHead className="text-right">Confirmed</TableHead>
                      <TableHead className="text-right">False alarms</TableHead>
                      <TableHead className="text-right">Precision</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {stats.data.precision.map((row) => {
                      const value = precision(row.confirmed, row.falseAlarms);
                      return (
                        <TableRow key={row.source}>
                          <TableCell>{sourceLabels[row.source]}</TableCell>
                          <TableCell className="text-right tabular-nums">{count(row.confirmed)}</TableCell>
                          <TableCell className="text-right tabular-nums">{count(row.falseAlarms)}</TableCell>
                          <TableCell className="text-right">
                            <div className="flex items-center justify-end gap-2">
                              <div className="hidden h-1.5 w-20 overflow-hidden rounded-full bg-muted sm:block">
                                <div className="h-full bg-primary" style={{ width: `${(value ?? 0) * 100}%` }} />
                              </div>
                              <span className="w-12 tabular-nums">{formatPercent(value)}</span>
                            </div>
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </CardContent>
            </Card>

            <Card className="gap-3">
              <CardHeader>
                <CardTitle className="text-base">Reviewers</CardTitle>
              </CardHeader>
              <CardContent>
                {reviewers.length === 0 ? (
                  <p className="text-sm text-muted-foreground">No reviews in this range.</p>
                ) : (
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Reviewer</TableHead>
                        <TableHead className="text-right">Reviews</TableHead>
                        <TableHead className="text-right">Confirmed</TableHead>
                        <TableHead className="text-right">False alarms</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {reviewers.slice((reviewerPage - 1) * reviewerPaging.pageSize, reviewerPage * reviewerPaging.pageSize).map((row) => (
                        <TableRow key={row.reviewer}>
                          <TableCell className="max-w-40 truncate font-medium" title={row.reviewer}>
                            {row.reviewer}
                          </TableCell>
                          <TableCell className="text-right tabular-nums">{count(row.total)}</TableCell>
                          <TableCell className="text-right tabular-nums">{count(row.confirmed)}</TableCell>
                          <TableCell className="text-right tabular-nums">{count(row.falseAlarms)}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )}
                {reviewers.length > reviewerPageSizes[0] && (
                  <Pagination
                    paging={{ page: reviewerPage, pageSize: reviewerPaging.pageSize }}
                    total={reviewers.length}
                    onChange={setReviewerPaging}
                    unit="Reviewers"
                    sizes={reviewerPageSizes}
                    className="mt-3"
                  />
                )}
              </CardContent>
            </Card>
          </div>
        </div>
      )}
    </div>
  );
}
