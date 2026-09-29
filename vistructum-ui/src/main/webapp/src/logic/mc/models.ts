import { directionNames, type Direction } from "./direction";
import { booleanOf, isRecord, numberOf, numbersOf, stringOf, stripNamespace, type JsonRecord } from "./json";

export type Vec3 = [number, number, number];

export interface ModelFace {
  uv: [number, number, number, number] | null;
  texture: string;
  cullface: Direction | null;
  rotation: number;
  tintindex: number;
}

export interface ElementRotation {
  origin: Vec3;
  axis: "x" | "y" | "z";
  angle: number;
  rescale: boolean;
}

export interface ModelElement {
  from: Vec3;
  to: Vec3;
  rotation: ElementRotation | null;
  shade: boolean;
  faces: (ModelFace | null)[];
}

export interface DisplayTransform {
  rotation: Vec3;
  translation: Vec3;
  scale: Vec3;
}

export interface ResolvedModel {
  elements: ModelElement[];
  textures: Record<string, string>;
  ambientOcclusion: boolean;
  display: Record<string, DisplayTransform>;
  generated: boolean;
}

export type ModelLookup = (id: string) => unknown;

export const missingTexture = "procedural/missing/all";

const maxDepth = 32;

function vec3(value: unknown, fallback: Vec3): Vec3 {
  const numbers = numbersOf(value, 3);
  return numbers ? [numbers[0], numbers[1], numbers[2]] : fallback;
}

function parseRotation(value: unknown): ElementRotation | null {
  if (!isRecord(value)) return null;
  const axis = stringOf(value.axis);
  if (axis !== "x" && axis !== "y" && axis !== "z") return null;
  const angle = numberOf(value.angle, 0);
  if (angle === 0) return null;
  return { origin: vec3(value.origin, [8, 8, 8]), axis, angle, rescale: booleanOf(value.rescale, false) };
}

function parseFace(value: unknown): ModelFace | null {
  if (!isRecord(value)) return null;
  const texture = stringOf(value.texture);
  if (!texture) return null;
  const uv = numbersOf(value.uv, 4);
  const cullface = stringOf(value.cullface);
  const cullIndex = cullface ? directionNames.indexOf(cullface as (typeof directionNames)[number]) : -1;
  return {
    uv: uv ? [uv[0], uv[1], uv[2], uv[3]] : null,
    texture,
    cullface: cullIndex < 0 ? null : (cullIndex as Direction),
    rotation: (((Math.round(numberOf(value.rotation, 0) / 90) * 90) % 360) + 360) % 360,
    tintindex: numberOf(value.tintindex, -1),
  };
}

export function parseElement(value: unknown): ModelElement | null {
  if (!isRecord(value)) return null;
  const faces = isRecord(value.faces) ? value.faces : {};
  return {
    from: vec3(value.from, [0, 0, 0]),
    to: vec3(value.to, [16, 16, 16]),
    rotation: parseRotation(value.rotation),
    shade: booleanOf(value.shade, true),
    faces: directionNames.map((name) => parseFace(faces[name])),
  };
}

function parseDisplay(value: unknown): DisplayTransform | null {
  if (!isRecord(value)) return null;
  return {
    rotation: vec3(value.rotation, [0, 0, 0]),
    translation: vec3(value.translation, [0, 0, 0]),
    scale: vec3(value.scale, [1, 1, 1]),
  };
}

export function normaliseModelId(id: string): string {
  return stripNamespace(id);
}

export function resolveTexture(reference: string, textures: Record<string, string>): string {
  let current = reference;
  for (let depth = 0; depth < maxDepth; depth++) {
    if (!current.startsWith("#")) return stripNamespace(current);
    const next = textures[current.slice(1)];
    if (next === undefined) return missingTexture;
    current = next;
  }
  return missingTexture;
}

export function resolveModel(id: string, lookup: ModelLookup): ResolvedModel | null {
  const chain: JsonRecord[] = [];
  let generated = false;
  let current: string | null = normaliseModelId(id);
  const seen = new Set<string>();
  while (current && !seen.has(current) && chain.length < maxDepth) {
    seen.add(current);
    if (current === "builtin/generated") {
      generated = true;
      break;
    }
    const json = lookup(current);
    if (!isRecord(json)) break;
    chain.push(json);
    const parent = stringOf(json.parent);
    current = parent ? normaliseModelId(parent) : null;
  }
  if (!chain.length) return null;
  const rawTextures: Record<string, string> = {};
  const display: Record<string, DisplayTransform> = {};
  let elements: ModelElement[] | null = null;
  let ambientOcclusion: boolean | null = null;
  for (let i = chain.length - 1; i >= 0; i--) {
    const json = chain[i];
    if (isRecord(json.textures)) {
      for (const [key, value] of Object.entries(json.textures)) if (typeof value === "string") rawTextures[key] = value;
    }
    if (isRecord(json.display)) {
      for (const [key, value] of Object.entries(json.display)) {
        const transform = parseDisplay(value);
        if (transform) display[key] = transform;
      }
    }
  }
  for (const json of chain) {
    if (elements === null && Array.isArray(json.elements)) {
      elements = json.elements.map(parseElement).filter((element): element is ModelElement => element !== null);
    }
    if (ambientOcclusion === null && typeof json.ambientocclusion === "boolean") ambientOcclusion = json.ambientocclusion;
  }
  const textures: Record<string, string> = {};
  for (const key of Object.keys(rawTextures)) textures[key] = resolveTexture(`#${key}`, rawTextures);
  const resolvedElements = (elements ?? []).map((element) => ({
    ...element,
    faces: element.faces.map((face) => (face ? { ...face, texture: resolveTexture(face.texture, rawTextures) } : null)),
  }));
  return { elements: resolvedElements, textures, ambientOcclusion: ambientOcclusion ?? true, display, generated };
}
