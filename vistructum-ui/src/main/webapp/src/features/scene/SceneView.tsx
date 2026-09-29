import { useEffect, useMemo, useRef, useState } from "react";
import type { Heatmap, Scene } from "@/api/types";
import { Label } from "@/components/ui/label";
import { Slider } from "@/components/ui/slider";
import { Switch } from "@/components/ui/switch";
import { displayMaterial } from "@/logic/blocks";
import { layers, paintLayer, pointToCell, readCell, windowBaseHeight, type CellReadout, type LayerKey } from "@/logic/sceneLayers";
import { cn } from "@/lib/utils";

interface SceneViewProps {
  scene: Scene;
  colours: number[];
  heatmap: Heatmap | null;
  heatmapState: "idle" | "loading" | "ready" | "unavailable";
  layer: number;
  onLayer: (index: number) => void;
  overlay: boolean;
  onOverlay: (value: boolean) => void;
}

function LayerCanvas({
  scene,
  colours,
  layer,
  heatmap,
  overlay,
  span,
  showWindow,
  onHover,
  maxHeight,
}: {
  scene: Scene;
  colours: number[];
  layer: LayerKey;
  heatmap: Heatmap | null;
  overlay: boolean;
  span: number;
  showWindow: boolean;
  onHover?: (cell: { row: number; col: number } | null) => void;
  maxHeight?: string;
}) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const pixels = useMemo(() => paintLayer(scene, colours, layer, { span, heatmap, overlay }), [scene, colours, layer, span, heatmap, overlay]);

  useEffect(() => {
    const canvas = canvasRef.current;
    const context = canvas?.getContext("2d");
    if (!canvas || !context) return;
    context.putImageData(new ImageData(new Uint8ClampedArray(pixels), scene.width, scene.height), 0, 0);
  }, [pixels, scene.width, scene.height]);

  const { top, left, bottom, right } = scene.window;
  return (
    <div
      className="relative mx-auto w-full"
      style={{
        aspectRatio: `${scene.width} / ${scene.height}`,
        maxWidth: maxHeight ? `calc(${maxHeight} * ${scene.width / scene.height})` : undefined,
      }}
      onMouseMove={(event) => {
        if (!onHover) return;
        const rect = event.currentTarget.getBoundingClientRect();
        onHover(pointToCell(rect, scene, event.clientX, event.clientY));
      }}
      onMouseLeave={() => onHover?.(null)}
    >
      <canvas ref={canvasRef} width={scene.width} height={scene.height} className="pixelated absolute inset-0 size-full rounded-sm" />
      {showWindow && (
        <svg className="pointer-events-none absolute inset-0 size-full" viewBox={`0 0 ${scene.width} ${scene.height}`} preserveAspectRatio="none">
          <rect
            x={left}
            y={top}
            width={right - left + 1}
            height={bottom - top + 1}
            fill="none"
            stroke="white"
            strokeOpacity={0.9}
            strokeWidth={1.5}
            strokeDasharray="4 3"
            vectorEffect="non-scaling-stroke"
          />
        </svg>
      )}
    </div>
  );
}

function Readout({ readout, base }: { readout: CellReadout | null; base: number }) {
  if (!readout) return <span>Hover over the map to inspect a cell.</span>;
  return (
    <span className="flex flex-wrap gap-x-4 gap-y-1">
      <span>
        row {readout.row} · col {readout.col}
      </span>
      <span className="text-foreground">{readout.material ? displayMaterial(readout.material) : "no block"}</span>
      <span>
        height {readout.height} ({readout.relative >= 0 ? "+" : ""}
        {readout.relative} vs. window median {base})
      </span>
      <span>luminance {readout.luminance}</span>
      {readout.heat > 0 && <span className="text-foreground">score drop {(readout.heat / 255).toFixed(2)}</span>}
    </span>
  );
}

export function SceneView({ scene, colours, heatmap, heatmapState, layer, onLayer, overlay, onOverlay }: SceneViewProps) {
  const [span, setSpan] = useState(4);
  const [showWindow, setShowWindow] = useState(true);
  const [hover, setHover] = useState<{ row: number; col: number } | null>(null);
  const base = useMemo(() => windowBaseHeight(scene), [scene]);
  const readout = hover ? readCell(scene, base, heatmap, hover.row, hover.col) : null;
  const active = layers[layer] ?? layers[0];
  const heatNote =
    heatmapState === "loading" ? "Computing heatmap…" : heatmapState === "unavailable" ? "Heatmap not available for this finding" : null;

  return (
    <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_220px]">
      <div className="space-y-3">
        <div className="rounded-lg bg-well p-3">
          <LayerCanvas
            scene={scene}
            colours={colours}
            layer={active.key}
            heatmap={heatmap}
            overlay={overlay}
            span={span}
            showWindow={showWindow}
            onHover={setHover}
            maxHeight="68vh"
          />
        </div>
        <div className="min-h-5 font-mono text-xs text-muted-foreground tabular-nums">
          <Readout readout={readout} base={base} />
        </div>
        <div className="flex flex-wrap items-center gap-x-6 gap-y-3 text-sm">
          <div className="flex items-center gap-2">
            <Switch id="overlay" checked={overlay} onCheckedChange={onOverlay} />
            <Label htmlFor="overlay">
              Heatmap overlay <kbd>H</kbd>
            </Label>
          </div>
          <div className="flex items-center gap-2">
            <Switch id="window" checked={showWindow} onCheckedChange={setShowWindow} />
            <Label htmlFor="window">Model window</Label>
          </div>
          {active.key === "height" && (
            <div className="flex min-w-56 items-center gap-3">
              <Label className="shrink-0">Height span ±{span}</Label>
              <Slider min={1} max={24} step={1} value={[span]} onValueChange={([value]) => setSpan(value)} />
            </div>
          )}
          {heatNote && <span className="text-muted-foreground">{heatNote}</span>}
        </div>
      </div>
      <div className="grid grid-cols-5 gap-2 xl:grid-cols-1">
        {layers.map((info, index) => (
          <button
            key={info.key}
            type="button"
            onClick={() => onLayer(index)}
            className={cn(
              "group flex flex-col gap-1.5 rounded-lg border bg-card p-1.5 text-left transition-colors hover:border-ring",
              index === layer && "border-primary ring-1 ring-primary",
            )}
          >
            <div className="rounded bg-well p-1">
              <LayerCanvas scene={scene} colours={colours} layer={info.key} heatmap={heatmap} overlay={false} span={span} showWindow={false} />
            </div>
            <div className="flex items-center justify-between gap-1 px-0.5">
              <span className="truncate text-xs font-medium">{info.title}</span>
              <kbd>{info.hotkey}</kbd>
            </div>
            <span className="hidden px-0.5 text-[11px] leading-tight text-muted-foreground xl:block">{info.hint}</span>
          </button>
        ))}
      </div>
    </div>
  );
}
