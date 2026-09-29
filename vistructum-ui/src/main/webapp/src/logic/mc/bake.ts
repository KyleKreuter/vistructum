import { directionAxis, directionVectors, nearestDirection, type Direction } from "./direction";
import type { ElementRotation, ModelElement, ModelFace, ResolvedModel } from "./models";

export interface RawQuad {
  positions: Float32Array;
  uvs: Float32Array;
  texture: string;
  tintindex: number;
  shade: boolean;
  cullface: Direction | -1;
  normal: Direction;
  normalVector: [number, number, number];
  axisAligned: boolean;
  flush: Direction | -1;
}

export interface Placement {
  x: number;
  y: number;
  uvlock: boolean;
}

const faceCorners: readonly (readonly (readonly [number, number, number])[])[] = [
  [
    [0, 0, 1],
    [0, 0, 0],
    [1, 0, 0],
    [1, 0, 1],
  ],
  [
    [0, 1, 0],
    [0, 1, 1],
    [1, 1, 1],
    [1, 1, 0],
  ],
  [
    [1, 1, 0],
    [1, 0, 0],
    [0, 0, 0],
    [0, 1, 0],
  ],
  [
    [0, 1, 1],
    [0, 0, 1],
    [1, 0, 1],
    [1, 1, 1],
  ],
  [
    [0, 1, 0],
    [0, 0, 0],
    [0, 0, 1],
    [0, 1, 1],
  ],
  [
    [1, 1, 1],
    [1, 0, 1],
    [1, 0, 0],
    [1, 1, 0],
  ],
];

export function defaultUv(direction: Direction, from: readonly number[], to: readonly number[]): [number, number, number, number] {
  switch (direction) {
    case 0:
      return [from[0], 16 - to[2], to[0], 16 - from[2]];
    case 1:
      return [from[0], from[2], to[0], to[2]];
    case 2:
      return [16 - to[0], 16 - to[1], 16 - from[0], 16 - from[1]];
    case 3:
      return [from[0], 16 - to[1], to[0], 16 - from[1]];
    case 4:
      return [from[2], 16 - to[1], to[2], 16 - from[1]];
    default:
      return [16 - to[2], 16 - to[1], 16 - from[2], 16 - from[1]];
  }
}

function exactTrig(degrees: number): [number, number] {
  const normalised = ((degrees % 360) + 360) % 360;
  if (normalised === 0) return [0, 1];
  if (normalised === 90) return [1, 0];
  if (normalised === 180) return [0, -1];
  if (normalised === 270) return [-1, 0];
  const radians = (degrees * Math.PI) / 180;
  return [Math.sin(radians), Math.cos(radians)];
}

function rotateAxis(point: number[], axis: "x" | "y" | "z", degrees: number) {
  const [sin, cos] = exactTrig(degrees);
  const [x, y, z] = point;
  if (axis === "x") {
    point[1] = y * cos - z * sin;
    point[2] = y * sin + z * cos;
  } else if (axis === "y") {
    point[0] = x * cos + z * sin;
    point[2] = -x * sin + z * cos;
  } else {
    point[0] = x * cos - y * sin;
    point[1] = x * sin + y * cos;
  }
}

function applyElementRotation(point: number[], rotation: ElementRotation) {
  const origin = rotation.origin.map((value) => value / 16);
  for (let i = 0; i < 3; i++) point[i] -= origin[i];
  rotateAxis(point, rotation.axis, rotation.angle);
  if (rotation.rescale) {
    const factor = 1 / Math.cos((Math.abs(rotation.angle) * Math.PI) / 180);
    for (let i = 0; i < 3; i++) if ("xyz"[i] !== rotation.axis) point[i] *= factor;
  }
  for (let i = 0; i < 3; i++) point[i] += origin[i];
}

function applyPlacement(point: number[], placement: Placement, centre: number) {
  for (let i = 0; i < 3; i++) point[i] -= centre;
  if (placement.x) rotateAxis(point, "x", -placement.x);
  if (placement.y) rotateAxis(point, "y", -placement.y);
  for (let i = 0; i < 3; i++) point[i] += centre;
}

function rotateDirection(direction: Direction, placement: Placement): Direction {
  const vector = [...directionVectors[direction]];
  applyPlacement(vector, placement, 0);
  return nearestDirection(vector[0], vector[1], vector[2]);
}

