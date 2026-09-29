import type { Box, BlockVolume } from "@/api/types";

export function boxCentre(box: Box): { x: number; y: number; z: number } {
  return {
    x: Math.floor((box.minX + box.maxX) / 2),
    y: Math.floor((box.minY + box.maxY) / 2),
    z: Math.floor((box.minZ + box.maxZ) / 2),
  };
}

export function boxSize(box: Box): { x: number; y: number; z: number } {
  return { x: box.maxX - box.minX + 1, y: box.maxY - box.minY + 1, z: box.maxZ - box.minZ + 1 };
}

export function formatBox(box: Box): string {
  const size = boxSize(box);
  const centre = boxCentre(box);
  return `${centre.x} ${centre.y} ${centre.z} (${size.x}×${size.y}×${size.z})`;
}

export function localPosition(volume: Pick<BlockVolume, "minX" | "minY" | "minZ">, x: number, y: number, z: number): [number, number, number] {
  return [x - volume.minX, y - volume.minY, z - volume.minZ];
}

export function volumeCentre(volume: Pick<BlockVolume, "sizeX" | "sizeY" | "sizeZ">): [number, number, number] {
  return [volume.sizeX / 2, volume.sizeY / 2, volume.sizeZ / 2];
}

export function surfaceHeight(volume: Pick<BlockVolume, "sizeY">): number {
  return volume.sizeY / 2;
}

export function formatWorldPosition(x: number, y: number, z: number): string {
  return `${Math.round(x)} ${Math.round(y)} ${Math.round(z)}`;
}
