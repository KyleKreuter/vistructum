import { cutoutAlpha, translucentAlpha, type AlphaClass } from "./atlas";
import { srgbToLinear } from "./colour";
import { directionAxis, directionVectors } from "./direction";
import type { MeshBlock, MeshQuad } from "./library";
import { sectionSize, type GridSize } from "./sections";

export interface Grid extends GridSize {
  cells: Int32Array;
}

export interface Overlay {
  alpha: Float32Array;
  colour: [number, number, number];
}

export interface LayerGeometry {
  positions: Float32Array;
  uvs: Float32Array;
  colours: Float32Array;
  indices: Uint32Array;
  centroids: Float32Array;
  quadCount: number;
}

export type SectionGeometry = [LayerGeometry, LayerGeometry, LayerGeometry];

class LayerBuilder {
  positions = new Float32Array(1024 * 12);
  uvs = new Float32Array(1024 * 8);
  colours = new Float32Array(1024 * 12);
  indices = new Uint32Array(1024 * 6);
  centroids = new Float32Array(1024 * 3);
  quads = 0;

  reserve() {
    if (this.quads * 12 < this.positions.length) return;
    const grow = <T extends Float32Array | Uint32Array>(array: T, factory: (length: number) => T): T => {
      const next = factory(array.length * 2);
      next.set(array);
      return next;
    };
    this.positions = grow(this.positions, (n) => new Float32Array(n));
    this.uvs = grow(this.uvs, (n) => new Float32Array(n));
    this.colours = grow(this.colours, (n) => new Float32Array(n));
    this.indices = grow(this.indices, (n) => new Uint32Array(n));
    this.centroids = grow(this.centroids, (n) => new Float32Array(n));
  }

  build(): LayerGeometry {
    const q = this.quads;
    return {
      positions: this.positions.slice(0, q * 12),
      uvs: this.uvs.slice(0, q * 8),
      colours: this.colours.slice(0, q * 12),
      indices: this.indices.slice(0, q * 6),
      centroids: this.centroids.slice(0, q * 3),
      quadCount: q,
    };
  }
}

const aoStep = 0.2;
const cornerValues = new Float32Array(4);
const vertexAo = new Float32Array(4);
const vertexColour = new Float32Array(3);

function occluderAt(grid: Grid, occluders: Uint8Array, x: number, y: number, z: number): number {
  if (x < 0 || y < 0 || z < 0 || x >= grid.sizeX || y >= grid.sizeY || z >= grid.sizeZ) return 0;
  return occluders[grid.cells[(y * grid.sizeZ + z) * grid.sizeX + x]];
}

const tangents = directionAxis.map((axis) => {
  const t1 = [0, 0, 0];
  const t2 = [0, 0, 0];
  t1[(axis + 1) % 3] = 1;
  t2[(axis + 2) % 3] = 1;
  return [t1[0], t1[1], t1[2], t2[0], t2[1], t2[2]];
});

function computeCorners(grid: Grid, occluders: Uint8Array, x: number, y: number, z: number, direction: number) {
  const [ax, ay, az, bx, by, bz] = tangents[direction];
  for (let corner = 0; corner < 4; corner++) {
    const s1 = corner & 1 ? 1 : -1;
    const s2 = corner & 2 ? 1 : -1;
    const side1 = occluderAt(grid, occluders, x + ax * s1, y + ay * s1, z + az * s1);
    const side2 = occluderAt(grid, occluders, x + bx * s2, y + by * s2, z + bz * s2);
    const diagonal = occluderAt(grid, occluders, x + ax * s1 + bx * s2, y + ay * s1 + by * s2, z + az * s1 + bz * s2);
    const count = side1 && side2 ? 3 : side1 + side2 + diagonal;
    cornerValues[corner] = 1 - aoStep * count;
  }
}

function emit(
  builder: LayerBuilder,
  quad: MeshQuad,
  x: number,
  y: number,
  z: number,
  grid: Grid,
  occluders: Uint8Array,
  overlayAlpha: number,
  overlayColour: [number, number, number] | null,
) {
  builder.reserve();
  const q = builder.quads;
  if (quad.aoDir >= 0) {
    const vector = directionVectors[quad.aoDir];
    const px = quad.aoFlush ? x + vector[0] : x;
    const py = quad.aoFlush ? y + vector[1] : y;
    const pz = quad.aoFlush ? z + vector[2] : z;
    computeCorners(grid, occluders, px, py, pz, quad.aoDir);
    for (let i = 0; i < 4; i++) {
      const a = quad.aoCoords[i * 2];
      const b = quad.aoCoords[i * 2 + 1];
      vertexAo[i] = (1 - a) * (1 - b) * cornerValues[0] + a * (1 - b) * cornerValues[1] + (1 - a) * b * cornerValues[2] + a * b * cornerValues[3];
    }
  } else {
    vertexAo[0] = vertexAo[1] = vertexAo[2] = vertexAo[3] = 1;
  }
  const positions = builder.positions;
  const uvs = builder.uvs;
  const colours = builder.colours;
  let cx = 0;
  let cy = 0;
  let cz = 0;
  for (let i = 0; i < 4; i++) {
    const px = x + quad.positions[i * 3];
    const py = y + quad.positions[i * 3 + 1];
    const pz = z + quad.positions[i * 3 + 2];
    positions[q * 12 + i * 3] = px;
    positions[q * 12 + i * 3 + 1] = py;
    positions[q * 12 + i * 3 + 2] = pz;
    cx += px;
    cy += py;
    cz += pz;
    uvs[q * 8 + i * 2] = quad.uvs[i * 2];
    uvs[q * 8 + i * 2 + 1] = quad.uvs[i * 2 + 1];
    const light = vertexAo[i];
    for (let k = 0; k < 3; k++) {
      let value = quad.colour[k] * light;
      if (overlayColour && overlayAlpha > 0) value = value * (1 - overlayAlpha) + overlayColour[k] * overlayAlpha;
      vertexColour[k] = srgbToLinear(value);
    }
    colours[q * 12 + i * 3] = vertexColour[0];
    colours[q * 12 + i * 3 + 1] = vertexColour[1];
    colours[q * 12 + i * 3 + 2] = vertexColour[2];
  }
  builder.centroids[q * 3] = cx / 4;
  builder.centroids[q * 3 + 1] = cy / 4;
  builder.centroids[q * 3 + 2] = cz / 4;
  const base = q * 4;
  const indices = builder.indices;
  if (vertexAo[1] + vertexAo[3] > vertexAo[0] + vertexAo[2]) {
    indices[q * 6] = base + 1;
    indices[q * 6 + 1] = base + 2;
    indices[q * 6 + 2] = base + 3;
    indices[q * 6 + 3] = base + 1;
    indices[q * 6 + 4] = base + 3;
    indices[q * 6 + 5] = base;
  } else {
    indices[q * 6] = base;
    indices[q * 6 + 1] = base + 1;
    indices[q * 6 + 2] = base + 2;
    indices[q * 6 + 3] = base;
    indices[q * 6 + 4] = base + 2;
    indices[q * 6 + 5] = base + 3;
  }
  builder.quads = q + 1;
}

