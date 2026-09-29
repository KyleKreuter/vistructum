import { useMemo, useState } from "react";
import { Bar, CartesianGrid, ComposedChart, Line, XAxis, YAxis } from "recharts";
import { Link } from "react-router";
import { useStats } from "@/api/queries";
import { ErrorState, PageHeader, PageSpinner } from "@/components/app/States";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { ChartContainer, ChartLegend, ChartLegendContent, ChartTooltip, ChartTooltipContent, type ChartConfig } from "@/components/ui/chart";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { formatDateTime, formatPercent, formatRelative, precision } from "@/logic/format";
import { dayRows, rangeBounds, ranges, reviewerRows, totals, type RangeKey } from "@/logic/stats";

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

export default function StatsPage() {
  const [range, setRange] = useState<RangeKey>("30d");
  const [now] = useState(() => new Date());
  const bounds = useMemo(() => rangeBounds(range, now), [range, now]);
  const stats = useStats(bounds.from.toISOString(), bounds.to.toISOString());
  const rows = useMemo(() => (stats.data ? dayRows(stats.data, bounds.from, bounds.to) : []), [stats.data, bounds]);
  const sum = useMemo(() => totals(rows), [rows]);
  const reviewers = useMemo(() => (stats.data ? reviewerRows(stats.data) : []), [stats.data]);
  const tickEvery = Math.max(1, Math.ceil(rows.length / 12));

  return (
    <div className="space-y-4">
      <PageHeader
        title="Statistics"
        description="Findings created and reviewed in the selected range"
        actions={
          <ToggleGroup type="single" variant="outline" size="sm" value={range} onValueChange={(value) => value && setRange(value as RangeKey)}>
            {ranges.map((entry) => (
              <ToggleGroupItem key={entry.key} value={entry.key} className="px-3">
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
            <Kpi label="Created" value={sum.created.toLocaleString("en-GB")} hint={`in ${ranges.find((entry) => entry.key === range)?.label}`} />
            <Kpi label="Confirmed" value={sum.confirmed.toLocaleString("en-GB")} hint={`${sum.falseAlarms.toLocaleString("en-GB")} false alarms`} />
            <Kpi label="Precision" value={formatPercent(precision(sum.confirmed, sum.falseAlarms))} hint="Confirmed of all reviewed" />
          </div>

          <Card className="gap-3">
            <CardHeader>
              <CardTitle className="text-base">Findings per day</CardTitle>
              <CardDescription>Bars stack the created findings by source. Lines show the verdicts given that day.</CardDescription>
            </CardHeader>
            <CardContent>
              <ChartContainer config={chartConfig} className="aspect-auto h-72 w-full">
                <ComposedChart data={rows} margin={{ left: -16, right: 8 }}>
                  <CartesianGrid vertical={false} />
                  <XAxis dataKey="day" tickLine={false} axisLine={false} interval={tickEvery - 1} tickFormatter={(day: string) => day.slice(5)} />
                  <YAxis allowDecimals={false} tickLine={false} axisLine={false} width={40} />
                  <ChartTooltip content={<ChartTooltipContent />} />
                  <ChartLegend content={<ChartLegendContent />} />
                  <Bar dataKey="mask" stackId="created" fill="var(--color-mask)" />
                  <Bar dataKey="fullscan" stackId="created" fill="var(--color-fullscan)" radius={[3, 3, 0, 0]} />
                  <Line dataKey="confirmed" type="monotone" stroke="var(--color-confirmed)" strokeWidth={2} dot={false} />
                  <Line dataKey="falseAlarms" type="monotone" stroke="var(--color-falseAlarms)" strokeWidth={2} dot={false} />
                </ComposedChart>
              </ChartContainer>
            </CardContent>
          </Card>

          <div className="grid gap-4 lg:grid-cols-2">
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
                          <TableCell className="text-right tabular-nums">{row.confirmed}</TableCell>
                          <TableCell className="text-right tabular-nums">{row.falseAlarms}</TableCell>
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
                      {reviewers.map((row) => (
                        <TableRow key={row.reviewer}>
                          <TableCell className="font-medium">{row.reviewer}</TableCell>
                          <TableCell className="text-right tabular-nums">{row.total}</TableCell>
                          <TableCell className="text-right tabular-nums">{row.confirmed}</TableCell>
                          <TableCell className="text-right tabular-nums">{row.falseAlarms}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )}
              </CardContent>
            </Card>
          </div>
        </div>
      )}
    </div>
  );
}
