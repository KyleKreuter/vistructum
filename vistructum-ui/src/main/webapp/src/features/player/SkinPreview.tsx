import { OrbitControls } from "@react-three/drei";
import { Canvas, useFrame } from "@react-three/fiber";
import { useEffect, useMemo, useState } from "react";
import { Box3 } from "three";
import { IdleAnimation, WalkingAnimation, type PlayerAnimation } from "skinview3d/libs/animation.js";
import { PlayerObject } from "skinview3d/libs/model.js";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { useTheme } from "@/lib/theme";
import { EntityLights } from "@/features/world/EntityLights";
import { Sky } from "@/features/world/Sky";
import { skyTheme } from "@/features/world/skyColours";
import { loadSkin, type LoadedSkin } from "@/features/replay/skin";

function createPlayer(): PlayerObject {
  const player = new PlayerObject();
  player.cape.visible = false;
  player.elytra.visible = false;
  player.ears.visible = false;
  const bounds = new Box3().setFromObject(player);
  player.position.y = -(bounds.min.y + bounds.max.y) / 2;
  return player;
}

function dress(player: PlayerObject, skin: LoadedSkin) {
  player.skin.map = skin.texture;
  player.skin.modelType = skin.slim ? "slim" : "default";
}

function play(animation: PlayerAnimation, player: PlayerObject, delta: number) {
  animation.update(player, delta);
}

function Player({ url, walking, onMissing }: { url: string; walking: boolean; onMissing: () => void }) {
  const player = useMemo(() => createPlayer(), []);
  const animation = useMemo<PlayerAnimation>(() => (walking ? new WalkingAnimation() : new IdleAnimation()), [walking]);

  useEffect(() => {
    let active = true;
    void loadSkin(url).then((skin) => {
      if (!active) return;
      if (skin) dress(player, skin);
      else onMissing();
    });
    return () => {
      active = false;
    };
  }, [player, url, onMissing]);

  useFrame((_, delta) => play(animation, player, delta));

  return <primitive object={player} />;
}

export default function SkinPreview({ url, onMissing }: { url: string; onMissing: () => void }) {
  const [walking, setWalking] = useState(false);
  const { resolved } = useTheme();
  return (
    <div className="space-y-2">
      <div className="h-72 overflow-hidden rounded-lg bg-well">
        <Canvas flat camera={{ position: [26, 6, 58], fov: 45, near: 1, far: 400 }} dpr={[1, 2]} aria-label="3D skin preview">
          <Sky colours={skyTheme(resolved === "dark")} near={300} far={600} />
          <EntityLights />
          <Player url={url} walking={walking} onMissing={onMissing} />
          <OrbitControls target={[0, 0, 0]} enablePan={false} minDistance={30} maxDistance={90} autoRotate autoRotateSpeed={1.2} makeDefault />
        </Canvas>
      </div>
      <div className="flex items-center gap-2 text-sm">
        <Switch id="walking" checked={walking} onCheckedChange={setWalking} />
        <Label htmlFor="walking">Walking</Label>
      </div>
    </div>
  );
}
