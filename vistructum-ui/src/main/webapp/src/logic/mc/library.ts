import { isAir } from "@/logic/blocks";
import { alphaClass, atlasUv, cutoutAlpha, opaqueAlpha, type AlphaClass, type Atlas, type TextureImage } from "./atlas";
import { bakeModel, type RawQuad } from "./bake";
import { isWaterlogged, parseBlockData, type BlockState } from "./blockData";
import { resolveBlockstate, type ModelPlacement } from "./blockstates";
import { directionAxis, directionNames, directionShade } from "./direction";
import { fallbackModel, fallbackPlacements } from "./fallbackModels";
import { missingTexture, parseElement, resolveModel, type ModelLookup, type ResolvedModel } from "./models";
import { proceduralId } from "./procedural";
import { blockTint, waterColour, type TintColours } from "./tint";

export interface AssetSource {
  blockstate(name: string): unknown;
  model(id: string): unknown;
  item(name: string): unknown;
  hasTexture(id: string): boolean;
}

export const noLiquid = 0;
export const waterLiquid = 1;
export const lavaLiquid = 2;
export type Liquid = 0 | 1 | 2;

export interface RawBlock {
  state: BlockState;
  quads: RawQuad[];
  ambientOcclusion: boolean;
  liquid: Liquid;
  liquidTexture: string | null;
  empty: boolean;
}

export function modelLookup(source: AssetSource | null): ModelLookup {
  return (id) => (id.startsWith("fallback/") ? fallbackModel(id) : source?.model(id));
}

function liquidOf(state: BlockState): Liquid {
  if (state.name === "lava") return lavaLiquid;
  if (state.name === "water" || isWaterlogged(state)) return waterLiquid;
  return noLiquid;
}

function liquidTexture(liquid: Liquid, source: AssetSource | null): string | null {
  if (liquid === noLiquid) return null;
  const id = liquid === waterLiquid ? "block/water_still" : "block/lava_still";
  if (source?.hasTexture(id)) return id;
  return proceduralId(liquid === waterLiquid ? "water" : "lava", "all");
}

function bakePlacements(placements: ModelPlacement[], lookup: ModelLookup): { quads: RawQuad[]; ambientOcclusion: boolean } | null {
  const quads: RawQuad[] = [];
  let ambientOcclusion = true;
  let resolved = 0;
  for (const placement of placements) {
    const model = resolveModel(placement.model, lookup);
    if (!model) continue;
    resolved++;
    ambientOcclusion &&= model.ambientOcclusion;
    quads.push(...bakeModel(model, placement));
  }
  return resolved ? { quads, ambientOcclusion } : null;
}

function checkTextures(quads: RawQuad[], source: AssetSource | null): RawQuad[] {
  if (!source) return quads;
  return quads.map((quad) => (quad.texture.startsWith("procedural/") || source.hasTexture(quad.texture) ? quad : { ...quad, texture: missingTexture }));
}

export function rawBlock(blockData: string, source: AssetSource | null): RawBlock {
  const state = parseBlockData(blockData);
  const liquid = liquidOf(state);
  const empty: RawBlock = { state, quads: [], ambientOcclusion: true, liquid, liquidTexture: liquidTexture(liquid, source), empty: liquid === noLiquid };
  if (isAir(blockData)) return { ...empty, liquid: noLiquid, liquidTexture: null, empty: true };
  if (state.name === "water" || state.name === "lava" || state.name === "bubble_column") return empty;
  const lookup = modelLookup(source);
  const definition = source?.blockstate(state.name);
  const fromAssets = definition === undefined ? null : bakePlacements(resolveBlockstate(definition, state.properties), lookup);
  const baked = fromAssets?.quads.length ? fromAssets : bakePlacements(fallbackPlacements(state), lookup);
  if (!baked) return empty;
  return { ...empty, quads: checkTextures(baked.quads, source), ambientOcclusion: baked.ambientOcclusion, empty: false };
}

export function rawTextures(blocks: RawBlock[]): Set<string> {
  const result = new Set<string>();
  for (const block of blocks) {
    for (const quad of block.quads) result.add(quad.texture);
    if (block.liquidTexture) result.add(block.liquidTexture);
  }
  return result;
}

export interface MeshQuad {
  positions: Float32Array;
  uvs: Float32Array;
  colour: Float32Array;
  cull: number;
  aoDir: number;
  aoFlush: boolean;
  aoCoords: Float32Array;
}

export interface MeshBlock {
  empty: boolean;
  occluder: boolean;
  layer: AlphaClass;
  quads: MeshQuad[];
  cullGroup: number;
  liquid: Liquid;
  liquidLayer: AlphaClass;
  liquidFull: MeshQuad[];
  liquidLow: MeshQuad[];
}

export interface TextureInfo {
  atlas: Pick<Atlas, "width" | "height" | "rects">;
  alpha: Map<string, AlphaClass>;
}

export function textureAlphas(images: Map<string, TextureImage>): Map<string, AlphaClass> {
  const result = new Map<string, AlphaClass>();
  images.forEach((image, id) => result.set(id, alphaClass(image)));
  return result;
}

function srgb(rgb: number): [number, number, number] {
  return [((rgb >> 16) & 255) / 255, ((rgb >> 8) & 255) / 255, (rgb & 255) / 255];
}

