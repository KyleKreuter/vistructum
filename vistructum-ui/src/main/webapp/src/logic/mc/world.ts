import type { Palette } from "@/api/types";
import { colourOf } from "@/logic/blocks";
import { firstFrameIndex, frameImage, packAtlas, parseAnimation, type Animation, type Atlas, type TextureImage } from "./atlas";
import { finaliseBlock, rawBlock, rawTextures, textureAlphas, type AssetSource, type MeshBlock, type RawBlock } from "./library";
import { occluderTable } from "./mesher";
import { missingTexture } from "./models";
import { parseProceduralId, proceduralTexture, textureSize } from "./procedural";
import type { TintColours } from "./tint";

export interface BlockLibrary {
  blocks: MeshBlock[];
  occluders: Uint8Array;
  atlas: Atlas;
  animations: AnimatedTexture[];
}

export interface AnimatedTexture {
  id: string;
  strip: TextureImage;
  animation: Animation;
}

export function planBlocks(states: string[], source: AssetSource | null): { raws: RawBlock[]; textures: string[] } {
  const raws = states.map((state) => rawBlock(state, source));
  const textures = rawTextures(raws);
  textures.add(missingTexture);
  return { raws, textures: [...textures].sort() };
}

export function proceduralImage(id: string, palette: Palette): TextureImage | null {
  const parsed = parseProceduralId(id);
  if (!parsed) return null;
  const rgb = colourOf(`minecraft:${parsed.name}`, palette);
  return { width: textureSize, height: textureSize, pixels: proceduralTexture(parsed.name, parsed.part, rgb) };
}

export function missingImage(): TextureImage {
  return { width: textureSize, height: textureSize, pixels: proceduralTexture("missing", "all", 0) };
}

export function buildLibrary(raws: RawBlock[], strips: Map<string, TextureImage>, meta: Record<string, unknown>, tints: TintColours): BlockLibrary {
  const frames = new Map<string, TextureImage>();
  const animations: AnimatedTexture[] = [];
  strips.forEach((strip, id) => {
    const count = Math.max(1, Math.floor(strip.height / Math.max(1, strip.width)));
    frames.set(id, frameImage(strip, firstFrameIndex(meta[id], count)));
    const animation = count > 1 ? parseAnimation(meta[id] ?? {}, count) : null;
    if (animation) animations.push({ id, strip, animation });
  });
  if (!frames.has(missingTexture)) frames.set(missingTexture, missingImage());
  const atlas = packAtlas([...frames].map(([id, image]) => ({ id, image })));
  const alpha = textureAlphas(frames);
  const cullGroups = new Map<string, number>();
  const blocks = raws.map((raw) => finaliseBlock(raw, { atlas, alpha }, tints, cullGroups));
  return { blocks, occluders: occluderTable(blocks), atlas, animations };
}
