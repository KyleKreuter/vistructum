import { OrbitControls } from "@react-three/drei";
import { Canvas, useFrame } from "@react-three/fiber";
import { useEffect, useMemo } from "react";
import { Box3 } from "three";
import { IdleAnimation, type PlayerAnimation } from "skinview3d/libs/animation.js";
import { PlayerObject } from "skinview3d/libs/model.js";
import { useTheme } from "@/lib/theme";
import { EntityLights } from "@/features/world/EntityLights";
import { Sky } from "@/features/world/Sky";
import { skyTheme } from "@/features/world/skyColours";
import { disposeModel, loadSkin, type LoadedSkin } from "@/features/replay/skin";

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

function Player({ url, onMissing }: { url: string; onMissing: () => void }) {
  const player = useMemo(() => createPlayer(), []);
  const animation = useMemo<PlayerAnimation>(() => new IdleAnimation(), []);

  useEffect(() => () => disposeModel(player), [player]);

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
  const { resolved } = useTheme();
  return (
    <div className="h-72 overflow-hidden rounded-lg bg-well">
      <Canvas flat camera={{ position: [26, 6, 58], fov: 45, near: 1, far: 400 }} dpr={[1, 2]} aria-label="3D skin preview">
        <Sky colours={skyTheme(resolved === "dark")} near={300} far={600} />
        <EntityLights />
        <Player url={url} onMissing={onMissing} />
        <OrbitControls target={[0, 0, 0]} enablePan={false} minDistance={30} maxDistance={90} autoRotate autoRotateSpeed={1.2} makeDefault />
      </Canvas>
    </div>
  );
}