export function isFullFace(quad: RawQuad): boolean {
  if (quad.flush < 0) return false;
  const axis = directionAxis[quad.flush];
  const t1 = (axis + 1) % 3;
  const t2 = (axis + 2) % 3;
  let min1 = 1;
  let max1 = 0;
  let min2 = 1;
  let max2 = 0;
  for (let i = 0; i < 4; i++) {
    min1 = Math.min(min1, quad.positions[i * 3 + t1]);
    max1 = Math.max(max1, quad.positions[i * 3 + t1]);
    min2 = Math.min(min2, quad.positions[i * 3 + t2]);
    max2 = Math.max(max2, quad.positions[i * 3 + t2]);
  }
  return min1 <= 1e-4 && min2 <= 1e-4 && max1 >= 1 - 1e-4 && max2 >= 1 - 1e-4;
}

function meshQuad(quad: RawQuad, info: TextureInfo, tint: number, ambientOcclusion: boolean): MeshQuad {
  const rect = info.atlas.rects.get(quad.texture) ?? info.atlas.rects.get(missingTexture);
  const uvs = new Float32Array(8);
  for (let i = 0; i < 4; i++) {
    const [u, v] = rect ? atlasUv(info.atlas, rect, quad.uvs[i * 2], quad.uvs[i * 2 + 1]) : [0, 0];
    uvs[i * 2] = u;
    uvs[i * 2 + 1] = v;
  }
  const shade = quad.shade ? directionShade[quad.normal] : 1;
  const [r, g, b] = quad.tintindex >= 0 ? srgb(tint) : [1, 1, 1];
  const aoDir = ambientOcclusion && quad.axisAligned ? quad.normal : -1;
  const aoCoords = new Float32Array(8);
  if (aoDir >= 0) {
    const axis = directionAxis[aoDir];
    const t1 = (axis + 1) % 3;
    const t2 = (axis + 2) % 3;
    for (let i = 0; i < 4; i++) {
      aoCoords[i * 2] = Math.min(1, Math.max(0, quad.positions[i * 3 + t1]));
      aoCoords[i * 2 + 1] = Math.min(1, Math.max(0, quad.positions[i * 3 + t2]));
    }
  }
  return {
    positions: quad.positions,
    uvs,
    colour: new Float32Array([r * shade, g * shade, b * shade]),
    cull: quad.cullface >= 0 ? quad.cullface : quad.flush,
    aoDir,
    aoFlush: quad.flush >= 0,
    aoCoords,
  };
}

export const liquidHeight = 14 / 16;

export function liquidModel(texture: string, height: number, tinted: boolean): ResolvedModel {
  const faces: Record<string, unknown> = {};
  for (const name of directionNames) faces[name] = { texture, cullface: name === "up" && height < 1 ? undefined : name, tintindex: tinted ? 0 : -1 };
  const element = parseElement({ from: [0, 0, 0], to: [16, height * 16, 16], faces });
  return { elements: element ? [element] : [], textures: {}, ambientOcclusion: false, display: {}, generated: false };
}

function maxAlpha(textures: Iterable<string>, alpha: Map<string, AlphaClass>): AlphaClass {
  let result: AlphaClass = opaqueAlpha;
  for (const texture of textures) {
    const value = alpha.get(texture) ?? opaqueAlpha;
    if (value > result) result = value;
  }
  return result;
}

export function finaliseBlock(raw: RawBlock, info: TextureInfo, tints: TintColours, cullGroups: Map<string, number>): MeshBlock {
  const tint = blockTint(raw.state, tints);
  const quads = raw.quads.map((quad) => meshQuad(quad, info, tint, raw.ambientOcclusion));
  const layer = maxAlpha(
    raw.quads.map((quad) => quad.texture),
    info.alpha,
  );
  const fullFaces = new Set<number>();
  const opaqueFaces = new Set<number>();
  for (const quad of raw.quads) {
    if (!isFullFace(quad)) continue;
    fullFaces.add(quad.flush);
    if ((info.alpha.get(quad.texture) ?? opaqueAlpha) === opaqueAlpha) opaqueFaces.add(quad.flush);
  }
  const occluder = opaqueFaces.size === 6;
  let cullGroup = 0;
  if (!occluder && fullFaces.size === 6 && layer >= cutoutAlpha && !raw.state.name.endsWith("_leaves")) {
    const known = cullGroups.get(raw.state.name);
    cullGroup = known ?? cullGroups.size + 1;
    if (known === undefined) cullGroups.set(raw.state.name, cullGroup);
  }
  const liquidTexture = raw.liquidTexture;
  const tinted = raw.liquid === waterLiquid;
  const liquidQuads = (height: number) =>
    liquidTexture ? bakeModel(liquidModel(liquidTexture, height, tinted), { x: 0, y: 0, uvlock: false }).map((quad) => meshQuad(quad, info, waterColour, false)) : [];
  return {
    empty: raw.empty,
    occluder,
    layer,
    quads,
    cullGroup,
    liquid: raw.liquid,
    liquidLayer: liquidTexture ? maxAlpha([liquidTexture], info.alpha) : opaqueAlpha,
    liquidFull: raw.liquid ? liquidQuads(1) : [],
    liquidLow: raw.liquid ? liquidQuads(liquidHeight) : [],
  };
}

