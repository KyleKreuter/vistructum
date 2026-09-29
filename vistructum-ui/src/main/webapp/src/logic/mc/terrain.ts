import type { Heatmap, Scene } from "@/api/types";
import { heatAlpha, heatPeak } from "@/logic/sceneLayers";
import { bakeModel, type RawQuad } from "./bake";
import { DOWN, UP } from "./direction";
import { isFullFace, type RawBlock } from "./library";
import type { Grid, Overlay } from "./mesher";
import { parseElement } from "./models";
import { materialClass } from "./procedural";

export interface TerrainGrid {
  grid: Grid;
  floor: number;
  states: string[];
}

export const airState = "minecraft:air";

export function terrainGrid(scene: Scene): TerrainGrid {
  let floor = Number.POSITIVE_INFINITY;
  let top = Number.NEGATIVE_INFINITY;
  for (let i = 0; i < scene.blocks.length; i++) {
    if (scene.blocks[i] < 0) continue;
    floor = Math.min(floor, scene.heights[i]);
    top = Math.max(top, scene.heights[i]);
  }
  if (!Number.isFinite(floor)) floor = top = 0;
  floor -= 1;
  const sizeX = scene.width;
  const sizeZ = scene.height;
  const sizeY = top - floor + 1;
  const cells = new Int32Array(sizeX * sizeY * sizeZ);
  for (let i = 0; i < scene.blocks.length; i++) {
    const block = scene.blocks[i];
    if (block < 0) continue;
    const x = i % sizeX;
    const z = (i - x) / sizeX;
    const height = scene.heights[i] - floor;
    for (let y = 0; y <= height; y++) cells[(y * sizeZ + z) * sizeX + x] = block + 1;
  }
  return { grid: { sizeX, sizeY, sizeZ, cells }, floor, states: [airState, ...scene.palette] };
}

export function terrainOverlay(terrain: TerrainGrid, heatmap: Heatmap | null, strength = 0.85): Overlay | undefined {
  const { grid } = terrain;
  if (!heatmap || heatmap.width !== grid.sizeX || heatmap.height !== grid.sizeZ) return undefined;
  const peak = heatPeak(heatmap.values);
  const alpha = new Float32Array(grid.cells.length);
  const layer = grid.sizeX * grid.sizeZ;
  for (let i = 0; i < layer; i++) {
    const value = heatAlpha(heatmap.values[i] ?? 0, peak) * strength;
    if (value <= 0) continue;
    for (let y = 0; y < grid.sizeY; y++) alpha[y * layer + i] = value;
  }
  return { alpha, colour: [1, 0.27, 0.12] };
}

function pick(quads: RawQuad[], test: (quad: RawQuad) => boolean): RawQuad | undefined {
  return quads.filter(test).sort((a, b) => Number(isFullFace(b)) - Number(isFullFace(a)))[0];
}

function cube(up: RawQuad, side: RawQuad, down: RawQuad): RawQuad[] {
  const face = (quad: RawQuad, name: string) => ({ texture: quad.texture, tintindex: quad.tintindex, cullface: name });
  const element = parseElement({
    from: [0, 0, 0],
    to: [16, 16, 16],
    faces: {
      up: face(up, "up"),
      down: face(down, "down"),
      north: face(side, "north"),
      south: face(side, "south"),
      west: face(side, "west"),
      east: face(side, "east"),
    },
  });
  return element ? bakeModel({ elements: [element], textures: {}, ambientOcclusion: true, display: {}, generated: false }, { x: 0, y: 0, uvlock: false }) : [];
}

export function columnBlock(raw: RawBlock, ground: RawBlock | null): RawBlock {
  if (raw.empty || (raw.liquid && !raw.quads.length)) return raw;
  const full = raw.quads.filter(isFullFace);
  if (new Set(full.map((quad) => quad.flush)).size === 6) return { ...raw, quads: full, ambientOcclusion: true };
  const aligned = raw.quads.filter((quad) => quad.axisAligned);
  if (!aligned.length) {
    if (ground && (materialClass(raw.state.name) === "plant" || materialClass(raw.state.name) === "torch")) return { ...ground, state: raw.state, liquid: raw.liquid, liquidTexture: raw.liquidTexture };
    const first = raw.quads[0];
    return first ? { ...raw, quads: cube(first, first, first), ambientOcclusion: true } : raw;
  }
  const up = pick(aligned, (quad) => quad.normal === UP) ?? aligned[0];
  const side = pick(aligned, (quad) => quad.normal !== UP && quad.normal !== DOWN) ?? up;
  const down = pick(aligned, (quad) => quad.normal === DOWN) ?? side;
  return { ...raw, quads: cube(up, side, down), ambientOcclusion: true };
}
