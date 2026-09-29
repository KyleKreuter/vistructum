import type { Box, Scene, Terrain } from "@/api/types";

const margin = 16;
const depth = 6;
const canopy = 4;

export function sceneTerrain(scene: Scene, box: Box): Terrain | null {
  if (scene.source !== "fullscan") return null;
  const { window } = scene;
  const left = Math.max(0, window.left - margin);
  const right = Math.min(scene.width - 1, window.right + margin);
  const top = Math.max(0, window.top - margin);
  const bottom = Math.min(scene.height - 1, window.bottom + margin);
  const sizeX = right - left + 1;
  const sizeZ = bottom - top + 1;
  const palette = ["minecraft:air", "minecraft:stone", "minecraft:dirt", "minecraft:grass_block[snowy=false]", "minecraft:oak_log[axis=y]", ...scene.palette];
  const leafy = scene.palette.map((name) => name.includes("leaves"));
  const heights: number[] = [];
  const blocks: number[] = [];
  let low = Number.POSITIVE_INFINITY;
  let high = Number.NEGATIVE_INFINITY;
  for (let z = top; z <= bottom; z++) {
    for (let x = left; x <= right; x++) {
      const index = z * scene.width + x;
      const block = scene.blocks[index];
      const height = scene.heights[index];
      blocks.push(block);
      heights.push(height);
      if (block < 0) continue;
      low = Math.min(low, leafy[block] ? height - canopy : height);
      high = Math.max(high, height);
    }
  }
  if (!Number.isFinite(low)) return null;
  const floor = low - depth;
  const sizeY = high - floor + 1;
  const cells = new Array<number>(sizeX * sizeY * sizeZ).fill(0);
  const set = (x: number, y: number, z: number, value: number) => {
    cells[((y - floor) * sizeZ + z) * sizeX + x] = value;
  };
  for (let z = 0; z < sizeZ; z++) {
    for (let x = 0; x < sizeX; x++) {
      const block = blocks[z * sizeX + x];
      if (block < 0) continue;
      const height = heights[z * sizeX + x];
      const tree = leafy[block];
      const ground = tree ? height - canopy : height;
      for (let y = floor; y <= ground; y++) set(x, y, z, y > ground - 3 ? 2 : 1);
      set(x, ground, z, tree ? 3 : block + 5);
      if (!tree) continue;
      set(x, height, z, block + 5);
      set(x, height - 1, z, block + 5);
      if ((x * 7 + z * 13) % 9 === 0) for (let y = ground + 1; y < height; y++) set(x, y, z, 4);
    }
  }
  const windowHeights = [...heights].filter((_, i) => blocks[i] >= 0).sort((a, b) => a - b);
  const offset = box.minY - windowHeights[Math.floor(windowHeights.length / 2)];
  return {
    blocks: { minX: box.minX - window.left + left, minY: floor + offset, minZ: box.minZ - window.top + top, sizeX, sizeY, sizeZ, palette, cells },
    sceneOrigin: { x: box.minX - window.left, z: box.minZ - window.top },
  };
}
