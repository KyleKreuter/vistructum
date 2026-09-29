export const sectionSize = 16;

export interface GridSize {
  sizeX: number;
  sizeY: number;
  sizeZ: number;
}

export interface SectionLayout {
  countX: number;
  countY: number;
  countZ: number;
  total: number;
}

export function sectionLayout(size: GridSize): SectionLayout {
  const countX = Math.ceil(size.sizeX / sectionSize);
  const countY = Math.ceil(size.sizeY / sectionSize);
  const countZ = Math.ceil(size.sizeZ / sectionSize);
  return { countX, countY, countZ, total: countX * countY * countZ };
}

export function sectionIndex(layout: SectionLayout, sx: number, sy: number, sz: number): number {
  return (sy * layout.countZ + sz) * layout.countX + sx;
}

export function sectionCoordinates(layout: SectionLayout, index: number): [number, number, number] {
  const sx = index % layout.countX;
  const rest = (index - sx) / layout.countX;
  const sz = rest % layout.countZ;
  return [sx, (rest - sz) / layout.countZ, sz];
}

export function dirtySections(size: GridSize, changedCells: Iterable<number>): Set<number> {
  const layout = sectionLayout(size);
  const result = new Set<number>();
  for (const cell of changedCells) {
    const x = cell % size.sizeX;
    const rest = (cell - x) / size.sizeX;
    const z = rest % size.sizeZ;
    const y = (rest - z) / size.sizeZ;
    for (let dy = -1; dy <= 1; dy++) {
      for (let dz = -1; dz <= 1; dz++) {
        for (let dx = -1; dx <= 1; dx++) {
          const nx = x + dx;
          const ny = y + dy;
          const nz = z + dz;
          if (nx < 0 || ny < 0 || nz < 0 || nx >= size.sizeX || ny >= size.sizeY || nz >= size.sizeZ) continue;
          result.add(sectionIndex(layout, Math.floor(nx / sectionSize), Math.floor(ny / sectionSize), Math.floor(nz / sectionSize)));
        }
      }
    }
  }
  return result;
}

export function changedCells(previous: Int32Array, next: Int32Array): number[] {
  const result: number[] = [];
  for (let i = 0; i < next.length; i++) if (previous[i] !== next[i]) result.push(i);
  return result;
}
