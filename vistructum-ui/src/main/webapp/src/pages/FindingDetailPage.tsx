import { ArrowLeft, Check, ChevronLeft, ChevronRight, Copy, X } from "lucide-react";
import { useQueryClient } from "@tanstack/react-query";
import { lazy, Suspense, useCallback, useEffect, useMemo, useRef, useState, type CSSProperties } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router";
import { toast } from "sonner";
import { ApiError, errorMessage } from "@/api/client";
import { findingsOptions, useEvidence, useFinding, useFindings, useHeatmap, useMe, usePalette, useScene, useTerrain, useVerdict } from "@/api/queries";
import { urls } from "@/api/client";
import type { FindingDetail, FindingSummary, Verdict } from "@/api/types";
import { IndicatorIcons, RolledBackBadge, SourceBadge, VerdictBadge } from "@/components/app/Badges";
import { FindingIntegrations } from "@/components/app/IntegrationsPanel";
import { PlayerCard } from "@/components/app/PlayerFace";
import { SharePanel } from "@/components/app/SharePanel";
import { EmptyState, ErrorState, PageSpinner } from "@/components/app/States";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { SceneView } from "@/features/scene/SceneView";
import type { ReplayControls } from "@/features/replay/ReplayView";
import { copyText } from "@/lib/clipboard";
import { useHeightToBottom } from "@/lib/useHeightToBottom";
import { useStoredChoice } from "@/lib/useStoredChoice";
import { cn } from "@/lib/utils";
import { boxSize } from "@/logic/coords";
import { listParams, parseFilter, parsePaging } from "@/logic/filters";
import { formatDateTime, formatRelative, formatScore } from "@/logic/format";
import { findingIntegrations } from "@/logic/integrations";
import { detailAction, isEditableTarget } from "@/logic/keyboard";
import { locateNeighbour, neighbour, type ListEntry, type LoadedPage } from "@/logic/listNavigation";
import { pageCount } from "@/logic/pagination";
import { sceneColours } from "@/logic/sceneLayers";
import { shareView } from "@/logic/share";

function toEntry(item: FindingSummary): ListEntry {
  return { id: item.id };
}

const ReplayView = lazy(() => import("@/features/replay/ReplayView"));
const TerrainView = lazy(() => import("@/features/terrain/TerrainView"));

type Tab = "replay" | "scene" | "3d";

const noTabs: ReadonlySet<Tab> = new Set();

const sideTabs = ["verdict", "integrations", "builders"] as const;

function Fact({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="truncate text-sm">{children}</dd>
    </div>
  );
}

const selectedConfirmed =
  "border-confirmed bg-confirmed text-white hover:bg-confirmed/90 hover:text-white dark:border-confirmed dark:bg-confirmed dark:hover:bg-confirmed/90";
const selectedFalseAlarm =
  "border-false-alarm bg-false-alarm text-white hover:bg-false-alarm/90 hover:text-white dark:border-false-alarm dark:bg-false-alarm dark:hover:bg-false-alarm/90";

function VerdictButtons({ finding, pending, onVerdict }: { finding: FindingDetail; pending: boolean; onVerdict: (verdict: Verdict) => void }) {
  const current = finding.review?.verdict ?? null;
  return (
    <div className="grid grid-cols-2 gap-2">
      <Button
        variant="outline"
        className={cn("gap-1.5 px-3", current === "CONFIRMED" && selectedConfirmed)}
        onClick={() => onVerdict("CONFIRMED")}
        disabled={pending}
      >
        <Check /> Confirm
      </Button>
      <Button
        variant="outline"
        className={cn("gap-1.5 px-3", current === "FALSE_ALARM" && selectedFalseAlarm)}
        onClick={() => onVerdict("FALSE_ALARM")}
        disabled={pending}
      >
        <X /> False alarm
      </Button>
    </div>
  );
}

