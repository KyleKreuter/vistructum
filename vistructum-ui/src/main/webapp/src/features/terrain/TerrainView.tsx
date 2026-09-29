import { OrbitControls } from "@react-three/drei";
import { Canvas } from "@react-three/fiber";
import { useMemo, type Ref } from "react";
import type { Box, Heatmap, Palette, Terrain } from "@/api/types";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { useWorldAssets } from "@/features/world/assets";
import { EntityLights } from "@/features/world/EntityLights";
import { useBlockLibrary, type LoadedLibrary } from "@/features/world/library";
import { Sky } from "@/features/world/Sky";
import { fogRange, skyTheme } from "@/features/world/skyColours";
import { useWorldMesh } from "@/features/world/useWorldMesh";
import type { Grid } from "@/logic/mc/mesher";
import { boxOutline, columnTops, heatSurface, terrainGrid, typicalTop, type HeatSurface, type Outline } from "@/logic/mc/terrain";
import { heatmapNote, type HeatmapState } from "@/logic/sceneLayers";
import { useTheme } from "@/lib/theme";

interface TerrainViewProps {
  terrain: Terrain;
  box: Box;
  palette: Palette;
  heatmap: Heatmap | null;
  heatmapState: HeatmapState;
  overlay: boolean;
  onOverlay: (value: boolean) => void;
  active?: boolean;
  stageRef?: Ref<HTMLDivElement>;
}

function TerrainBlocks({ grid, loaded }: { grid: Grid; loaded: LoadedLibrary }) {
  const world = useWorldMesh(grid, loaded);
  return <primitive object={world.group} />;
}

function HeatLayer({ surface }: { surface: HeatSurface }) {
  return (
    <mesh renderOrder={2}>
      <bufferGeometry>
        <bufferAttribute attach="attributes-position" args={[surface.positions, 3]} />
        <bufferAttribute attach="attributes-color" args={[surface.colours, 4]} />
        <bufferAttribute attach="index" args={[surface.indices, 1]} />
      </bufferGeometry>
      <meshBasicMaterial vertexColors transparent depthWrite={false} polygonOffset polygonOffsetFactor={-1} />
    </mesh>
  );
}

function BoxOutline({ outline, height }: { outline: Outline; height: number }) {
  const points = useMemo(() => {
    const { x0, x1, z0, z1 } = outline;
    return new Float32Array([x0, 0, z0, x1, 0, z0, x1, 0, z0, x1, 0, z1, x1, 0, z1, x0, 0, z1, x0, 0, z1, x0, 0, z0]);
  }, [outline]);
  return (
    <lineSegments position={[0, height, 0]}>
      <bufferGeometry>
        <bufferAttribute attach="attributes-position" args={[points, 3]} />
      </bufferGeometry>
      <lineBasicMaterial color="#ffffff" transparent opacity={0.9} fog={false} />
    </lineSegments>
  );
}

export default function TerrainView({ terrain, box, palette, heatmap, heatmapState, overlay, onOverlay, active = true, stageRef }: TerrainViewProps) {
  const { resolved } = useTheme();
  const { sizeX, sizeY, sizeZ } = terrain.blocks;
  const grid = useMemo(() => terrainGrid(terrain), [terrain]);
  const tops = useMemo(() => columnTops(terrain), [terrain]);
  const assets = useWorldAssets(palette);
  const loaded = useBlockLibrary(assets, terrain.blocks.palette);
  const aligned = terrain.sceneOrigin !== null;
  const heatNote = aligned ? heatmapNote(heatmapState) : null;
  const surface = useMemo(() => (overlay ? heatSurface(terrain, tops, heatmap) : null), [overlay, terrain, tops, heatmap]);
  const outline = useMemo(() => boxOutline(terrain, box), [terrain, box]);
  const level = useMemo(() => typicalTop(tops), [tops]);
  const extent = Math.max(sizeX, sizeZ);
  const [near, far] = fogRange(extent);
  const distance = Math.max(20, extent * 1.1);
  const cx = (outline.x0 + outline.x1) / 2 - sizeX / 2;
  const cz = (outline.z0 + outline.z1) / 2 - sizeZ / 2;
  return (
    <div className="space-y-3">
      <div ref={stageRef} className="relative h-[min(68vh,680px)] min-h-80 overflow-hidden rounded-lg bg-well">
        <Canvas flat camera={{ position: [cx, level + distance * 0.9, cz + distance * 0.55], fov: 45, near: 0.5, far: 4000 }} dpr={[1, 2]} frameloop={active ? "always" : "never"}>
          <Sky colours={skyTheme(resolved === "dark")} near={near} far={far} />
          <EntityLights />
          <group position={[-sizeX / 2, 0, -sizeZ / 2]}>
            {loaded && <TerrainBlocks grid={grid} loaded={loaded} />}
            {surface && <HeatLayer surface={surface} />}
            <BoxOutline outline={outline} height={sizeY + 0.5} />
          </group>
          <OrbitControls target={[cx, level, cz]} enableDamping makeDefault />
        </Canvas>
        {!loaded && <div className="pointer-events-none absolute inset-x-0 top-3 text-center text-xs text-white/80">Loading block textures…</div>}
        <div className="pointer-events-none absolute bottom-2 left-3 text-xs text-well-foreground/80">Drag to rotate · right-drag to pan · scroll to zoom</div>
      </div>
      {aligned && (
        <div className="flex flex-wrap items-center gap-x-6 gap-y-3 text-sm">
          <div className="flex items-center gap-2">
            <Switch id="terrain-overlay" checked={overlay} onCheckedChange={onOverlay} />
            <Label htmlFor="terrain-overlay">Heatmap overlay</Label>
          </div>
          {heatNote && <span className="text-muted-foreground">{heatNote}</span>}
        </div>
      )}
    </div>
  );
}
