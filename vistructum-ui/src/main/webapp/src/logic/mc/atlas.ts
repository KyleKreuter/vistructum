import { isRecord, numberOf } from "./json";

export interface TextureImage {
  width: number;
  height: number;
  pixels: Uint8ClampedArray;
}

export interface AtlasRect {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface Atlas {
  width: number;
  height: number;
  pixels: Uint8ClampedArray;
  rects: Map<string, AtlasRect>;
}

export type AlphaClass = 0 | 1 | 2;

export const opaqueAlpha: AlphaClass = 0;
export const cutoutAlpha: AlphaClass = 1;
export const translucentAlpha: AlphaClass = 2;

export const atlasPadding = 2;

export function alphaClass(image: TextureImage): AlphaClass {
  let transparent = 0;
  let partial = 0;
  const total = image.width * image.height;
  for (let i = 3; i < image.pixels.length; i += 4) {
    const alpha = image.pixels[i];
    if (alpha === 0) transparent++;
    else if (alpha < 250) partial++;
  }
  if (partial > total * 0.02) return translucentAlpha;
  if (transparent > 0 || partial > 0) return cutoutAlpha;
  return opaqueAlpha;
}

export function frameImage(image: TextureImage, frame: number): TextureImage {
  const size = image.width;
  const count = Math.max(1, Math.floor(image.height / size));
  if (count === 1) return image;
  const index = ((frame % count) + count) % count;
  const start = index * size * size * 4;
  return { width: size, height: size, pixels: image.pixels.slice(start, start + size * size * 4) };
}

export interface Animation {
  frames: { index: number; time: number }[];
  total: number;
}

export function parseAnimation(meta: unknown, frameCount: number): Animation | null {
  if (!isRecord(meta) || frameCount < 1) return null;
  const frametime = Math.max(1, numberOf(meta.frametime, 1));
  const frames: { index: number; time: number }[] = [];
  if (Array.isArray(meta.frames) && meta.frames.length) {
    for (const entry of meta.frames) {
      if (typeof entry === "number") frames.push({ index: entry, time: frametime });
      else if (isRecord(entry)) frames.push({ index: numberOf(entry.index, 0), time: Math.max(1, numberOf(entry.time, frametime)) });
    }
  } else {
    for (let i = 0; i < frameCount; i++) frames.push({ index: i, time: frametime });
  }
  const valid = frames.filter((frame) => frame.index >= 0 && frame.index < frameCount);
  if (valid.length < 2) return null;
  return { frames: valid, total: valid.reduce((sum, frame) => sum + frame.time, 0) };
}

export function firstFrameIndex(meta: unknown, frameCount: number): number {
  const animation = parseAnimation(meta, frameCount);
  return animation ? animation.frames[0].index : 0;
}

export function animationFrameAt(animation: Animation, tick: number): number {
  let remaining = ((Math.floor(tick) % animation.total) + animation.total) % animation.total;
  for (const frame of animation.frames) {
    if (remaining < frame.time) return frame.index;
    remaining -= frame.time;
  }
  return animation.frames[animation.frames.length - 1].index;
}

function nextPowerOfTwo(value: number): number {
  let result = 16;
  while (result < value) result *= 2;
  return result;
}

export function blitWithGutter(target: Atlas, rect: AtlasRect, image: TextureImage, padding = atlasPadding) {
  const { pixels } = target;
  for (let y = -padding; y < rect.height + padding; y++) {
    const sy = Math.min(rect.height - 1, Math.max(0, y));
    for (let x = -padding; x < rect.width + padding; x++) {
      const sx = Math.min(rect.width - 1, Math.max(0, x));
      const source = (sy * image.width + sx) * 4;
      const destination = ((rect.y + y) * target.width + rect.x + x) * 4;
      pixels[destination] = image.pixels[source];
      pixels[destination + 1] = image.pixels[source + 1];
      pixels[destination + 2] = image.pixels[source + 2];
      pixels[destination + 3] = image.pixels[source + 3];
    }
  }
}

export function packAtlas(entries: { id: string; image: TextureImage }[], padding = atlasPadding): Atlas {
  const sorted = [...entries].sort((a, b) => b.image.height - a.image.height || b.image.width - a.image.width || a.id.localeCompare(b.id));
  const area = sorted.reduce((sum, entry) => sum + (entry.image.width + 2 * padding) * (entry.image.height + 2 * padding), 0);
  const widest = sorted.reduce((max, entry) => Math.max(max, entry.image.width + 2 * padding), 0);
  let width = nextPowerOfTwo(Math.max(widest, Math.ceil(Math.sqrt(area))));
  for (;;) {
    const rects = new Map<string, AtlasRect>();
    let x = 0;
    let y = 0;
    let shelf = 0;
    for (const { id, image } of sorted) {
      const w = image.width + 2 * padding;
      const h = image.height + 2 * padding;
      if (x + w > width) {
        x = 0;
        y += shelf;
        shelf = 0;
      }
      rects.set(id, { x: x + padding, y: y + padding, width: image.width, height: image.height });
      x += w;
      shelf = Math.max(shelf, h);
    }
    const height = nextPowerOfTwo(y + shelf);
    if (height <= width * 2) {
      const atlas: Atlas = { width, height, pixels: new Uint8ClampedArray(width * height * 4), rects };
      for (const { id, image } of sorted) {
        const rect = rects.get(id);
        if (rect) blitWithGutter(atlas, rect, image, padding);
      }
      return atlas;
    }
    width *= 2;
  }
}

export function atlasUv(atlas: Pick<Atlas, "width" | "height">, rect: AtlasRect, u: number, v: number): [number, number] {
  return [(rect.x + (u / 16) * rect.width) / atlas.width, (rect.y + (v / 16) * rect.height) / atlas.height];
}