export default function FindingDetailPage() {
  const params = useParams();
  const id = Number(params.id);
  const validId = Number.isInteger(id) && id > 0;
  const [search] = useSearchParams();
  const navigate = useNavigate();
  const filter = useMemo(() => parseFilter(search), [search]);
  const paging = useMemo(() => parsePaging(search), [search]);
  const listSuffix = `?${listParams(filter, paging).toString()}`;
  const client = useQueryClient();
  const me = useMe();
  const finding = useFinding(id, validId);
  const list = useFindings(filter, paging);
  const palette = usePalette();
  const verdict = useVerdict();
  const data = finding.data;

  const [chosen, setChosen] = useState<{ id: number; tab: Tab; previous: Tab } | null>(null);
  const [layer, setLayer] = useState(0);
  const [overlay, setOverlay] = useState(false);
  const [visitedTabs, setVisitedTabs] = useState<{ id: number; tabs: ReadonlySet<Tab> }>(() => ({ id, tabs: new Set() }));
  const [stage, setStage] = useState<HTMLDivElement | null>(null);
  const [aside, setAside] = useState<HTMLElement | null>(null);
  const stageHeight = useHeightToBottom(stage, aside);
  const replayControls = useRef<ReplayControls | null>(null);
  const [storedSideTab, chooseSideTab] = useStoredChoice("vistructum-finding-sidebar", sideTabs, "verdict");

  const defaultTab: Tab = data?.hasEvidence ? "replay" : "scene";
  const available = (next: Tab) => (next === "replay" ? !!data?.hasEvidence : next === "3d" ? !!data?.hasTerrain : true);
  const tab: Tab = chosen?.id === id && available(chosen.tab) ? chosen.tab : defaultTab;
  const setTab = useCallback((next: Tab) => setChosen({ id, tab: next, previous: tab }), [id, tab]);

  const visited = visitedTabs.id === id ? visitedTabs.tabs : noTabs;
  if (!visited.has(tab)) setVisitedTabs({ id, tabs: new Set(visited).add(tab) });

  const heatmapWanted = overlay || layer === 4;
  const scene = useScene(id, finding.isSuccess);
  const heatmap = useHeatmap(id, heatmapWanted);
  const evidence = useEvidence(id, !!data?.hasEvidence);
  const terrain = useTerrain(id, !!data?.hasTerrain && visited.has("3d"));
  const colours = useMemo(() => (scene.data && palette.data ? sceneColours(scene.data, palette.data) : []), [scene.data, palette.data]);

  const entries: ListEntry[] = useMemo(() => (list.data?.items ?? []).map(toEntry), [list.data]);
  const position = entries.findIndex((entry) => entry.id === id);
  const total = list.data?.total;

  const go = useCallback(
    (target: number, page: number) => {
      void navigate(`/findings/${target}?${listParams(filter, { page, pageSize: paging.pageSize }).toString()}`);
    },
    [navigate, filter, paging.pageSize],
  );

  const load = useCallback(
    async (page: number): Promise<LoadedPage> => {
      const loaded = await client.fetchQuery(findingsOptions(filter, { page, pageSize: paging.pageSize }));
      return { entries: loaded.items.map(toEntry), pageCount: pageCount(loaded.total, paging.pageSize) };
    },
    [client, filter, paging.pageSize],
  );

  const lookup = useRef<{ ticket: number; pending: boolean }>({ ticket: 0, pending: false });
  useEffect(() => {
    const current = lookup.current;
    return () => {
      current.ticket++;
      current.pending = false;
    };
  }, [id]);

  const goNeighbour = useCallback(
    async (delta: 1 | -1) => {
      const cached = neighbour(entries, id, delta);
      if (cached !== null) return go(cached, paging.page);
      const state = lookup.current;
      if (state.pending) return;
      state.pending = true;
      const ticket = ++state.ticket;
      try {
        const located = await locateNeighbour(paging.page, id, delta, load);
        if (ticket !== state.ticket) return;
        if (located) go(located.id, located.page);
        else toast.info(delta > 0 ? "This is the last finding in this list." : "This is the first finding in this list.");
      } catch (error) {
        if (ticket === state.ticket) toast.error(errorMessage(error));
      } finally {
        if (ticket === state.ticket) state.pending = false;
      }
    },
    [entries, id, paging.page, load, go],
  );

  const judge = useCallback(
    (value: Verdict) => {
      if (!data) return;
      const findingId = data.id;
      verdict.mutate(
        { id: findingId, verdict: value },
        {
          onSuccess: () => {
            toast.success(`#${findingId} marked as ${value === "CONFIRMED" ? "confirmed" : "false alarm"}.`);
          },
          onError: (error) => toast.error(errorMessage(error)),
        },
      );
    },
    [data, verdict],
  );

  const copyTeleport = useCallback(async () => {
    if (!data) return;
    if (await copyText(data.teleport)) toast.success("Teleport command copied.");
    else toast.error("Copy failed. Select the command and copy it by hand.");
  }, [data]);

  const hasEvidence = !!data?.hasEvidence;
  const showReplay = useCallback(() => {
    if (!hasEvidence) return false;
    setTab("replay");
    return true;
  }, [hasEvidence, setTab]);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.defaultPrevented) return;
      const action = detailAction({
        key: event.key,
        metaKey: event.metaKey,
        ctrlKey: event.ctrlKey,
        altKey: event.altKey,
        editable: isEditableTarget(event.target),
      });
      if (!action) return;
      if (action.type === "navigate") void goNeighbour(action.delta);
      else if (showReplay()) replayControls.current?.toggle();
      else return;
      event.preventDefault();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [goNeighbour, showReplay]);

  if (!validId) return <EmptyState title="Unknown finding" />;
  if (finding.isPending) return <PageSpinner />;
  if (finding.isError || !data) {
    const notFound = finding.error instanceof ApiError && finding.error.status === 404;
    return notFound ? (
      <EmptyState title={`Finding #${id} does not exist`}>
        <Link to={`/findings${listSuffix}`} className="underline">
          Back to the list
        </Link>
      </EmptyState>
    ) : (
      <ErrorState error={finding.error} onRetry={() => void finding.refetch()} />
    );
  }

  const retryReplay = () => {
    if (evidence.isError) void evidence.refetch();
    if (palette.isError) void palette.refetch();
  };
  const retryTerrain = () => {
    if (terrain.isError) void terrain.refetch();
    if (palette.isError) void palette.refetch();
  };
  const size = boxSize(data.box);
  const canShare = !!me.data?.canShare;
  const integrations = findingIntegrations(data, !!me.data?.blockLog, me.data?.punishments ?? null);
  const sideTab = storedSideTab === "integrations" && !integrations.length ? "verdict" : storedSideTab;
  const heatmapState = !heatmapWanted ? "idle" : heatmap.isPending ? "loading" : heatmap.isError ? "unavailable" : "ready";

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3">
        <Button variant="ghost" size="sm" asChild className="-ml-2">
          <Link to={`/findings${listSuffix}`}>
            <ArrowLeft /> Findings
          </Link>
        </Button>
        <div className="ml-auto flex items-center gap-1 text-sm text-muted-foreground">
          {position >= 0 && total !== undefined && (
            <span className="mr-2 tabular-nums">
              {((paging.page - 1) * paging.pageSize + position + 1).toLocaleString("en-GB")} of {total.toLocaleString("en-GB")}
            </span>
          )}
          <Tooltip>
            <TooltipTrigger asChild>
              <Button variant="outline" size="icon" className="size-8" onClick={() => void goNeighbour(-1)} aria-label="Previous finding">
                <ChevronLeft />
              </Button>
            </TooltipTrigger>
            <TooltipContent>Previous (←)</TooltipContent>
          </Tooltip>
          <Tooltip>
            <TooltipTrigger asChild>
              <Button variant="outline" size="icon" className="size-8" onClick={() => void goNeighbour(1)} aria-label="Next finding">
                <ChevronRight />
              </Button>
            </TooltipTrigger>
            <TooltipContent>Next (→)</TooltipContent>
          </Tooltip>
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-3">
        <h1 className="text-2xl font-semibold tracking-tight">#{data.id}</h1>
        <VerdictBadge review={data.review} />
        <RolledBackBadge finding={data} />
        <SourceBadge source={data.source} />
        <IndicatorIcons finding={data} />
      </div>

      <Tabs value={tab} onValueChange={(value) => setTab(value as Tab)} className="gap-3">
        <TabsList>
          {data.hasEvidence && <TabsTrigger value="replay">Replay</TabsTrigger>}
          <TabsTrigger value="scene">Scene</TabsTrigger>
          {data.hasTerrain && <TabsTrigger value="3d">3D</TabsTrigger>}
        </TabsList>
        <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_320px]">
          <div className="min-w-0">
            {data.hasEvidence && (
              <TabsContent value="replay" forceMount className="data-[state=inactive]:hidden">
                {!visited.has("replay") ? null : evidence.isPending || palette.isPending ? (
                  <PageSpinner className="h-[64vh]" />
                ) : evidence.isError || palette.isError || !evidence.data || !palette.data ? (
                  <ErrorState error={evidence.error ?? palette.error} onRetry={retryReplay} />
                ) : (
                  <Suspense fallback={<PageSpinner className="h-[64vh]" />}>
                    <ReplayView
                      key={data.id}
                      evidence={evidence.data}
                      palette={palette.data}
                      skinUrl={urls.skin}
                      reconstruct
                      controlsRef={replayControls}
                      stageRef={tab === "replay" ? setStage : undefined}
                      active={tab === "replay"}
                    />
                  </Suspense>
                )}
              </TabsContent>
            )}
            <TabsContent value="scene">
              {scene.isPending || palette.isPending ? (
                <PageSpinner className="h-[50vh]" />
              ) : scene.isError || !scene.data ? (
                <ErrorState error={scene.error} onRetry={() => void scene.refetch()} />
              ) : (
                <SceneView
                  stageRef={setStage}
                  scene={scene.data}
                  colours={colours}
                  heatmap={heatmap.data ?? null}
                  heatmapState={heatmapState}
                  layer={layer}
                  onLayer={setLayer}
                  overlay={overlay}
                  onOverlay={setOverlay}
                />
              )}
            </TabsContent>
            {data.hasTerrain && (
              <TabsContent value="3d" forceMount className="data-[state=inactive]:hidden">
                {!visited.has("3d") ? null : terrain.data && palette.data ? (
                  <Suspense fallback={<PageSpinner className="h-[64vh]" />}>
                    <TerrainView
                      stageRef={tab === "3d" ? setStage : undefined}
                      key={data.id}
                      terrain={terrain.data}
                      box={data.box}
                      palette={palette.data}
                      heatmap={overlay ? (heatmap.data ?? null) : null}
                      heatmapState={heatmapState}
                      overlay={overlay}
                      onOverlay={setOverlay}
                      active={tab === "3d"}
                    />
                  </Suspense>
                ) : terrain.isError || palette.isError ? (
                  <ErrorState error={terrain.error ?? palette.error} onRetry={retryTerrain} />
                ) : (
                  <PageSpinner className="h-[50vh]" />
                )}
              </TabsContent>
            )}
          </div>

          <aside
            ref={setAside}
            className={cn("flex flex-col gap-4", tab === "scene" && "lg:mt-12", stageHeight !== null && "lg:h-(--stage-height)")}
            style={stageHeight !== null ? ({ "--stage-height": `${stageHeight}px` } as CSSProperties) : undefined}
          >
            <Tabs value={sideTab} onValueChange={chooseSideTab} className="min-h-0 flex-1">
              <TabsList className="w-full shrink-0">
                <TabsTrigger value="verdict">Verdict</TabsTrigger>
                {integrations.length > 0 && <TabsTrigger value="integrations">Integrations</TabsTrigger>}
                <TabsTrigger value="builders">
                  Builders <span className="text-muted-foreground tabular-nums">{data.players.length}</span>
                </TabsTrigger>
              </TabsList>
              <TabsContent value="verdict" className="flex min-h-0 flex-col gap-4 overflow-y-auto">
                <Card className="shrink-0 gap-4 py-4">
                  <CardContent className="space-y-4 px-4">
                    <VerdictButtons finding={data} pending={verdict.isPending} onVerdict={judge} />
                    {data.review && (
                      <p className="text-xs text-muted-foreground">
                        {data.review.verdict === "CONFIRMED" ? "Confirmed" : "Marked as false alarm"} by {data.review.reviewer}, {formatDateTime(data.review.reviewedAt)}
                      </p>
                    )}
                    <dl className="grid grid-cols-2 gap-x-4 gap-y-3">
                      <Fact label="Score">
                        <span className="tabular-nums">{formatScore(data.score)}</span>
                        {data.votes > 1 && <span className="text-muted-foreground"> · {data.votes} votes</span>}
                      </Fact>
                      <Fact label="Model">
                        <span>{data.modelVersion}</span>
                      </Fact>
                      <Fact label="Source">{data.source === "mask" ? "Live check" : "Full scan"}</Fact>
                      <Fact label="Created">
                        <Tooltip>
                          <TooltipTrigger asChild>
                            <span>{formatRelative(data.createdAt)}</span>
                          </TooltipTrigger>
                          <TooltipContent>{formatDateTime(data.createdAt)}</TooltipContent>
                        </Tooltip>
                      </Fact>
                      <Fact label="World">
                        <span>{data.world}</span>
                      </Fact>
                      <Fact label="Box">
                        <span className="tabular-nums">
                          {size.x}×{size.y}×{size.z}
                        </span>
                      </Fact>
                    </dl>
                    <div className="space-y-1.5">
                      <div className="text-xs text-muted-foreground">Teleport</div>
                      <div className="flex gap-2">
                        <code className="min-w-0 flex-1 truncate rounded-md border bg-muted px-2 py-1.5 text-xs select-all" title={data.teleport}>
                          {data.teleport}
                        </code>
                        <Tooltip>
                          <TooltipTrigger asChild>
                            <Button variant="outline" size="icon" className="size-8 shrink-0" onClick={() => void copyTeleport()} aria-label="Copy teleport command">
                              <Copy />
                            </Button>
                          </TooltipTrigger>
                          <TooltipContent>Copy</TooltipContent>
                        </Tooltip>
                      </div>
                    </div>
                  </CardContent>
                </Card>

                {shareView(data, canShare) !== "hidden" && (
                  <div className="shrink-0">
                    <SharePanel key={data.id} finding={data} canShare={canShare} />
                  </div>
                )}
              </TabsContent>
              {integrations.length > 0 && (
                <TabsContent value="integrations" className="flex min-h-0 flex-col">
                  <FindingIntegrations key={data.id} finding={data} integrations={integrations} blockLog={!!me.data?.blockLog} canRollback={!!me.data?.canRollback} />
                </TabsContent>
              )}
              <TabsContent value="builders" className="min-h-0 overflow-y-auto pr-1 max-lg:max-h-[70vh]">
                {data.players.length ? (
                  <div className="flex flex-col gap-2">
                    {data.players.map((player) => (
                      <PlayerCard key={player.uuid} player={player} className="w-full" />
                    ))}
                  </div>
                ) : (
                  <p className="text-sm text-muted-foreground">No players recorded.</p>
                )}
              </TabsContent>
            </Tabs>
          </aside>
        </div>
      </Tabs>
    </div>
  );
}
