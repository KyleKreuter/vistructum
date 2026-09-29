import { OrbitControls } from "@react-three/drei";
import { useFrame, useThree } from "@react-three/fiber";
import { useEffect, useLayoutEffect, useMemo, useRef, useSyncExternalStore } from "react";
import { PlayerObject } from "skinview3d/libs/model.js";
import { BoxGeometry, EdgesGeometry, Group, LineBasicMaterial, LineSegments, Mesh, MeshLambertMaterial, Vector3 } from "three";
import { isAir, isFullOpaque } from "@/logic/blocks";
import type { Grid } from "@/logic/mc/mesher";
import { skeletonFor, followCameraOffset, eyeHeight, poseAt, type JointPose, type Track } from "@/logic/motion";
import { appliedCount, type Timeline } from "@/logic/timeline";
import { cellOf, dynamicStates, type IndexedVolume } from "@/logic/volume";
import type { WorldAssets } from "@/features/world/assets";
import { EntityLights } from "@/features/world/EntityLights";
import { heldItemObject, loadHeldItem } from "@/features/world/heldItem";
import type { LoadedLibrary } from "@/features/world/library";
import { Sky } from "@/features/world/Sky";
import { fogRange, type SkyColours } from "@/features/world/skyColours";
import { useWorldMesh } from "@/features/world/useWorldMesh";
import type { ReplayClock } from "./clock";
import { loadSkin, type LoadedSkin } from "./skin";

interface OrbitLike {
  target: Vector3;
  update: () => void;
}

export type CameraMode = { kind: "orbit" } | { kind: "follow"; player: string };

const pixel = 0.9 / 16;

function useApplied(clock: ReplayClock, timeline: Timeline): number {
  return useSyncExternalStore(clock.subscribe, () => appliedCount(timeline, clock.getSnapshot().time));
}

function pulseHighlight(highlight: LineSegments, material: LineBasicMaterial, age: number) {
  material.opacity = age < 0 ? 1 : 0.55 + 0.45 * Math.exp(-age / 1200);
  highlight.scale.setScalar(1 + 0.08 * Math.exp(-Math.max(0, age) / 300));
}

function ReplayBlocks({ indexed, loaded, clock, timeline }: { indexed: IndexedVolume; loaded: LoadedLibrary; clock: ReplayClock; timeline: Timeline }) {
  const applied = useApplied(clock, timeline);
  const grid = useMemo<Grid>(() => {
    const { sizeX, sizeY, sizeZ } = indexed.volume;
    return { sizeX, sizeY, sizeZ, cells: Int32Array.from(indexed.cells) };
  }, [indexed]);
  const world = useWorldMesh(grid, loaded);

  useLayoutEffect(() => {
    const states = dynamicStates(indexed, applied);
    const changed: number[] = [];
    indexed.dynamicCells.forEach((cell, n) => {
      if (grid.cells[cell] === states[n]) return;
      grid.cells[cell] = states[n];
      changed.push(cell);
    });
    if (changed.length) world.update(changed);
  }, [applied, indexed, grid, world]);

  return <primitive object={world.group} />;
}

function ChangeHighlight({ indexed, clock, timeline, highlightColour }: { indexed: IndexedVolume; clock: ReplayClock; timeline: Timeline; highlightColour: (player: string) => string }) {
  const applied = useApplied(clock, timeline);
  const highlightRef = useRef<LineSegments>(null);
  const edges = useMemo(() => new EdgesGeometry(new BoxGeometry(1.04, 1.04, 1.04)), []);
  const highlightMaterial = useMemo(() => new LineBasicMaterial({ color: "#ffd43b", transparent: true, depthTest: false, fog: false }), []);

  useEffect(
    () => () => {
      edges.dispose();
      highlightMaterial.dispose();
    },
    [edges, highlightMaterial],
  );

  useLayoutEffect(() => {
    const highlight = highlightRef.current;
    if (!highlight) return;
    const change = applied > 0 ? timeline.changes[applied - 1] : null;
    const cell = applied > 0 ? indexed.changeCells[applied - 1] : -1;
    highlight.visible = !!change && cell >= 0;
    if (change && cell >= 0) {
      const { dx, dy, dz } = cellOf(indexed.volume, cell);
      highlight.position.set(dx + 0.5, dy + 0.5, dz + 0.5);
      highlightMaterial.color.set(change.action === "BREAK" ? "#ff4d4f" : highlightColour(change.player));
    }
  }, [applied, indexed, timeline, highlightColour, highlightMaterial]);

  useFrame(() => {
    const highlight = highlightRef.current;
    if (!highlight?.visible) return;
    const change = timeline.changes[applied - 1];
    pulseHighlight(highlight, highlightMaterial, change ? clock.getSnapshot().time - change.t : 0);
  });

  return <lineSegments ref={highlightRef} geometry={edges} material={highlightMaterial} renderOrder={5} visible={false} />;
}

