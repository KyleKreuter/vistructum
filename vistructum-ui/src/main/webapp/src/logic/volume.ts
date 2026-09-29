import type { BlockChange, BlockVolume } from "@/api/types";
import { isAir } from "./blocks";

export interface Cell {
  dx: number;
  dy: number;
  dz: number;
}

export function cellIndex(volume: Pick<BlockVolume, "sizeX" | "sizeZ">, dx: number, dy: number, dz: number): number {
  return (dy * volume.sizeZ + dz) * volume.sizeX + dx;
}

export function cellOf(volume: Pick<BlockVolume, "sizeX" | "sizeZ">, index: number): Cell {
  const dx = index % volume.sizeX;
  const rest = (index - dx) / volume.sizeX;
  const dz = rest % volume.sizeZ;
  const dy = (rest - dz) / volume.sizeZ;
  return { dx, dy, dz };
}

export function contains(volume: Pick<BlockVolume, "sizeX" | "sizeY" | "sizeZ">, dx: number, dy: number, dz: number): boolean {
  return dx >= 0 && dy >= 0 && dz >= 0 && dx < volume.sizeX && dy < volume.sizeY && dz < volume.sizeZ;
}

export function worldToLocal(volume: Pick<BlockVolume, "minX" | "minY" | "minZ">, x: number, y: number, z: number): Cell {
  return { dx: x - volume.minX, dy: y - volume.minY, dz: z - volume.minZ };
}

export function worldIndex(volume: BlockVolume, x: number, y: number, z: number): number {
  const { dx, dy, dz } = worldToLocal(volume, x, y, z);
  return contains(volume, dx, dy, dz) ? cellIndex(volume, dx, dy, dz) : -1;
}

export interface IndexedVolume {
  volume: BlockVolume;
  states: string[];
  cells: Int32Array;
  airState: number;
  changeCells: Int32Array;
  changeStates: Int32Array;
  dynamicCells: number[];
}

export function indexVolume(volume: BlockVolume, changes: BlockChange[]): IndexedVolume {
  const states = [...volume.palette];
  const lookup = new Map(states.map((state, index) => [state, index]));
  const stateIndex = (state: string) => {
    const known = lookup.get(state);
    if (known !== undefined) return known;
    states.push(state);
    lookup.set(state, states.length - 1);
    return states.length - 1;
  };
  let airState = states.findIndex((state) => isAir(state));
  if (airState < 0) airState = stateIndex("minecraft:air");
  const changeCells = new Int32Array(changes.length);
  const changeStates = new Int32Array(changes.length);
  const dynamic = new Set<number>();
  changes.forEach((change, i) => {
    const cell = worldIndex(volume, change.x, change.y, change.z);
    changeCells[i] = cell;
    changeStates[i] = change.action === "BREAK" ? airState : stateIndex(change.blockData);
    if (cell >= 0) dynamic.add(cell);
  });
  return {
    volume,
    states,
    cells: Int32Array.from(volume.cells),
    airState,
    changeCells,
    changeStates,
    dynamicCells: [...dynamic].sort((a, b) => a - b),
  };
}

export function dynamicStates(indexed: IndexedVolume, applied: number): Int32Array {
  const position = new Map(indexed.dynamicCells.map((cell, i) => [cell, i]));
  const result = new Int32Array(indexed.dynamicCells.length);
  indexed.dynamicCells.forEach((cell, i) => {
    result[i] = indexed.cells[cell];
  });
  const limit = Math.min(applied, indexed.changeCells.length);
  for (let i = 0; i < limit; i++) {
    const slot = position.get(indexed.changeCells[i]);
    if (slot !== undefined) result[slot] = indexed.changeStates[i];
  }
  return result;
}

