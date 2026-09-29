import { Canvas } from "@react-three/fiber";
import { ChevronLeft, ChevronRight, Orbit, Pause, Play, Video } from "lucide-react";
import { memo, useCallback, useEffect, useMemo, useState, useSyncExternalStore, type RefObject } from "react";
import type { Evidence, Palette } from "@/api/types";
import { PlayerFace } from "@/components/app/PlayerFace";
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Slider } from "@/components/ui/slider";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { useTheme } from "@/lib/theme";
import { cn } from "@/lib/utils";
import { useWorldAssets } from "@/features/world/assets";
import { useBlockLibrary } from "@/features/world/library";
import { skyTheme } from "@/features/world/skyColours";
import { displayMaterial } from "@/logic/blocks";
import { prepareTrack } from "@/logic/motion";
import { appliedCount, buildTimeline, formatClock, speeds, tallies, type Timeline } from "@/logic/timeline";
import { indexVolume } from "@/logic/volume";
import { createClock } from "./clock";
import { playerColour } from "./colours";
import { ReplayScene, type CameraMode } from "./ReplayScene";

export interface ReplayControls {
  toggle: () => void;
}

export interface ReplayViewProps {
  evidence: Evidence;
  palette: Palette;
  skinUrl: (uuid: string) => string;
  facesFromSkin?: boolean;
  showCoordinates: boolean;
  controlsRef?: RefObject<ReplayControls | null>;
  active?: boolean;
}

const TimelineMarks = memo(function TimelineMarks({ timeline, applied, colourFor, onSeek }: { timeline: Timeline; applied: number; colourFor: (uuid: string) => string; onSeek: (t: number) => void }) {
  const span = timeline.end - timeline.start;
  return (
    <div className="absolute inset-x-1 top-0 h-3">
      {timeline.changes.map((change, index) => (
        <button
          key={`${change.t}-${index}`}
          type="button"
          className="absolute top-0 h-3 w-[3px] -translate-x-1/2 rounded-full opacity-80 hover:opacity-100 focus-visible:outline-2"
          style={{
            left: `${span > 0 ? ((change.t - timeline.start) / span) * 100 : 0}%`,
            background: change.action === "BREAK" ? "#ff4d4f" : colourFor(change.player),
            opacity: index < applied ? 1 : 0.35,
          }}
          onClick={() => onSeek(change.t)}
          aria-label={`Block change ${index + 1}`}
          tabIndex={-1}
        />
      ))}
    </div>
  );
});

function idleMessage(changes: number, recorded: boolean): string {
  if (changes) return `No block changed yet · ${changes.toLocaleString("en-GB")} to come`;
  return recorded ? "No block changes recorded" : "Nothing was recorded for this finding";
}