function applyJoint(target: Group, joint: JointPose) {
  target.rotation.set(joint.rx, joint.ry, joint.rz);
  target.position.set(joint.px, joint.py, joint.pz);
}

interface Avatar {
  root: Group;
  player: PlayerObject;
  hand: Group;
  marker: Mesh<BoxGeometry, MeshLambertMaterial>;
  held: string;
}

function createAvatar(colour: string): Avatar {
  const root = new Group();
  const player = new PlayerObject();
  player.scale.setScalar(pixel);
  player.position.y = 24 * pixel;
  player.cape.visible = false;
  player.elytra.visible = false;
  player.ears.visible = false;
  const hand = new Group();
  const marker = new Mesh(new BoxGeometry(0.18, 0.18, 0.18), new MeshLambertMaterial({ color: colour, emissive: colour, emissiveIntensity: 0.6 }));
  marker.rotation.set(Math.PI / 4, 0, Math.PI / 4);
  root.add(player);
  root.add(marker);
  player.skin.rightArm.add(hand);
  return { root, player, hand, marker, held: "" };
}

function disposeAvatar(avatar: Avatar) {
  avatar.marker.geometry.dispose();
  avatar.marker.material.dispose();
}

function dressAvatar(avatar: Avatar, skin: LoadedSkin | null) {
  avatar.player.skin.map = skin ? skin.texture : null;
  if (skin) avatar.player.skin.modelType = skin.slim ? "slim" : "default";
}

function holdItem(avatar: Avatar, item: string, assets: WorldAssets | null) {
  const held = `${item}@${assets?.key ?? ""}`;
  if (avatar.held === held) return;
  avatar.held = held;
  avatar.hand.clear();
  if (!item || isAir(item) || !assets) return;
  void loadHeldItem(item, assets).then((asset) => {
    if (asset && avatar.held === held) avatar.hand.add(heldItemObject(asset));
  });
}

function poseAvatar(avatar: Avatar, track: Track, time: number, origin: [number, number, number], assets: WorldAssets | null) {
  const pose = poseAt(track, time);
  avatar.root.visible = !!pose;
  if (!pose) return;
  avatar.root.position.set(pose.x - origin[0], pose.y - origin[1], pose.z - origin[2]);
  const skeleton = skeletonFor(pose);
  avatar.player.rotation.y = skeleton.rotationY;
  const skin = avatar.player.skin;
  applyJoint(skin.head, skeleton.head);
  applyJoint(skin.body, skeleton.body);
  applyJoint(skin.rightArm, skeleton.rightArm);
  applyJoint(skin.leftArm, skeleton.leftArm);
  applyJoint(skin.rightLeg, skeleton.rightLeg);
  applyJoint(skin.leftLeg, skeleton.leftLeg);
  avatar.marker.position.y = (pose.sneaking ? 1.65 : 2.05) + 0.05 * Math.sin(time / 300);
  holdItem(avatar, pose.mainHand.toLowerCase(), assets);
}

function PlayerAvatar({
  track,
  skinUrl,
  clock,
  assets,
  origin,
  colour,
}: {
  track: Track;
  skinUrl: string;
  clock: ReplayClock;
  assets: WorldAssets | null;
  origin: [number, number, number];
  colour: string;
}) {
  const avatar = useMemo(() => createAvatar(colour), [colour]);

  useEffect(() => () => disposeAvatar(avatar), [avatar]);

  useEffect(() => {
    let active = true;
    void loadSkin(skinUrl).then((skin) => {
      if (active) dressAvatar(avatar, skin);
    });
    return () => {
      active = false;
    };
  }, [avatar, skinUrl]);

  useFrame(() => poseAvatar(avatar, track, clock.getSnapshot().time, origin, assets));

  return <primitive object={avatar.root} />;
}

