import { OrbitControls } from "@react-three/drei";
import { Canvas } from "@react-three/fiber";
import { useMemo, useState } from "react";
import type { Heatmap, Palette, Scene } from "@/api/types";
import { Label } from "@/components/ui/label";
import { Slider } from "@/components/ui/slider";
import { Switch } from "@/components/ui/switch";
import { useWorldAssets } from "@/features/world/assets";
import { EntityLights } from "@/features/world/EntityLights";
import { useBlockLibrary, type LoadedLibrary } from "@/features/world/library";
import { Sky } from "@/features/world/Sky";
import { fogRange, skyTheme } from "@/features/world/skyColours";
import { useWorldMesh } from "@/features/world/useWorldMesh";
import type { RawBlock } from "@/logic/mc/library";
import { columnBlock, terrainGrid, heatSurface, type HeatSurface, type TerrainGrid } from "@/logic/mc/terrain";
import { useTheme } from "@/lib/theme";

interface TerrainViewProps {
  scene: Scene;
  colours: number[];
  heatmap: Heatmap | null;
  tint: boolean;
  onTint: (value: boolean) => void;
  active?: boolean;
}

const groundNames = ["grass_block", "dirt", "stone"];

function prepareColumns(raws: RawBlock[]): RawBlock[] {
  const ground = groundNames.map((name) => raws.find((raw) => raw.state.name === name)).find((raw) => !!raw) ?? null;
  return raws.map((raw) => columnBlock(raw, ground));
}

function scenePalette(scene: Scene, colours: number[]): Palette {
  const palette: Palette = {};
  scene.palette.forEach((name, index) => {
    palette[name] = colours[index] ?? 0x808080;
  });
  return palette;
}

function typicalHeight(scene: Scene): number {
  const heights = scene.heights.filter((_, index) => scene.blocks[index] >= 0).sort((a, b) => a - b);
  return heights.length ? heights[Math.floor(heights.length / 2)] + 1 : 0;
}

function TerrainBlocks({ terrain, loaded }: { terrain: TerrainGrid; loaded: LoadedLibrary }) {
  const world = useWorldMesh(terrain.grid, loaded);
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

function Outline({ scene, height }: { scene: Scene; height: number }) {
  const { top, left, bottom, right } = scene.window;
  const outline = useMemo(() => {
    const x0 = left - scene.width / 2;
    const x1 = right + 1 - scene.width / 2;
    const z0 = top - scene.height / 2;
    const z1 = bottom + 1 - scene.height / 2;
    return new Float32Array([x0, 0, z0, x1, 0, z0, x1, 0, z0, x1, 0, z1, x1, 0, z1, x0, 0, z1, x0, 0, z1, x0, 0, z0]);
  }, [top, left, bottom, right, scene.width, scene.height]);
  return (
    <lineSegments position={[0, height, 0]}>
      <bufferGeometry>
        <bufferAttribute attach="attributes-position" args={[outline, 3]} />
      </bufferGeometry>
      <lineBasicMaterial color="#ffffff" transparent opacity={0.9} fog={false} />
    </lineSegments>
  );
}

export default function TerrainView({ scene, colours, heatmap, tint, onTint, active = true }: TerrainViewProps) {
  const [lift, setLift] = useState(1);
  const { resolved } = useTheme();
  const terrain = useMemo(() => terrainGrid(scene), [scene]);
  const palette = useMemo(() => scenePalette(scene, colours), [scene, colours]);
  const assets = useWorldAssets(palette);
  const loaded = useBlockLibrary(assets, terrain.states, prepareColumns);
  const surface = useMemo(() => (tint ? heatSurface(terrain, heatmap) : null), [tint, terrain, heatmap]);
  const top = terrain.floor + terrain.grid.sizeY;
  const extent = Math.max(scene.width, scene.height);
  const [near, far] = fogRange(extent);
  const distance = Math.max(20, extent * 1.3);
  const level = useMemo(() => typicalHeight(scene), [scene]);
  const frame = scene.window;
  const cx = (frame.left + frame.right) / 2 - scene.width / 2;
  const cz = (frame.top + frame.bottom) / 2 - scene.height / 2;
  return (
    <div className="space-y-3">
      <div className="relative h-[min(68vh,680px)] min-h-80 overflow-hidden rounded-lg bg-well">
        <Canvas flat camera={{ position: [cx - distance * 0.3, level + distance * 0.7, cz + distance], fov: 45, near: 0.5, far: 4000 }} dpr={[1, 2]} frameloop={active ? "always" : "never"}>
          <Sky colours={skyTheme(resolved === "dark")} near={near} far={far} />
          <EntityLights />
          <group position={[-scene.width / 2, terrain.floor * lift, -scene.height / 2]} scale={[1, lift, 1]}>
            {loaded && <TerrainBlocks terrain={terrain} loaded={loaded} />}
            {surface && <HeatLayer surface={surface} />}
          </group>
          <Outline scene={scene} height={(top + 1.5) * lift} />
          <OrbitControls target={[cx, level * lift, cz]} enableDamping makeDefault />
        </Canvas>
        {!loaded && <div className="pointer-events-none absolute inset-x-0 top-3 text-center text-xs text-white/80">Loading block textures…</div>}
        <div className="pointer-events-none absolute bottom-2 left-3 text-xs text-well-foreground/80">Drag to rotate · right-drag to pan · scroll to zoom</div>
      </div>
      <div className="flex flex-wrap items-center gap-x-6 gap-y-3 text-sm">
        <div className="flex items-center gap-2">
          <Switch id="tint" checked={tint} onCheckedChange={onTint} disabled={!heatmap} />
          <Label htmlFor="tint">
            Heatmap tint <kbd>H</kbd>
          </Label>
        </div>
        <div className="flex min-w-60 items-center gap-3">
          <Label className="shrink-0">Exaggeration {lift}×</Label>
          <Slider min={1} max={4} step={0.5} value={[lift]} onValueChange={([value]) => setLift(value)} />
        </div>
      </div>
    </div>
  );
}
