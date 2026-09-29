import { OrbitControls } from "@react-three/drei";
import { useFrame, useThree } from "@react-three/fiber";
import { useEffect, useLayoutEffect, useMemo, useRef, useSyncExternalStore } from "react";
import { PlayerObject } from "skinview3d/libs/model.js";
import {
  BoxGeometry,
  Color,
  EdgesGeometry,
  Group,
  InstancedMesh,
  LineBasicMaterial,
  LineSegments,
  Matrix4,
  Mesh,
  MeshLambertMaterial,
  Vector3,
} from "three";
import type { Palette } from "@/api/types";
import { colourOf, isAir, isTransparent, shapeBox, shapeOf } from "@/logic/blocks";
import { skeletonFor, followCameraOffset, eyeHeight, poseAt, type JointPose, type Track } from "@/logic/motion";
import { appliedCount, type Timeline } from "@/logic/timeline";
import { cellOf, dynamicStates, staticVisibleCells, type IndexedVolume } from "@/logic/volume";
import type { ReplayClock } from "./clock";
import { loadSkin, type LoadedSkin } from "./skin";

interface OrbitLike {
  target: Vector3;
  update: () => void;
}

export type CameraMode = { kind: "orbit" } | { kind: "follow"; player: string };

const pixel = 0.9 / 16;

interface StateStyle {
  colour: number;
  transparent: boolean;
  empty: boolean;
  height: number;
  offset: number;
  width: number;
}

function stateStyles(states: string[], palette: Palette): StateStyle[] {
  return states.map((state) => {
    const shape = shapeOf(state);
    const box = shapeBox(shape);
    return { colour: colourOf(state, palette), transparent: isTransparent(state), empty: shape === "empty" || isAir(state), ...box };
  });
}

function placeCell(matrix: Matrix4, indexed: IndexedVolume, cell: number, style: StateStyle) {
  const { dx, dy, dz } = cellOf(indexed.volume, cell);
  matrix.makeScale(style.width, style.height, style.width);
  matrix.setPosition(dx + 0.5, dy + style.offset, dz + 0.5);
}

function StaticVoxels({ indexed, styles }: { indexed: IndexedVolume; styles: StateStyle[] }) {
  const opaqueRef = useRef<InstancedMesh>(null);
  const clearRef = useRef<InstancedMesh>(null);
  const geometry = useMemo(() => new BoxGeometry(1, 1, 1), []);
  const opaqueMaterial = useMemo(() => new MeshLambertMaterial(), []);
  const clearMaterial = useMemo(() => new MeshLambertMaterial({ transparent: true, opacity: 0.45, depthWrite: false }), []);
  const cells = useMemo(() => staticVisibleCells(indexed), [indexed]);
  const split = useMemo(() => {
    const opaque: number[] = [];
    const clear: number[] = [];
    for (const cell of cells) (styles[indexed.cells[cell]].transparent ? clear : opaque).push(cell);
    return { opaque, clear };
  }, [cells, styles, indexed]);

  useLayoutEffect(() => {
    const matrix = new Matrix4();
    const colour = new Color();
    for (const [mesh, list] of [
      [opaqueRef.current, split.opaque],
      [clearRef.current, split.clear],
    ] as const) {
      if (!mesh) continue;
      list.forEach((cell, n) => {
        const style = styles[indexed.cells[cell]];
        placeCell(matrix, indexed, cell, style);
        mesh.setMatrixAt(n, matrix);
        mesh.setColorAt(n, colour.setHex(style.colour));
      });
      mesh.count = list.length;
      mesh.instanceMatrix.needsUpdate = true;
      if (mesh.instanceColor) mesh.instanceColor.needsUpdate = true;
      mesh.computeBoundingSphere();
    }
  }, [split, styles, indexed]);

  return (
    <>
      <instancedMesh key={`o${split.opaque.length}`} ref={opaqueRef} args={[geometry, opaqueMaterial, Math.max(1, split.opaque.length)]} />
      <instancedMesh key={`c${split.clear.length}`} ref={clearRef} args={[geometry, clearMaterial, Math.max(1, split.clear.length)]} renderOrder={2} />
    </>
  );
}

function useApplied(clock: ReplayClock, timeline: Timeline): number {
  return useSyncExternalStore(clock.subscribe, () => appliedCount(timeline, clock.getSnapshot().time));
}

