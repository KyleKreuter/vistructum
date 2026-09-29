import { describe, expect, it } from "vitest";
import type { TextureImage } from "./atlas";
import { meshSection, type Grid } from "./mesher";
import { seededRandom } from "./random";
import { dirtySections, sectionCoordinates, sectionLayout } from "./sections";
import { plainsFoliage, plainsGrass } from "./tint";
import { buildLibrary, planBlocks, proceduralImage } from "./world";

const states = [
  "minecraft:air",
  "minecraft:stone",
  "minecraft:dirt",
  "minecraft:grass_block[snowy=false]",
  "minecraft:water[level=0]",
  "minecraft:oak_log[axis=y]",
  "minecraft:oak_leaves[distance=1,persistent=false,waterlogged=false]",
  "minecraft:short_grass",
  "minecraft:poppy",
  "minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]",
  "minecraft:cobblestone",
];
const [AIR, STONE, DIRT, GRASS, WATER, LOG, LEAVES, SHORT_GRASS, POPPY, STAIRS, COBBLE] = states.map((_, index) => index);

function terrain(size: number): Grid {
  const rnd = seededRandom(11);
  const cells = new Int32Array(size * size * size);
  const set = (x: number, y: number, z: number, state: number) => {
    if (x >= 0 && y >= 0 && z >= 0 && x < size && y < size && z < size) cells[(y * size + z) * size + x] = state;
  };
  const sea = Math.floor(size * 0.4);
  for (let x = 0; x < size; x++) {
    for (let z = 0; z < size; z++) {
      const height = Math.floor(size * 0.42 + Math.sin(x / 7) * 3 + Math.cos(z / 9) * 4 + Math.sin((x + z) / 13) * 2);
      for (let y = 0; y <= height; y++) set(x, y, z, y < height - 3 ? STONE : y < height ? DIRT : GRASS);
      for (let y = height + 1; y <= sea; y++) set(x, y, z, WATER);
      if (height >= sea && rnd() < 0.12) set(x, height + 1, z, rnd() < 0.8 ? SHORT_GRASS : POPPY);
    }
  }
  for (let tree = 0; tree < 14; tree++) {
    const x = 3 + Math.floor(rnd() * (size - 6));
    const z = 3 + Math.floor(rnd() * (size - 6));
    let ground = size - 1;
    while (ground > 0 && cells[(ground * size + z) * size + x] === AIR) ground--;
    if (cells[(ground * size + z) * size + x] !== GRASS) continue;
    for (let dy = -2; dy <= 1; dy++) for (let dx = -2; dx <= 2; dx++) for (let dz = -2; dz <= 2; dz++) if (Math.abs(dx) + Math.abs(dz) < 4) set(x + dx, ground + 5 + dy, z + dz, LEAVES);
    for (let y = 1; y <= 5; y++) set(x, ground + y, z, LOG);
  }
  for (let x = 20; x < 30; x++) for (let y = sea + 1; y < sea + 6; y++) set(x, y, 20, y === sea + 5 ? STAIRS : COBBLE);
  return { sizeX: size, sizeY: size, sizeZ: size, cells };
}

describe("meshing performance", () => {
  const { raws, textures } = planBlocks(states, null);
  const strips = new Map<string, TextureImage>();
  for (const id of textures) {
    const image = proceduralImage(id, {});
    if (image) strips.set(id, image);
  }
  const library = buildLibrary(raws, strips, {}, { grass: plainsGrass, foliage: plainsFoliage });
  const grid = terrain(64);
  const layout = sectionLayout(grid);

  it("meshes a 64 cube of typical terrain quickly", () => {
    for (let s = 0; s < layout.total; s++) meshSection(grid, library.blocks, library.occluders, ...sectionCoordinates(layout, s));
    const start = performance.now();
    let quads = 0;
    for (let s = 0; s < layout.total; s++) {
      const geometry = meshSection(grid, library.blocks, library.occluders, ...sectionCoordinates(layout, s));
      quads += geometry[0].quadCount + geometry[1].quadCount + geometry[2].quadCount;
    }
    const elapsed = performance.now() - start;
    console.info(`full mesh 64^3: ${elapsed.toFixed(1)} ms, ${quads} quads`);
    expect(quads).toBeGreaterThan(5000);
    expect(elapsed).toBeLessThan(1500);
  });

  it("remeshes a single replay step within a frame", () => {
    const cell = (40 * 64 + 31) * 64 + 31;
    const start = performance.now();
    grid.cells[cell] = grid.cells[cell] === AIR ? COBBLE : AIR;
    for (const section of dirtySections(grid, [cell])) meshSection(grid, library.blocks, library.occluders, ...sectionCoordinates(layout, section));
    const elapsed = performance.now() - start;
    console.info(`replay step remesh: ${elapsed.toFixed(2)} ms`);
    expect(elapsed).toBeLessThan(100);
  });
});
