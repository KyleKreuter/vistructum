import type { Heatmap, Palette, Scene } from "@/api/types";
import { colourOf } from "./blocks";

export type LayerKey = "colour" | "height" | "relief" | "luminance" | "heatmap";

export interface LayerInfo {
  key: LayerKey;
  title: string;
}

export const layers: LayerInfo[] = [
  { key: "colour", title: "Colour" },
  { key: "height", title: "Height" },
  { key: "relief", title: "Relief" },
  { key: "luminance", title: "Luminance" },
  { key: "heatmap", title: "Heatmap" },
];

export type HeatmapState = "idle" | "loading" | "ready" | "unavailable";

export function heatmapNote(state: HeatmapState): string | null {
  if (state === "loading") return "Computing heatmap…";
  return state === "unavailable" ? "Heatmap not available for this finding" : null;
}

export interface PaintOptions {
  span: number;
  heatmap: Heatmap | null;
  overlay: boolean;
}

export function sceneColours(scene: Pick<Scene, "palette">, palette: Palette): number[] {
  return scene.palette.map((key) => colourOf(key, palette));
}

export function median(values: number[]): number {
  if (!values.length) return 0;
  const sorted = [...values].sort((a, b) => a - b);
  const mid = sorted.length >> 1;
  return sorted.length % 2 ? sorted[mid] : Math.round((sorted[mid - 1] + sorted[mid]) / 2);
}

export function windowBaseHeight(scene: Scene): number {
  const values: number[] = [];
  const { top, left, bottom, right } = scene.window;
  for (let row = Math.max(0, top); row <= Math.min(scene.height - 1, bottom); row++) {
    for (let col = Math.max(0, left); col <= Math.min(scene.width - 1, right); col++) {
      const i = row * scene.width + col;
      if (scene.blocks[i] >= 0) values.push(scene.heights[i]);
    }
  }
  return median(values);
}

export function heatPeak(values: number[]): number {
  let peak = 0;
  for (const value of values) if (value > peak) peak = value;
  return peak;
}

export function heatAlpha(value: number, peak: number): number {
  return Math.min(1, value / Math.max(40, peak));
}

function shadeFactor(scene: Scene, i: number): number {
  const w = scene.width;
  if (i < w || scene.blocks[i] < 0 || scene.blocks[i - w] < 0) return 220 / 255;
  const here = scene.heights[i];
  const north = scene.heights[i - w];
  return here > north ? 1 : here === north ? 220 / 255 : 180 / 255;
}

function heightAt(scene: Scene, i: number, x: number, y: number): number {
  const cx = Math.min(scene.width - 1, Math.max(0, x));
  const cy = Math.min(scene.height - 1, Math.max(0, y));
  const j = cy * scene.width + cx;
  return scene.blocks[j] < 0 ? scene.heights[i] : scene.heights[j];
}

export function paintLayer(scene: Scene, colours: number[], layer: LayerKey, options: PaintOptions): Uint8ClampedArray {
  const { width: w, height: h } = scene;
  const out = new Uint8ClampedArray(w * h * 4);
  const base = windowBaseHeight(scene);
  const heat = options.heatmap && options.heatmap.width === w && options.heatmap.height === h ? options.heatmap.values : null;
  const peak = heat ? heatPeak(heat) : 0;
  for (let i = 0; i < w * h; i++) {
    let r = 24;
    let g = 24;
    let b = 24;
    if (scene.blocks[i] >= 0) {
      const colour = colours[scene.blocks[i]] ?? 0x9e9e9e;
      if (layer === "colour" || layer === "heatmap") {
        const f = shadeFactor(scene, i);
        r = ((colour >> 16) & 255) * f;
        g = ((colour >> 8) & 255) * f;
        b = (colour & 255) * f;
        if (layer === "heatmap") {
          const grey = (0.3 * r + 0.59 * g + 0.11 * b) * 0.55;
          r = g = b = grey;
        }
      } else if (layer === "height") {
        const t = Math.max(-1, Math.min(1, (scene.heights[i] - base) / Math.max(1, options.span)));
        if (t >= 0) {
          r = 245;
          g = 245 - 150 * t;
          b = 240 - 200 * t;
        } else {
          r = 245 + 200 * t;
          g = 245 + 110 * t;
          b = 240 + 10 * t;
        }
      } else if (layer === "relief") {
        const x = i % w;
        const y = (i - x) / w;
        const dx = heightAt(scene, i, x + 1, y) - heightAt(scene, i, x - 1, y);
        const dz = heightAt(scene, i, x, y + 1) - heightAt(scene, i, x, y - 1);
        r = g = b = Math.max(0, Math.min(255, 150 - 40 * dx - 40 * dz));
      } else {
        r = g = b = scene.luminance[i];
      }
      if (heat && (layer === "heatmap" || options.overlay)) {
        const a = heatAlpha(heat[i], peak) * (layer === "heatmap" ? 1 : 0.8);
        r = r * (1 - a) + 255 * a;
        g = g * (1 - a) + 70 * a;
        b = b * (1 - a) + 30 * a;
      }
    }
    out[4 * i] = r;
    out[4 * i + 1] = g;
    out[4 * i + 2] = b;
    out[4 * i + 3] = 255;
  }
  return out;
}

export interface CellReadout {
  row: number;
  col: number;
  material: string | null;
  height: number;
  relative: number;
  luminance: number;
  heat: number;
}

export function readCell(scene: Scene, base: number, heatmap: Heatmap | null, row: number, col: number): CellReadout | null {
  if (row < 0 || col < 0 || row >= scene.height || col >= scene.width) return null;
  const i = row * scene.width + col;
  const block = scene.blocks[i];
  return {
    row,
    col,
    material: block >= 0 ? scene.palette[block] : null,
    height: scene.heights[i],
    relative: scene.heights[i] - base,
    luminance: scene.luminance[i],
    heat: heatmap && heatmap.width === scene.width && heatmap.height === scene.height ? (heatmap.values[i] ?? 0) : 0,
  };
}

export function pointToCell(
  rect: { left: number; top: number; width: number; height: number },
  size: { width: number; height: number },
  clientX: number,
  clientY: number,
): { row: number; col: number } | null {
  const side = Math.min(rect.width / size.width, rect.height / size.height);
  if (side <= 0) return null;
  const offsetX = (rect.width - side * size.width) / 2;
  const offsetY = (rect.height - side * size.height) / 2;
  const col = Math.floor((clientX - rect.left - offsetX) / side);
  const row = Math.floor((clientY - rect.top - offsetY) / side);
  if (col < 0 || row < 0 || col >= size.width || row >= size.height) return null;
  return { row, col };
}


