import { describe, expect, it } from "vitest";
import type { TextureImage } from "./atlas";
import { fixtureSource } from "./fixtures";
import { liquidHeight } from "./library";
import { meshSection, sortTranslucent, type Grid, type LayerGeometry } from "./mesher";
import { buildLibrary, planBlocks, type BlockLibrary } from "./world";
import { plainsFoliage, plainsGrass } from "./tint";

function image(alpha: (x: number, y: number) => number): TextureImage {
  const pixels = new Uint8ClampedArray(16 * 16 * 4);
  for (let y = 0; y < 16; y++) {
    for (let x = 0; x < 16; x++) {
      const i = (y * 16 + x) * 4;
      pixels[i] = 120;
      pixels[i + 1] = 120;
      pixels[i + 2] = 120;
      pixels[i + 3] = alpha(x, y);
    }
  }
  return { width: 16, height: 16, pixels };
}

const images: Record<string, TextureImage> = {
  "block/stone": image(() => 255),
  "block/oak_planks": image(() => 255),
  "block/glass": image((x, y) => (x === 0 || y === 0 ? 255 : 0)),
  "block/oak_leaves": image((x, y) => ((x + y) % 3 ? 255 : 0)),
  "block/water_still": image(() => 180),
};

const states = [
  "minecraft:air",
  "minecraft:stone",
  "minecraft:glass",
  "minecraft:water[level=0]",
  "minecraft:oak_slab[type=bottom,waterlogged=false]",
  "minecraft:oak_leaves[distance=1,persistent=false,waterlogged=false]",
  "minecraft:oak_slab[type=bottom,waterlogged=true]",
];
const [AIR, STONE, GLASS, WATER, SLAB, LEAVES, WET_SLAB] = states.map((_, index) => index);

function library(): BlockLibrary {
  const { raws, textures } = planBlocks(states, fixtureSource);
  const strips = new Map<string, TextureImage>();
  for (const id of textures) if (images[id]) strips.set(id, images[id]);
  return buildLibrary(raws, strips, {}, { grass: plainsGrass, foliage: plainsFoliage });
}

function grid(sizeX: number, sizeY: number, sizeZ: number, fill: (x: number, y: number, z: number) => number): Grid {
  const cells = new Int32Array(sizeX * sizeY * sizeZ);
  for (let y = 0; y < sizeY; y++) for (let z = 0; z < sizeZ; z++) for (let x = 0; x < sizeX; x++) cells[(y * sizeZ + z) * sizeX + x] = fill(x, y, z);
  return { sizeX, sizeY, sizeZ, cells };
}

function colourAt(layer: LayerGeometry, quad: number, vertex: number): number {
  return layer.colours[quad * 12 + vertex * 3];
}

describe("block library", () => {
  const lib = library();

  it("classifies occluders, layers and self culling", () => {
    expect(lib.blocks[AIR].empty).toBe(true);
    expect(lib.blocks[STONE].occluder).toBe(true);
    expect(lib.blocks[STONE].layer).toBe(0);
    expect(lib.blocks[GLASS].occluder).toBe(false);
    expect(lib.blocks[GLASS].layer).toBe(1);
    expect(lib.blocks[GLASS].cullGroup).toBeGreaterThan(0);
    expect(lib.blocks[LEAVES].occluder).toBe(false);
    expect(lib.blocks[LEAVES].cullGroup).toBe(0);
    expect(lib.blocks[SLAB].occluder).toBe(false);
    expect(lib.blocks[WATER].liquid).toBe(1);
    expect(lib.blocks[WATER].liquidLayer).toBe(2);
    expect(lib.blocks[WET_SLAB].liquid).toBe(1);
    expect(lib.blocks[WET_SLAB].quads.length).toBeGreaterThan(0);
  });

  it("tints leaves with the foliage colour", () => {
    const [r, g, b] = lib.blocks[LEAVES].quads[1].colour;
    expect([Math.round(r * 255), Math.round(g * 255), Math.round(b * 255)]).toEqual([0x77, 0xab, 0x2f]);
  });

  it("maps quads into the atlas without leaving it", () => {
    for (const block of lib.blocks) for (const quad of block.quads) for (const value of quad.uvs) expect(value).toBeGreaterThanOrEqual(0);
  });
});