function CameraRig({
  mode,
  tracks,
  clock,
  origin,
  centre,
}: {
  mode: CameraMode;
  tracks: Track[];
  clock: ReplayClock;
  origin: [number, number, number];
  centre: [number, number, number];
}) {
  const controls = useThree((state) => state.controls) as unknown as OrbitLike | null;
  const camera = useThree((state) => state.camera);
  const previous = useRef<Vector3 | null>(null);
  const snapped = useRef(false);

  useEffect(() => {
    previous.current = null;
    snapped.current = false;
    if (mode.kind === "orbit" && controls) {
      controls.target.set(centre[0], centre[1], centre[2]);
      controls.update();
    }
  }, [mode, controls, centre]);

  useFrame(() => {
    if (mode.kind !== "follow" || !controls) return;
    const track = tracks.find((entry) => entry.player === mode.player);
    const pose = track ? poseAt(track, clock.getSnapshot().time) : null;
    if (!pose) return;
    const head = new Vector3(pose.x - origin[0], pose.y - origin[1] + eyeHeight(pose) - 0.2, pose.z - origin[2]);
    if (!snapped.current) {
      const [ox, oy, oz] = followCameraOffset(pose, 6, 2.5);
      camera.position.set(head.x + ox, pose.y - origin[1] + oy, head.z + oz);
      controls.target.copy(head);
      snapped.current = true;
    } else if (previous.current) {
      const delta = head.clone().sub(previous.current);
      camera.position.add(delta);
      controls.target.add(delta);
    }
    previous.current = head;
    controls.update();
  });

  return null;
}

export interface ReplaySceneProps {
  indexed: IndexedVolume;
  timeline: Timeline;
  tracks: Track[];
  assets: WorldAssets | null;
  loaded: LoadedLibrary | null;
  clock: ReplayClock;
  skinUrl: (uuid: string) => string;
  colourOf: (player: string) => string;
  camera: CameraMode;
  sky: SkyColours;
}

function groundLevel(indexed: IndexedVolume): number {
  const { volume } = indexed;
  const solid = indexed.states.map((state) => isFullOpaque(state));
  const counts = new Map<number, number>();
  for (let dx = 0; dx < volume.sizeX; dx++) {
    for (let dz = 0; dz < volume.sizeZ; dz++) {
      for (let dy = volume.sizeY - 1; dy >= 0; dy--) {
        if (solid[indexed.cells[(dy * volume.sizeZ + dz) * volume.sizeX + dx]]) {
          counts.set(dy + 1, (counts.get(dy + 1) ?? 0) + 1);
          break;
        }
      }
    }
  }
  let best = 0;
  let bestCount = -1;
  counts.forEach((count, level) => {
    if (count > bestCount) {
      best = level;
      bestCount = count;
    }
  });
  return best;
}

export function ReplayScene({ indexed, timeline, tracks, assets, loaded, clock, skinUrl, colourOf: playerColour, camera, sky }: ReplaySceneProps) {
  const { volume } = indexed;
  const origin: [number, number, number] = useMemo(() => [volume.minX, volume.minY, volume.minZ], [volume.minX, volume.minY, volume.minZ]);
  const ground = useMemo(() => groundLevel(indexed), [indexed]);
  const centre: [number, number, number] = useMemo(() => [volume.sizeX / 2, ground, volume.sizeZ / 2], [volume.sizeX, volume.sizeZ, ground]);
  const [near, far] = fogRange(Math.max(volume.sizeX, volume.sizeY, volume.sizeZ));

  return (
    <>
      <Sky colours={sky} near={near} far={far} />
      <EntityLights />
      {loaded && <ReplayBlocks indexed={indexed} loaded={loaded} clock={clock} timeline={timeline} />}
      <ChangeHighlight indexed={indexed} clock={clock} timeline={timeline} highlightColour={playerColour} />
      {tracks.map((track) => (
        <PlayerAvatar key={track.player} track={track} skinUrl={skinUrl(track.player)} clock={clock} assets={assets} origin={origin} colour={playerColour(track.player)} />
      ))}
      <OrbitControls makeDefault enableDamping target={centre} maxPolarAngle={Math.PI * 0.495} />
      <CameraRig mode={camera} tracks={tracks} clock={clock} origin={origin} centre={centre} />
    </>
  );
}
