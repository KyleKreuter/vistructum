import type { Box } from "@/api/types";

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