function pulseHighlight(highlight: LineSegments, material: LineBasicMaterial, age: number) {
  material.opacity = age < 0 ? 1 : 0.55 + 0.45 * Math.exp(-age / 1200);
  highlight.scale.setScalar(1 + 0.08 * Math.exp(-Math.max(0, age) / 300));
}

function DynamicVoxels({ indexed, styles, clock, timeline, highlightColour }: { indexed: IndexedVolume; styles: StateStyle[]; clock: ReplayClock; timeline: Timeline; highlightColour: (player: string) => string }) {
  const applied = useApplied(clock, timeline);
  const opaqueRef = useRef<InstancedMesh>(null);
  const clearRef = useRef<InstancedMesh>(null);
  const highlightRef = useRef<LineSegments>(null);
  const geometry = useMemo(() => new BoxGeometry(1, 1, 1), []);
  const edges = useMemo(() => new EdgesGeometry(new BoxGeometry(1.04, 1.04, 1.04)), []);
  const opaqueMaterial = useMemo(() => new MeshLambertMaterial(), []);
  const clearMaterial = useMemo(() => new MeshLambertMaterial({ transparent: true, opacity: 0.45, depthWrite: false }), []);
  const highlightMaterial = useMemo(() => new LineBasicMaterial({ color: "#ffd43b", transparent: true, depthTest: false }), []);
  const count = Math.max(1, indexed.dynamicCells.length);

  useLayoutEffect(() => {
    const states = dynamicStates(indexed, applied);
    const matrix = new Matrix4();
    const hidden = new Matrix4().makeScale(0, 0, 0);
    const colour = new Color();
    const opaque = opaqueRef.current;
    const clear = clearRef.current;
    if (!opaque || !clear) return;
    indexed.dynamicCells.forEach((cell, n) => {
      const style = styles[states[n]];
      const visible = !style.empty;
      if (visible) placeCell(matrix, indexed, cell, style);
      opaque.setMatrixAt(n, visible && !style.transparent ? matrix : hidden);
      clear.setMatrixAt(n, visible && style.transparent ? matrix : hidden);
      colour.setHex(style.colour);
      opaque.setColorAt(n, colour);
      clear.setColorAt(n, colour);
    });
    for (const mesh of [opaque, clear]) {
      mesh.instanceMatrix.needsUpdate = true;
      if (mesh.instanceColor) mesh.instanceColor.needsUpdate = true;
      mesh.computeBoundingSphere();
    }
    const highlight = highlightRef.current;
    if (highlight) {
      const change = applied > 0 ? timeline.changes[applied - 1] : null;
      const cell = applied > 0 ? indexed.changeCells[applied - 1] : -1;
      highlight.visible = !!change && cell >= 0;
      if (change && cell >= 0) {
        const { dx, dy, dz } = cellOf(indexed.volume, cell);
        highlight.position.set(dx + 0.5, dy + 0.5, dz + 0.5);
        highlightMaterial.color.set(change.action === "BREAK" ? "#ff4d4f" : highlightColour(change.player));
      }
    }
  }, [applied, indexed, styles, timeline, highlightColour, highlightMaterial]);

  useFrame(() => {
    const highlight = highlightRef.current;
    if (!highlight?.visible) return;
    const change = timeline.changes[applied - 1];
    pulseHighlight(highlight, highlightMaterial, change ? clock.getSnapshot().time - change.t : 0);
  });

  return (
    <>
      <instancedMesh ref={opaqueRef} args={[geometry, opaqueMaterial, count]} />
      <instancedMesh ref={clearRef} args={[geometry, clearMaterial, count]} renderOrder={2} />
      <lineSegments ref={highlightRef} geometry={edges} material={highlightMaterial} renderOrder={5} visible={false} />
    </>
  );
}

function applyJoint(target: Group, joint: JointPose) {
  target.rotation.set(joint.rx, joint.ry, joint.rz);
  target.position.set(joint.px, joint.py, joint.pz);
}

