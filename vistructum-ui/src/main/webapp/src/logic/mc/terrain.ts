import type { Heatmap, Scene } from "@/api/types";
import { heatAlpha, heatPeak } from "@/logic/sceneLayers";
import { bakeModel, type RawQuad } from "./bake";
import { srgbToLinear } from "./colour";
import { DOWN, UP } from "./direction";
import { isFullFace, type RawBlock } from "./library";
import type { Grid } from "./mesher";
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

export interface HeatSurface {
  positions: Float32Array;
  colours: Float32Array;
  indices: Uint32Array;
}

export const heatColour: [number, number, number] = [1, 0.27, 0.12];

export function heatSurface(terrain: TerrainGrid, heatmap: Heatmap | null, strength = 0.85): HeatSurface | null {
  const { grid } = terrain;
  if (!heatmap || heatmap.width !== grid.sizeX || heatmap.height !== grid.sizeZ) return null;
  const peak = heatPeak(heatmap.values);
  const columns: { x: number; z: number; top: number; alpha: number }[] = [];
  const layer = grid.sizeX * grid.sizeZ;
  for (let i = 0; i < layer; i++) {
    const alpha = heatAlpha(heatmap.values[i] ?? 0, peak) * strength;
    if (alpha <= 0) continue;
    let top = grid.sizeY - 1;
    while (top >= 0 && grid.cells[top * layer + i] === 0) top--;
    if (top < 0) continue;
    const x = i % grid.sizeX;
    columns.push({ x, z: (i - x) / grid.sizeX, top: top + 1 + 1 / 64, alpha });
  }
  const positions = new Float32Array(columns.length * 12);
  const colours = new Float32Array(columns.length * 16);
  const indices = new Uint32Array(columns.length * 6);
  const linear = heatColour.map(srgbToLinear);
  columns.forEach(({ x, z, top, alpha }, n) => {
    positions.set([x, top, z, x, top, z + 1, x + 1, top, z + 1, x + 1, top, z], n * 12);
    for (let v = 0; v < 4; v++) colours.set([...linear, alpha], n * 16 + v * 4);
    indices.set([n * 4, n * 4 + 1, n * 4 + 2, n * 4, n * 4 + 2, n * 4 + 3], n * 6);
  });
  return { positions, colours, indices };
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
  const standsOnGround = materialClass(raw.state.name) === "plant" || materialClass(raw.state.name) === "torch" || !aligned.some((quad) => quad.normal === UP);
  if (ground && standsOnGround) return { ...ground, liquid: raw.liquid, liquidTexture: raw.liquidTexture };
  if (!aligned.length) {
    const first = raw.quads[0];
    return first ? { ...raw, quads: cube(first, first, first), ambientOcclusion: true } : raw;
  }
  const up = pick(aligned, (quad) => quad.normal === UP) ?? aligned[0];
  const side = pick(aligned, (quad) => quad.normal !== UP && quad.normal !== DOWN) ?? up;
  const down = pick(aligned, (quad) => quad.normal === DOWN) ?? side;
  return { ...raw, quads: cube(up, side, down), ambientOcclusion: true };
}
