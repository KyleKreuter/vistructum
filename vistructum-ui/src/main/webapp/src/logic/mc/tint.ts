import type { BlockState } from "./blockData";

export interface Climate {
  temperature: number;
  downfall: number;
}

export const plainsClimate: Climate = { temperature: 0.8, downfall: 0.4 };

export const plainsGrass = 0x91bd59;
export const plainsFoliage = 0x77ab2f;
export const waterColour = 0x3f76e4;
export const spruceFoliage = 0x619961;
export const birchFoliage = 0x80a755;
export const lilyPadColour = 0x208030;
export const attachedStemColour = 0xe0c71c;

export interface Colormap {
  width: number;
  height: number;
  pixels: Uint8ClampedArray;
}

export interface TintColours {
  grass: number;
  foliage: number;
}

export function colormapCoordinates(climate: Climate): { x: number; y: number } {
  const temperature = Math.min(1, Math.max(0, climate.temperature));
  const downfall = Math.min(1, Math.max(0, climate.downfall)) * temperature;
  return { x: Math.floor((1 - temperature) * 255), y: Math.floor((1 - downfall) * 255) };
}

export function sampleColormap(map: Colormap | null, climate: Climate, fallback: number): number {
  if (!map || map.width < 256 || map.height < 256) return fallback;
  const { x, y } = colormapCoordinates(climate);
  const index = (y * map.width + x) * 4;
  if (map.pixels[index + 3] === 0) return fallback;
  return (map.pixels[index] << 16) | (map.pixels[index + 1] << 8) | map.pixels[index + 2];
}

export function redstoneColour(power: number): number {
  const level = Math.min(15, Math.max(0, power)) / 15;
  const r = level * 0.6 + (level > 0 ? 0.4 : 0.3);
  const g = Math.min(1, Math.max(0, level * level * 0.7 - 0.5));
  const b = Math.min(1, Math.max(0, level * level * 0.6 - 0.7));
  return (Math.floor(r * 255) << 16) | (Math.floor(g * 255) << 8) | Math.floor(b * 255);
}

export function stemColour(age: number): number {
  const clamped = Math.min(7, Math.max(0, age));
  return ((clamped * 32) << 16) | ((255 - clamped * 8) << 8) | (clamped * 4);
}

const grassTinted = /^(grass_block|short_grass|grass|tall_grass|fern|large_fern|potted_fern|sugar_cane|pink_petals)$/;
const foliageTinted = /^(oak_leaves|jungle_leaves|acacia_leaves|dark_oak_leaves|mangrove_leaves|vine)$/;
const waterTinted = /^(water|bubble_column|water_cauldron)$/;

export function blockTint(state: BlockState, colours: TintColours): number {
  const { name, properties } = state;
  if (grassTinted.test(name)) return colours.grass;
  if (foliageTinted.test(name)) return colours.foliage;
  if (name === "spruce_leaves") return spruceFoliage;
  if (name === "birch_leaves") return birchFoliage;
  if (waterTinted.test(name)) return waterColour;
  if (name === "lily_pad") return lilyPadColour;
  if (name === "redstone_wire") return redstoneColour(Number(properties.power ?? 0));
  if (name === "melon_stem" || name === "pumpkin_stem") return stemColour(Number(properties.age ?? 0));
  if (name === "attached_melon_stem" || name === "attached_pumpkin_stem") return attachedStemColour;
  return 0xffffff;
}