export default function ReplayView({ evidence, palette, skinUrl, facesFromSkin = false, showCoordinates, controlsRef, active = true }: ReplayViewProps) {
  const timeline = useMemo(() => buildTimeline(evidence), [evidence]);
  const indexed = useMemo(() => indexVolume(evidence.before, timeline.changes), [evidence.before, timeline.changes]);
  const tracks = useMemo(() => evidence.recordings.map(prepareTrack), [evidence.recordings]);
  const [clock] = useState(() => createClock(timeline));
  const state = useSyncExternalStore(clock.subscribe, clock.getSnapshot);
  const [camera, setCamera] = useState<CameraMode>({ kind: "orbit" });
  const { resolved } = useTheme();

  useEffect(() => () => clock.dispose(), [clock]);

  useEffect(() => {
    if (!active) clock.pause();
  }, [active, clock]);

  const players = useMemo(() => {
    const order: { uuid: string; name: string }[] = [];
    const seen = new Set<string>();
    for (const recording of evidence.recordings) {
      if (!seen.has(recording.player)) {
        seen.add(recording.player);
        order.push({ uuid: recording.player, name: recording.playerName });
      }
    }
    for (const change of timeline.changes) {
      if (!seen.has(change.player)) {
        seen.add(change.player);
        order.push({ uuid: change.player, name: change.playerName });
      }
    }
    return order;
  }, [evidence.recordings, timeline.changes]);

  const colourFor = useCallback(
    (uuid: string) => {
      const index = players.findIndex((player) => player.uuid === uuid);
      return playerColour(index < 0 ? 0 : index);
    },
    [players],
  );

  useEffect(() => {
    if (!controlsRef) return;
    controlsRef.current = { toggle: clock.toggle };
    return () => {
      controlsRef.current = null;
    };
  }, [clock, controlsRef]);

  const applied = appliedCount(timeline, state.time);
  const counts = useMemo(() => tallies(timeline.changes, applied), [timeline.changes, applied]);
  const totalCounts = useMemo(() => tallies(timeline.changes), [timeline.changes]);
  const current = applied > 0 ? timeline.changes[applied - 1] : null;
  const span = timeline.end - timeline.start;
  const recorded = tracks.length > 0;
  const playable = span > 0;
  const seekTo = useCallback(
    (t: number) => {
      clock.pause();
      clock.seek(t);
    },
    [clock],
  );
  const assets = useWorldAssets(palette);
  const loaded = useBlockLibrary(assets, indexed.states);

  return (
    <div className="space-y-3">
      <div className="relative h-[min(64vh,680px)] min-h-80 overflow-hidden rounded-lg bg-well">
        <Canvas flat camera={{ position: [indexed.volume.sizeX / 2 + 14, indexed.volume.sizeY + 12, indexed.volume.sizeZ / 2 + 18], fov: 50, near: 0.1, far: 1000 }} dpr={[1, 2]} frameloop={active ? "always" : "never"}>
          <ReplayScene
            indexed={indexed}
            timeline={timeline}
            tracks={tracks}
            assets={assets}
            loaded={loaded}
            clock={clock}
            skinUrl={skinUrl}
            colourOf={colourFor}
            camera={camera}
            sky={skyTheme(resolved === "dark")}
          />
        </Canvas>
        {!loaded && <div className="pointer-events-none absolute inset-x-0 bottom-8 text-center text-xs text-white/80">Loading block textures…</div>}
        <div className="pointer-events-none absolute top-3 left-3 max-w-[70%] rounded-md bg-black/55 px-3 py-2 text-xs text-white/90 backdrop-blur-sm">
          {current ? (
            <>
              <div>
                Block {applied.toLocaleString("en-GB")} of {timeline.changes.length.toLocaleString("en-GB")}
              </div>
              <div className="flex items-center gap-1.5">
                <span className="size-2 rounded-full" style={{ background: current.action === "BREAK" ? "#ff4d4f" : colourFor(current.player) }} />
                <span className="truncate">
                  {current.playerName} {current.action === "PLACE" ? "placed" : "broke"} {current.action === "PLACE" ? displayMaterial(current.blockData) : "a block"}
                </span>
              </div>
              {showCoordinates && (
                <div className="text-white/60">
                  at {current.x} {current.y} {current.z}
                </div>
              )}
            </>
          ) : (
            <div>{idleMessage(timeline.changes.length, recorded)}</div>
          )}
          {!recorded && timeline.changes.length > 0 && <div className="text-white/60">Player movement was not recorded</div>}
        </div>
        <div className="absolute top-3 right-3 flex gap-1 rounded-md bg-black/55 p-1 backdrop-blur-sm">
          <Tooltip>
            <TooltipTrigger asChild>
              <Button
                size="icon"
                variant="ghost"
                className={cn("size-7 text-white hover:bg-white/15 hover:text-white", camera.kind === "orbit" && "bg-white/20")}
                onClick={() => setCamera({ kind: "orbit" })}
                aria-label="Orbit camera"
              >
                <Orbit />
              </Button>
            </TooltipTrigger>
            <TooltipContent>Orbit around the scene</TooltipContent>
          </Tooltip>
          {players
            .filter((player) => tracks.some((track) => track.player === player.uuid))
            .map((player) => (
              <Tooltip key={player.uuid}>
                <TooltipTrigger asChild>
                  <Button
                    size="icon"
                    variant="ghost"
                    className={cn("size-7 text-white hover:bg-white/15", camera.kind === "follow" && camera.player === player.uuid && "bg-white/20")}
                    onClick={() => setCamera({ kind: "follow", player: player.uuid })}
                    aria-label={`Follow ${player.name}`}
                  >
                    <Video style={{ color: colourFor(player.uuid) }} />
                  </Button>
                </TooltipTrigger>
                <TooltipContent>Follow {player.name}</TooltipContent>
              </Tooltip>
            ))}
        </div>
        <div className="pointer-events-none absolute bottom-2 left-3 text-[11px] text-white/60">Drag to rotate · right-drag to pan · scroll to zoom</div>
      </div>

      <div className="rounded-lg border bg-card p-3">
        <div className="flex flex-wrap items-center gap-2">
          <Button size="icon" variant="outline" disabled={!timeline.changes.length} onClick={() => clock.step(-1)} aria-label="Previous block change">
            <ChevronLeft />
          </Button>
          <Button size="icon" disabled={!playable} onClick={clock.toggle} aria-label={state.playing ? "Pause" : "Play"}>
            {state.playing ? <Pause /> : <Play />}
          </Button>
          <Button size="icon" variant="outline" disabled={!timeline.changes.length} onClick={() => clock.step(1)} aria-label="Next block change">
            <ChevronRight />
          </Button>
          <span className="min-w-24 px-2 text-sm tabular-nums">
            {formatClock(state.time - timeline.start)} / {formatClock(span)}
          </span>
          <Select value={String(state.speed)} onValueChange={(value) => clock.setSpeed(Number(value))}>
            <SelectTrigger size="sm" className="w-24" aria-label="Speed">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {speeds.map((speed) => (
                <SelectItem key={speed} value={String(speed)}>
                  {speed}×
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="relative mt-3 px-1 pt-4">
          <TimelineMarks timeline={timeline} applied={applied} colourFor={colourFor} onSeek={seekTo} />
          <Slider
            disabled={!playable}
            min={timeline.start}
            max={timeline.end}
            step={50}
            value={[state.time]}
            onValueChange={([value]) => clock.seek(value)}
            aria-label="Replay time"
          />
        </div>
      </div>

      <div className="flex flex-wrap gap-2">
        {players.map((player) => {
          const now = counts.get(player.uuid);
          const total = totalCounts.get(player.uuid);
          return (
            <button
              key={player.uuid}
              type="button"
              onClick={() => tracks.some((track) => track.player === player.uuid) && setCamera({ kind: "follow", player: player.uuid })}
              className="flex items-center gap-2 rounded-lg border bg-card px-3 py-2 text-left text-sm hover:border-ring"
            >
              <span className="size-2.5 rounded-full" style={{ background: colourFor(player.uuid) }} />
              <PlayerFace uuid={player.uuid} name={player.name} size={20} skin={facesFromSkin ? skinUrl(player.uuid) : undefined} />
              <span className="max-w-48 truncate font-medium" title={player.name}>
                {player.name}
              </span>
              <span className="text-xs text-muted-foreground tabular-nums">
                {now?.placed ?? 0}/{total?.placed ?? 0} placed · {now?.broken ?? 0}/{total?.broken ?? 0} broken
              </span>
            </button>
          );
        })}
      </div>
    </div>
  );
}