function lockedUv(direction: Direction, x: number, y: number, z: number): [number, number] {
  switch (direction) {
    case 0:
      return [x * 16, (1 - z) * 16];
    case 1:
      return [x * 16, z * 16];
    case 2:
      return [(1 - x) * 16, (1 - y) * 16];
    case 3:
      return [x * 16, (1 - y) * 16];
    case 4:
      return [z * 16, (1 - y) * 16];
    default:
      return [(1 - z) * 16, (1 - y) * 16];
  }
}

const epsilon = 1e-4;

function clampUv(value: number): number {
  return Math.min(16, Math.max(0, value));
}

export function bakeFace(element: ModelElement, direction: Direction, face: ModelFace, placement: Placement): RawQuad | null {
  const min = [0, 1, 2].map((i) => Math.min(element.from[i], element.to[i]) / 16);
  const max = [0, 1, 2].map((i) => Math.max(element.from[i], element.to[i]) / 16);
  const uv = face.uv ?? defaultUv(direction, element.from, element.to);
  const positions = new Float32Array(12);
  const uvs = new Float32Array(8);
  const shift = face.rotation / 90;
  const corners = faceCorners[direction];
  for (let i = 0; i < 4; i++) {
    const corner = corners[i];
    const point = [corner[0] ? max[0] : min[0], corner[1] ? max[1] : min[1], corner[2] ? max[2] : min[2]];
    if (element.rotation) applyElementRotation(point, element.rotation);
    applyPlacement(point, placement, 0.5);
    for (let k = 0; k < 3; k++) positions[i * 3 + k] = Math.abs(point[k] - Math.round(point[k] * 16) / 16) < 1e-5 ? Math.round(point[k] * 16) / 16 : point[k];
    const shifted = (i + shift) % 4;
    uvs[i * 2] = uv[shifted === 0 || shifted === 1 ? 0 : 2];
    uvs[i * 2 + 1] = uv[shifted === 0 || shifted === 3 ? 1 : 3];
  }
  const ax = positions[3] - positions[0];
  const ay = positions[4] - positions[1];
  const az = positions[5] - positions[2];
  const bx = positions[6] - positions[0];
  const by = positions[7] - positions[1];
  const bz = positions[8] - positions[2];
  let nx = ay * bz - az * by;
  let ny = az * bx - ax * bz;
  let nz = ax * by - ay * bx;
  const length = Math.hypot(nx, ny, nz);
  if (length < 1e-9) return null;
  nx /= length;
  ny /= length;
  nz /= length;
  const normal = nearestDirection(nx, ny, nz);
  const axis = directionAxis[normal];
  const axisAligned = Math.abs([nx, ny, nz][axis]) > 1 - epsilon;
  const rotated = placement.x !== 0 || placement.y !== 0;
  if (placement.uvlock && rotated && axisAligned) {
    for (let i = 0; i < 4; i++) {
      const [u, v] = lockedUv(normal, positions[i * 3], positions[i * 3 + 1], positions[i * 3 + 2]);
      uvs[i * 2] = u;
      uvs[i * 2 + 1] = v;
    }
  }
  for (let i = 0; i < 8; i++) uvs[i] = clampUv(uvs[i]);
  const boundary = directionVectors[normal][axis] > 0 ? 1 : 0;
  let flush: Direction | -1 = -1;
  if (axisAligned) {
    let onBoundary = true;
    for (let i = 0; i < 4; i++) if (Math.abs(positions[i * 3 + axis] - boundary) > epsilon) onBoundary = false;
    if (onBoundary) flush = normal;
  }
  return {
    positions,
    uvs,
    texture: face.texture,
    tintindex: face.tintindex,
    shade: element.shade,
    cullface: face.cullface === null ? -1 : rotateDirection(face.cullface, placement),
    normal,
    normalVector: [nx, ny, nz],
    axisAligned,
    flush,
  };
}

export function bakeModel(model: ResolvedModel, placement: Placement): RawQuad[] {
  const quads: RawQuad[] = [];
  for (const element of model.elements) {
    element.faces.forEach((face, direction) => {
      const quad = face ? bakeFace(element, direction as Direction, face, placement) : null;
      if (quad) quads.push(quad);
    });
  }
  return quads;
}