describe("meshing", () => {
  const lib = library();

  it("culls faces between two opaque cubes", () => {
    const pair = grid(2, 1, 1, () => STONE);
    const [opaque] = meshSection(pair, lib.blocks, lib.occluders, 0, 0, 0);
    expect(opaque.quadCount).toBe(10);
  });

  it("culls glass against glass but not against stone", () => {
    const row = grid(3, 1, 1, (x) => (x < 2 ? GLASS : STONE));
    const [opaque, cutout] = meshSection(row, lib.blocks, lib.occluders, 0, 0, 0);
    expect(cutout.quadCount).toBe(12 - 2 - 1);
    expect(opaque.quadCount).toBe(6);
  });

  it("darkens top corners next to walls like smooth lighting", () => {
    const floor = grid(3, 2, 3, (x, y, z) => (y === 0 ? STONE : x === 0 && z === 1 ? STONE : AIR));
    const [opaque] = meshSection(floor, lib.blocks, lib.occluders, 0, 0, 0);
    let found = false;
    for (let q = 0; q < opaque.quadCount; q++) {
      const ys = [0, 1, 2, 3].map((v) => opaque.positions[q * 12 + v * 3 + 1]);
      const x = opaque.positions[q * 12];
      if (ys.every((y) => y === 1) && Math.min(...[0, 1, 2, 3].map((v) => opaque.positions[q * 12 + v * 3])) === 1 && x >= 1 && opaque.positions[q * 12 + 2] <= 2) {
        const zs = [0, 1, 2, 3].map((v) => opaque.positions[q * 12 + v * 3 + 2]);
        if (Math.min(...zs) !== 1) continue;
        const values = [0, 1, 2, 3].map((v) => colourAt(opaque, q, v));
        expect(Math.min(...values)).toBeLessThan(Math.max(...values));
        found = true;
      }
    }
    expect(found).toBe(true);
  });

  it("applies directional shade per face", () => {
    const single = grid(1, 1, 1, () => STONE);
    const [opaque] = meshSection(single, lib.blocks, lib.occluders, 0, 0, 0);
    const byFace = [0, 1, 2, 3, 4, 5].map((q) => colourAt(opaque, q, 0));
    expect(byFace[1]).toBeGreaterThan(byFace[2]);
    expect(byFace[2]).toBeGreaterThan(byFace[4]);
    expect(byFace[4]).toBeGreaterThan(byFace[0]);
  });

  it("lowers the water surface unless water is above", () => {
    const column = grid(1, 2, 1, () => WATER);
    const [, , translucent] = meshSection(column, lib.blocks, lib.occluders, 0, 0, 0);
    const heights = Array.from({ length: translucent.quadCount * 4 }, (_, i) => translucent.positions[i * 3 + 1]);
    expect(Math.max(...heights)).toBeCloseTo(1 + liquidHeight);
    expect(heights.filter((value) => Math.abs(value - 1) < 1e-6).length).toBe(4 * 4);
  });

  it("renders the slab and its water when waterlogged", () => {
    const single = grid(1, 1, 1, () => WET_SLAB);
    const [opaque, , translucent] = meshSection(single, lib.blocks, lib.occluders, 0, 0, 0);
    expect(opaque.quadCount).toBe(6);
    expect(translucent.quadCount).toBe(6);
  });

  it("sorts translucent quads back to front", () => {
    const row = grid(3, 1, 1, () => WATER);
    const [, , translucent] = meshSection(row, lib.blocks, lib.occluders, 0, 0, 0);
    const sorted = sortTranslucent(translucent, 10, 0.5, 0.5);
    const firstQuad = sorted[0] >> 2;
    const lastQuad = sorted[sorted.length - 1] >> 2;
    expect(translucent.centroids[firstQuad * 3]).toBeLessThan(translucent.centroids[lastQuad * 3]);
  });
});