export function occluderTable(blocks: MeshBlock[]): Uint8Array {
  return Uint8Array.from(blocks, (block) => (block.occluder ? 1 : 0));
}

function builderFor(builders: LayerBuilder[], layer: AlphaClass): LayerBuilder {
  return builders[layer === translucentAlpha ? 2 : layer === cutoutAlpha ? 1 : 0];
}

export function meshSection(grid: Grid, blocks: MeshBlock[], occluders: Uint8Array, sx: number, sy: number, sz: number, overlay?: Overlay): SectionGeometry {
  const builders = [new LayerBuilder(), new LayerBuilder(), new LayerBuilder()];
  const { sizeX, sizeY, sizeZ, cells } = grid;
  const x0 = sx * sectionSize;
  const y0 = sy * sectionSize;
  const z0 = sz * sectionSize;
  const x1 = Math.min(sizeX, x0 + sectionSize);
  const y1 = Math.min(sizeY, y0 + sectionSize);
  const z1 = Math.min(sizeZ, z0 + sectionSize);
  const strideY = sizeX * sizeZ;
  for (let y = y0; y < y1; y++) {
    for (let z = z0; z < z1; z++) {
      for (let x = x0; x < x1; x++) {
        const index = (y * sizeZ + z) * sizeX + x;
        const block = blocks[cells[index]];
        if (!block || block.empty) continue;
        const overlayAlpha = overlay ? overlay.alpha[index] : 0;
        const overlayColour = overlay ? overlay.colour : null;
        if (block.quads.length) {
          const builder = builderFor(builders, block.layer);
          for (const quad of block.quads) {
            const cull = quad.cull;
            if (cull >= 0) {
              const vector = directionVectors[cull];
              const nx = x + vector[0];
              const ny = y + vector[1];
              const nz = z + vector[2];
              if (nx >= 0 && ny >= 0 && nz >= 0 && nx < sizeX && ny < sizeY && nz < sizeZ) {
                const neighbour = blocks[cells[index + vector[0] + vector[2] * sizeX + vector[1] * strideY]];
                if (neighbour && (neighbour.occluder || (block.cullGroup !== 0 && neighbour.cullGroup === block.cullGroup))) continue;
              }
            }
            emit(builder, quad, x, y, z, grid, occluders, overlayAlpha, overlayColour);
          }
        }
        if (block.liquid) {
          const above = y + 1 < sizeY ? blocks[cells[index + strideY]] : undefined;
          const covered = !!above && above.liquid === block.liquid;
          const builder = builderFor(builders, block.liquidLayer);
          for (const quad of covered ? block.liquidFull : block.liquidLow) {
            const cull = quad.cull;
            if (cull >= 0) {
              const vector = directionVectors[cull];
              const nx = x + vector[0];
              const ny = y + vector[1];
              const nz = z + vector[2];
              if (nx >= 0 && ny >= 0 && nz >= 0 && nx < sizeX && ny < sizeY && nz < sizeZ) {
                const neighbour = blocks[cells[index + vector[0] + vector[2] * sizeX + vector[1] * strideY]];
                if (neighbour && (neighbour.occluder || neighbour.liquid === block.liquid)) continue;
              }
            }
            emit(builder, quad, x, y, z, grid, occluders, overlayAlpha, overlayColour);
          }
        }
      }
    }
  }
  return [builders[0].build(), builders[1].build(), builders[2].build()];
}

export function sortTranslucent(layer: LayerGeometry, eyeX: number, eyeY: number, eyeZ: number): Uint32Array {
  const count = layer.quadCount;
  const order = new Uint32Array(count);
  const distances = new Float32Array(count);
  for (let q = 0; q < count; q++) {
    order[q] = q;
    const dx = layer.centroids[q * 3] - eyeX;
    const dy = layer.centroids[q * 3 + 1] - eyeY;
    const dz = layer.centroids[q * 3 + 2] - eyeZ;
    distances[q] = dx * dx + dy * dy + dz * dz;
  }
  order.sort((a, b) => distances[b] - distances[a]);
  const indices = new Uint32Array(count * 6);
  for (let n = 0; n < count; n++) indices.set(layer.indices.subarray(order[n] * 6, order[n] * 6 + 6), n * 6);
  return indices;
}

