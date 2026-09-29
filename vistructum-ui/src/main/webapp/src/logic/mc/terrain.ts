import type { Box, Heatmap, Terrain } from "@/api/types";
import { isAir } from "@/logic/blocks";
import { heatAlpha, heatPeak } from "@/logic/sceneLayers";
import { srgbToLinear } from "./colour";
import type { Grid } from "./mesher";

export function terrainGrid(terrain: Terrain): Grid {
  const { sizeX, sizeY, sizeZ, cells } = terrain.blocks;
  return { sizeX, sizeY, sizeZ, cells: Int32Array.from(cells) };
}

export function columnTops(terrain: Terrain): Int32Array {
  const { sizeX, sizeY, sizeZ, palette, cells } = terrain.blocks;
  const empty = palette.map(isAir);
  const layer = sizeX * sizeZ;
  const tops = new Int32Array(layer).fill(-1);
  for (let i = 0; i < layer; i++) {
    let y = sizeY - 1;
    while (y >= 0 && empty[cells[y * layer + i]]) y--;
    tops[i] = y;
  }
  return tops;
}

export function typicalTop(tops: Int32Array): number {
  const filled = [...tops].filter((top) => top >= 0).sort((a, b) => a - b);
  return filled.length ? filled[Math.floor(filled.length / 2)] + 1 : 0;
}

export interface Outline {
  x0: number;
  x1: number;
  z0: number;
  z1: number;
}

export function boxOutline(terrain: Terrain, box: Box): Outline {
  const { minX, minZ } = terrain.blocks;
  return { x0: box.minX - minX, x1: box.maxX + 1 - minX, z0: box.minZ - minZ, z1: box.maxZ + 1 - minZ };
}

export interface HeatSurface {
  positions: Float32Array;
  colours: Float32Array;
  indices: Uint32Array;
}

export const heatColour: [number, number, number] = [1, 0.27, 0.12];

export function heatSurface(terrain: Terrain, tops: Int32Array, heatmap: Heatmap | null, strength = 0.85): HeatSurface | null {
  const origin = terrain.sceneOrigin;
  if (!heatmap || !origin) return null;
  const { minX, minZ, sizeX, sizeZ } = terrain.blocks;
  const peak = heatPeak(heatmap.values);
  const columns: { x: number; z: number; top: number; alpha: number }[] = [];
  for (let z = 0; z < sizeZ; z++) {
    const row = minZ + z - origin.z;
    if (row < 0 || row >= heatmap.height) continue;
    for (let x = 0; x < sizeX; x++) {
      const col = minX + x - origin.x;
      if (col < 0 || col >= heatmap.width) continue;
      const alpha = heatAlpha(heatmap.values[row * heatmap.width + col] ?? 0, peak) * strength;
      const top = tops[z * sizeX + x];
      if (alpha <= 0 || top < 0) continue;
      columns.push({ x, z, top: top + 1 + 1 / 64, alpha });
    }
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
