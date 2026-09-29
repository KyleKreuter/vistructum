import { ArrowLeft, Check, ChevronLeft, ChevronRight, Copy, Keyboard, SkipForward, X } from "lucide-react";
import { useQueryClient } from "@tanstack/react-query";
import { lazy, Suspense, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router";
import { toast } from "sonner";
import { ApiError, errorMessage } from "@/api/client";
import { findingsOptions, useEvidence, useFinding, useFindings, useHeatmap, useMe, usePalette, useScene, useVerdict } from "@/api/queries";
import { urls } from "@/api/client";
import type { FindingDetail, FindingSummary, Verdict } from "@/api/types";
import { IndicatorIcons, SourceBadge, VerdictBadge } from "@/components/app/Badges";
import { PlayerList } from "@/components/app/PlayerFace";
import { SharePanel } from "@/components/app/SharePanel";
import { EmptyState, ErrorState, PageSpinner } from "@/components/app/States";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { SceneView } from "@/features/scene/SceneView";
import type { ReplayControls } from "@/features/replay/ReplayView";
import { copyText } from "@/lib/clipboard";
import { cn } from "@/lib/utils";
import { boxSize } from "@/logic/coords";
import { listParams, parseFilter, parsePaging } from "@/logic/filters";
import { formatDateTime, formatRelative, formatScore } from "@/logic/format";
import { detailAction, isEditableTarget, shortcutHelp } from "@/logic/keyboard";
import { firstOpen, locateNeighbour, locateNextOpen, neighbour, type ListEntry, type LoadedPage } from "@/logic/listNavigation";
import { pageCount } from "@/logic/pagination";
import { sceneColours } from "@/logic/sceneLayers";
import { shareView } from "@/logic/share";

function toEntry(item: FindingSummary): ListEntry {
  return { id: item.id, open: item.review === null };
}

const ReplayView = lazy(() => import("@/features/replay/ReplayView"));
const TerrainView = lazy(() => import("@/features/terrain/TerrainView"));

type Tab = "replay" | "scene" | "3d";

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
        className={cn("justify-between gap-1.5 px-3", current === "CONFIRMED" && selectedConfirmed)}
        onClick={() => onVerdict("CONFIRMED")}
        disabled={pending}
      >
        <span className="flex items-center gap-1.5">
          <Check /> Confirm
        </span>
        <kbd className={cn(current === "CONFIRMED" && "border-white/40 bg-transparent text-white")}>1</kbd>
      </Button>
      <Button
        variant="outline"
        className={cn("justify-between gap-1.5 px-3", current === "FALSE_ALARM" && selectedFalseAlarm)}
        onClick={() => onVerdict("FALSE_ALARM")}
        disabled={pending}
      >
        <span className="flex items-center gap-1.5">
          <X /> False alarm
        </span>
        <kbd className={cn(current === "FALSE_ALARM" && "border-white/40 bg-transparent text-white")}>2</kbd>
      </Button>
    </div>
  );
}