interface Avatar {
  root: Group;
  player: PlayerObject;
  item: Mesh<BoxGeometry, MeshLambertMaterial>;
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
  const item = new Mesh(new BoxGeometry(4.5, 4.5, 4.5), new MeshLambertMaterial({ color: "#888888" }));
  item.position.set(-1, -11, 2.5);
  item.rotation.set(0.3, 0.6, 0);
  item.visible = false;
  const marker = new Mesh(new BoxGeometry(0.18, 0.18, 0.18), new MeshLambertMaterial({ color: colour, emissive: colour, emissiveIntensity: 0.6 }));
  marker.rotation.set(Math.PI / 4, 0, Math.PI / 4);
  root.add(player);
  root.add(marker);
  player.skin.rightArm.add(item);
  return { root, player, item, marker, held: "" };
}

function disposeAvatar(avatar: Avatar) {
  avatar.item.geometry.dispose();
  avatar.item.material.dispose();
  avatar.marker.geometry.dispose();
  avatar.marker.material.dispose();
}

function dressAvatar(avatar: Avatar, skin: LoadedSkin | null) {
  avatar.player.skin.map = skin ? skin.texture : null;
  if (skin) avatar.player.skin.modelType = skin.slim ? "slim" : "default";
}

function poseAvatar(avatar: Avatar, track: Track, time: number, origin: [number, number, number], palette: Palette) {
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
  const hand = pose.mainHand.toLowerCase();
  const empty = !hand || hand.endsWith("air");
  avatar.item.visible = !empty;
  if (!empty && avatar.held !== hand) {
    avatar.held = hand;
    avatar.item.material.color.setHex(colourOf(hand, palette));
  }
}

function PlayerAvatar({
  track,
  skinUrl,
  clock,
  palette,
  origin,
  colour,
}: {
  track: Track;
  skinUrl: string;
  clock: ReplayClock;
  palette: Palette;
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

  useFrame(() => poseAvatar(avatar, track, clock.getSnapshot().time, origin, palette));

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
  palette: Palette;
  clock: ReplayClock;
  skinUrl: (uuid: string) => string;
  colourOf: (player: string) => string;
  camera: CameraMode;
}

export function ReplayScene({ indexed, timeline, tracks, palette, clock, skinUrl, colourOf: playerColour, camera }: ReplaySceneProps) {
  const styles = useMemo(() => stateStyles(indexed.states, palette), [indexed, palette]);
  const { volume } = indexed;
  const origin: [number, number, number] = useMemo(() => [volume.minX, volume.minY, volume.minZ], [volume.minX, volume.minY, volume.minZ]);
  const ground = useMemo(() => {
    const counts = new Map<number, number>();
    for (let dx = 0; dx < volume.sizeX; dx++) {
      for (let dz = 0; dz < volume.sizeZ; dz++) {
        for (let dy = volume.sizeY - 1; dy >= 0; dy--) {
          const state = indexed.cells[(dy * volume.sizeZ + dz) * volume.sizeX + dx];
          if (!styles[state].empty && styles[state].height === 1) {
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
  }, [indexed, styles, volume]);
  const centre: [number, number, number] = useMemo(() => [volume.sizeX / 2, ground, volume.sizeZ / 2], [volume.sizeX, volume.sizeZ, ground]);
  const gridSize = Math.max(64, Math.ceil(Math.max(volume.sizeX, volume.sizeZ) * 3));

  return (
    <>
      <hemisphereLight args={[0xffffff, 0x556655, 1.1]} />
      <directionalLight position={[-30, 60, -20]} intensity={1.8} />
      <directionalLight position={[40, 30, 50]} intensity={0.5} />
      <gridHelper args={[gridSize, gridSize, new Color("#5c6b63"), new Color("#3a443f")]} position={[centre[0], ground - 0.001, centre[2]]} />
      <StaticVoxels indexed={indexed} styles={styles} />
      <DynamicVoxels indexed={indexed} styles={styles} clock={clock} timeline={timeline} highlightColour={playerColour} />
      {tracks.map((track) => (
        <PlayerAvatar key={track.player} track={track} skinUrl={skinUrl(track.player)} clock={clock} palette={palette} origin={origin} colour={playerColour(track.player)} />
      ))}
      <OrbitControls makeDefault enableDamping target={centre} maxPolarAngle={Math.PI * 0.495} />
      <CameraRig mode={camera} tracks={tracks} clock={clock} origin={origin} centre={centre} />
    </>
  );
}
