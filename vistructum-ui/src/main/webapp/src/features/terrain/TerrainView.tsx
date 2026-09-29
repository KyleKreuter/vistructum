import { OrbitControls } from "@react-three/drei";
import { Canvas } from "@react-three/fiber";
import { useLayoutEffect, useMemo, useRef, useState } from "react";
import { BoxGeometry, Color, InstancedMesh, Matrix4, MeshLambertMaterial } from "three";
import type { Heatmap, Scene } from "@/api/types";
import { Label } from "@/components/ui/label";
import { Slider } from "@/components/ui/slider";
import { Switch } from "@/components/ui/switch";
import { heatAlpha, terrainColumns } from "@/logic/sceneLayers";
import { useTheme } from "@/lib/theme";

interface TerrainViewProps {
  scene: Scene;
  colours: number[];
  heatmap: Heatmap | null;
  tint: boolean;
  onTint: (value: boolean) => void;
  active?: boolean;
}

function Terrain({ scene, colours, heatmap, tint, lift }: { scene: Scene; colours: number[]; heatmap: Heatmap | null; tint: boolean; lift: number }) {
  const meshRef = useRef<InstancedMesh>(null);
  const terrain = useMemo(() => terrainColumns(scene, colours, heatmap), [scene, colours, heatmap]);
  const geometry = useMemo(() => new BoxGeometry(1, 1, 1), []);
  const material = useMemo(() => new MeshLambertMaterial(), []);

  useLayoutEffect(() => {
    const mesh = meshRef.current;
    if (!mesh) return;
    const matrix = new Matrix4();
    const colour = new Color();
    const hot = new Color(1, 0.27, 0.12);
    terrain.columns.forEach((column, n) => {
      const tall = (column.top - terrain.floor) * lift;
      matrix.makeScale(1, tall, 1);
      matrix.setPosition(column.col - scene.width / 2 + 0.5, terrain.floor * lift + tall / 2, column.row - scene.height / 2 + 0.5);
      mesh.setMatrixAt(n, matrix);
      colour.setHex(column.colour);
      if (tint && heatmap) colour.lerp(hot, heatAlpha(column.heat, terrain.peak) * 0.85);
      mesh.setColorAt(n, colour);
    });
    mesh.count = terrain.columns.length;
    mesh.instanceMatrix.needsUpdate = true;
    if (mesh.instanceColor) mesh.instanceColor.needsUpdate = true;
    mesh.computeBoundingSphere();
  }, [terrain, lift, tint, heatmap, scene.width, scene.height]);

  const { top, left, bottom, right } = scene.window;
  const outline = useMemo(() => {
    const x0 = left - scene.width / 2;
    const x1 = right + 1 - scene.width / 2;
    const z0 = top - scene.height / 2;
    const z1 = bottom + 1 - scene.height / 2;
    return new Float32Array([x0, 0, z0, x1, 0, z0, x1, 0, z0, x1, 0, z1, x1, 0, z1, x0, 0, z1, x0, 0, z1, x0, 0, z0]);
  }, [top, left, bottom, right, scene.width, scene.height]);
  const maxTop = terrain.columns.reduce((max, column) => Math.max(max, column.top), 0);

  return (
    <>
      <instancedMesh key={terrain.columns.length} ref={meshRef} args={[geometry, material, Math.max(1, terrain.columns.length)]} />
      <lineSegments position={[0, (maxTop + 1.5) * lift, 0]}>
        <bufferGeometry>
          <bufferAttribute attach="attributes-position" args={[outline, 3]} />
        </bufferGeometry>
        <lineBasicMaterial color="#ffffff" transparent opacity={0.8} />
      </lineSegments>
    </>
  );
}

export default function TerrainView({ scene, colours, heatmap, tint, onTint, active = true }: TerrainViewProps) {
  const [lift, setLift] = useState(1);
  const { resolved } = useTheme();
  const background = resolved === "dark" ? "#141816" : "#262c29";
  const { top, left, bottom, right } = scene.window;
  const cx = (left + right) / 2 - scene.width / 2;
  const cz = (top + bottom) / 2 - scene.height / 2;
  return (
    <div className="space-y-3">
      <div className="relative h-[min(68vh,680px)] min-h-80 overflow-hidden rounded-lg bg-well">
        <Canvas camera={{ position: [cx - 20, 70, cz + 75], fov: 45, near: 0.5, far: 2000 }} dpr={[1, 2]} frameloop={active ? "always" : "never"}>
          <color attach="background" args={[background]} />
          <hemisphereLight args={[0xffffff, 0x445544, 0.9]} />
          <directionalLight position={[-40, 90, -25]} intensity={1.6} />
          <Terrain scene={scene} colours={colours} heatmap={heatmap} tint={tint} lift={lift} />
          <OrbitControls target={[cx, 0, cz]} enableDamping makeDefault />
        </Canvas>
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