export default function FindingDetailPage() {
  const params = useParams();
  const id = Number(params.id);
  const [search] = useSearchParams();
  const navigate = useNavigate();
  const filter = useMemo(() => parseFilter(search), [search]);
  const paging = useMemo(() => parsePaging(search), [search]);
  const listSuffix = `?${listParams(filter, paging).toString()}`;
  const client = useQueryClient();
  const me = useMe();
  const finding = useFinding(id);
  const list = useFindings(filter, paging);
  const palette = usePalette();
  const verdict = useVerdict();
  const data = finding.data;

  const [chosen, setChosen] = useState<{ id: number; tab: Tab; previous: Tab } | null>(null);
  const [layer, setLayer] = useState(0);
  const [overlay, setOverlay] = useState(false);
  const [reviewed, setReviewed] = useState<Set<number>>(() => new Set());
  const [visited, setVisited] = useState<ReadonlySet<Tab>>(() => new Set());
  const replayControls = useRef<ReplayControls | null>(null);

  const defaultTab: Tab = data?.hasEvidence ? "replay" : "scene";
  const tab: Tab = chosen?.id === id && (chosen.tab !== "replay" || data?.hasEvidence) ? chosen.tab : defaultTab;
  const setTab = useCallback((next: Tab) => setChosen({ id, tab: next, previous: tab }), [id, tab]);

  if (!visited.has(tab)) setVisited(new Set(visited).add(tab));

  const heatmapWanted = overlay || layer === 4;
  const scene = useScene(id);
  const heatmap = useHeatmap(id, heatmapWanted);
  const evidence = useEvidence(id, !!data?.hasEvidence);
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

  const goNeighbour = useCallback(
    async (delta: 1 | -1) => {
      const cached = neighbour(entries, id, delta);
      if (cached !== null) return go(cached, paging.page);
      try {
        const located = await locateNeighbour(paging.page, id, delta, load);
        if (located) go(located.id, located.page);
        else toast.info(delta > 0 ? "This is the last finding in this list." : "This is the first finding in this list.");
      } catch (error) {
        toast.error(errorMessage(error));
      }
    },
    [entries, id, paging.page, load, go],
  );

  const goNextOpen = useCallback(
    async (from: number, extra?: number) => {
      const skip = new Set(reviewed);
      if (extra !== undefined) skip.add(extra);
      const cached = firstOpen(entries, from, skip, true);
      if (cached !== null) return go(cached, paging.page);
      try {
        const located = await locateNextOpen(paging.page, from, skip, load);
        if (located) go(located.id, located.page);
        else toast.info("No more open findings in this list.");
      } catch (error) {
        toast.error(errorMessage(error));
      }
    },
    [entries, reviewed, paging.page, load, go],
  );

  const judge = useCallback(
    (value: Verdict, advance: boolean) => {
      if (!data) return;
      const findingId = data.id;
      verdict.mutate(
        { id: findingId, verdict: value },
        {
          onSuccess: () => {
            setReviewed((current) => new Set(current).add(findingId));
            toast.success(`#${findingId} marked as ${value === "CONFIRMED" ? "confirmed" : "false alarm"}.`);
            if (advance) void goNextOpen(findingId, findingId);
          },
          onError: (error) => toast.error(errorMessage(error)),
        },
      );
    },
    [data, verdict, goNextOpen],
  );

  const copyTeleport = useCallback(async () => {
    if (!data) return;
    if (await copyText(data.teleport)) toast.success("Teleport command copied.");
    else toast.error("Copy failed. Select the command and copy it by hand.");
  }, [data]);

  const toggle3d = useCallback(() => {
    const back = chosen?.id === id && chosen.previous !== "3d" ? chosen.previous : defaultTab;
    setTab(tab === "3d" ? back : "3d");
  }, [tab, chosen, id, defaultTab, setTab]);

  const hasEvidence = !!data?.hasEvidence;
  const showReplay = useCallback(() => {
    if (!hasEvidence) return false;
    setTab("replay");
    return true;
  }, [hasEvidence, setTab]);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      const action = detailAction({
        key: event.key,
        metaKey: event.metaKey,
        ctrlKey: event.ctrlKey,
        altKey: event.altKey,
        editable: isEditableTarget(event.target),
      });
      if (!action || event.repeat && action.type === "verdict") return;
      switch (action.type) {
        case "verdict":
          if (!verdict.isPending) judge(action.verdict, true);
          break;
        case "navigate":
          void goNeighbour(action.delta);
          break;
        case "nextOpen":
          void goNextOpen(id);
          break;
        case "layer":
          setLayer(action.index);
          setTab("scene");
          break;
        case "toggleHeatmap":
          setOverlay((value) => !value);
          break;
        case "toggle3d":
          toggle3d();
          break;
        case "copyTeleport":
          void copyTeleport();
          break;
        case "playPause":
          if (showReplay()) replayControls.current?.toggle();
          break;
        case "step":
          if (showReplay()) replayControls.current?.step(action.delta);
          break;
        case "speed":
          if (showReplay()) replayControls.current?.changeSpeed(action.delta);
          break;
      }
      event.preventDefault();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [judge, goNeighbour, goNextOpen, id, toggle3d, copyTeleport, showReplay, setTab, verdict.isPending]);

  if (!Number.isInteger(id) || id <= 0) return <EmptyState title="Unknown finding" />;
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

  const size = boxSize(data.box);
  const canShare = !!me.data?.canShare;
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
            <TooltipContent>Previous (← or K)</TooltipContent>
          </Tooltip>
          <Tooltip>
            <TooltipTrigger asChild>
              <Button variant="outline" size="icon" className="size-8" onClick={() => void goNeighbour(1)} aria-label="Next finding">
                <ChevronRight />
              </Button>
            </TooltipTrigger>
            <TooltipContent>Next (→ or J)</TooltipContent>
          </Tooltip>
          <Tooltip>
            <TooltipTrigger asChild>
              <Button variant="outline" size="sm" className="h-8" onClick={() => void goNextOpen(id)}>
                <SkipForward /> Next open
              </Button>
            </TooltipTrigger>
            <TooltipContent>Space</TooltipContent>
          </Tooltip>
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_320px]">
        <div className="min-w-0 space-y-4">
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="text-2xl font-semibold tracking-tight">#{data.id}</h1>
            <VerdictBadge review={data.review} />
            <SourceBadge source={data.source} />
            <IndicatorIcons finding={data} />
          </div>

          <Tabs value={tab} onValueChange={(value) => setTab(value as Tab)}>
            <TabsList>
              {data.hasEvidence && <TabsTrigger value="replay">Replay</TabsTrigger>}
              <TabsTrigger value="scene">Scene</TabsTrigger>
              <TabsTrigger value="3d">
                3D <kbd className="ml-1">V</kbd>
              </TabsTrigger>
            </TabsList>
            {data.hasEvidence && (
              <TabsContent value="replay" forceMount className="mt-3 data-[state=inactive]:hidden">
                {!visited.has("replay") ? null : evidence.isPending || palette.isPending ? (
                  <PageSpinner className="h-[64vh]" />
                ) : evidence.isError || palette.isError || !evidence.data || !palette.data ? (
                  <ErrorState error={evidence.error ?? palette.error} onRetry={() => void evidence.refetch()} />
                ) : (
                  <Suspense fallback={<PageSpinner className="h-[64vh]" />}>
                    <ReplayView
                      key={data.id}
                      evidence={evidence.data}
                      palette={palette.data}
                      skinUrl={urls.skin}
                      showCoordinates
                      controlsRef={replayControls}
                      active={tab === "replay"}
                    />
                  </Suspense>
                )}
              </TabsContent>
            )}
            <TabsContent value="scene" className="mt-3">
              {scene.isPending || palette.isPending ? (
                <PageSpinner className="h-[50vh]" />
              ) : scene.isError || !scene.data ? (
                <ErrorState error={scene.error} onRetry={() => void scene.refetch()} />
              ) : (
                <SceneView
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
            <TabsContent value="3d" forceMount className="mt-3 data-[state=inactive]:hidden">
              {!visited.has("3d") ? null : scene.data && palette.data ? (
                <Suspense fallback={<PageSpinner className="h-[64vh]" />}>
                  <TerrainView
                    key={data.id}
                    scene={scene.data}
                    colours={colours}
                    heatmap={overlay ? (heatmap.data ?? null) : null}
                    tint={overlay}
                    onTint={setOverlay}
                    active={tab === "3d"}
                  />
                </Suspense>
              ) : scene.isError ? (
                <ErrorState error={scene.error} />
              ) : (
                <PageSpinner className="h-[50vh]" />
              )}
            </TabsContent>
          </Tabs>
        </div>

        <aside className="space-y-4">
          <Card className="gap-4 py-4">
            <CardContent className="space-y-4 px-4">
              <VerdictButtons finding={data} pending={verdict.isPending} onVerdict={(value) => judge(value, false)} />
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
              {data.detail && <p className="text-xs [overflow-wrap:anywhere] text-muted-foreground">{data.detail}</p>}
              <div className="space-y-1.5">
                <div className="text-xs text-muted-foreground">Players</div>
                {data.players.length ? (
                  <PlayerList players={data.players} />
                ) : (
                  <p className="text-sm text-muted-foreground">No players recorded.</p>
                )}
              </div>
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
                    <TooltipContent>Copy (C)</TooltipContent>
                  </Tooltip>
                </div>
              </div>
            </CardContent>
          </Card>

          {shareView(data, canShare) !== "hidden" && <SharePanel finding={data} canShare={canShare} />}

          <Card className="gap-2 py-4">
            <CardContent className="px-4">
              <div className="mb-2 flex items-center gap-2 text-xs font-medium text-muted-foreground">
                <Keyboard className="size-3.5" /> Shortcuts
              </div>
              <ul className="grid grid-cols-1 gap-1.5 text-xs">
                {shortcutHelp.map((entry) => (
                  <li key={entry.label} className="flex items-center justify-between gap-2">
                    <span className="text-muted-foreground">{entry.label}</span>
                    <span className="flex gap-1">
                      {entry.keys.map((key) => (
                        <kbd key={key}>{key}</kbd>
                      ))}
                    </span>
                  </li>
                ))}
              </ul>
              <p className="mt-2 text-[11px] text-muted-foreground">Keys 1 and 2 record the verdict and open the next open finding.</p>
            </CardContent>
          </Card>
        </aside>
      </div>
    </div>
  );
}
