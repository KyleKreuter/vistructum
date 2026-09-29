import { useFrame } from "@react-three/fiber";
import { useEffect, useMemo, useRef } from "react";
import type { Grid } from "@/logic/mc/mesher";
import type { LoadedLibrary } from "./library";
import { animateAtlas, createBlockMaterials, createWorldMesh, disposeMaterials, type WorldMesh } from "./worldMesh";

export function useWorldMesh(grid: Grid, loaded: LoadedLibrary): WorldMesh {
  const materials = useMemo(() => createBlockMaterials(loaded.texture), [loaded]);
  const world = useMemo(() => createWorldMesh(grid, loaded, materials), [grid, loaded, materials]);
  useEffect(() => () => world.dispose(), [world]);
  useEffect(() => () => disposeMaterials(materials), [materials]);
  const shown = useRef(new Map<string, number>());
  useFrame(({ camera, clock }) => {
    world.sort(camera);
    animateAtlas(loaded, clock.elapsedTime * 20, shown.current);
  });
  return world;
}
