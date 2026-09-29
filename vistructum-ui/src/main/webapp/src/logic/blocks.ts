import type { Palette } from "@/api/types";

export type BlockShape = "empty" | "cube" | "bottomSlab" | "topSlab" | "thin" | "small";

const airMaterials = new Set(["minecraft:air", "minecraft:cave_air", "minecraft:void_air"]);
const transparentPattern = /(^|_)(glass|glass_pane)$|^(water|ice|frosted_ice|bubble_column)$/;
const thinPattern = /(_carpet|_pressure_plate|^minecraft:rail|_rail|^minecraft:redstone_wire|^minecraft:snow|_trapdoor|^minecraft:lily_pad|^minecraft:moss_carpet)$/;
const smallPattern =
  /(^minecraft:(short_grass|grass|tall_grass|fern|large_fern|dead_bush|torch|wall_torch|soul_torch|redstone_torch|lever|dandelion|poppy|cornflower|allium|azure_bluet|oxeye_daisy|lily_of_the_valley|sugar_cane|flower_pot|button))|(_sapling|_tulip|_button|_sign|_wall_sign|_hanging_sign|_banner|_wall_banner|_flower|_mushroom|_torch|_orchid)$/;

export const fallbackColour = 0x9e9e9e;

export function normaliseMaterial(value: string): string {
  const trimmed = value.trim().toLowerCase();
  if (!trimmed) return "minecraft:air";
  return trimmed.includes(":") ? trimmed : `minecraft:${trimmed}`;
}

export function materialOf(blockData: string): string {
  const bracket = blockData.indexOf("[");
  return normaliseMaterial(bracket < 0 ? blockData : blockData.slice(0, bracket));
}

export function blockProperties(blockData: string): Record<string, string> {
  const open = blockData.indexOf("[");
  const close = blockData.lastIndexOf("]");
  if (open < 0 || close <= open) return {};
  const result: Record<string, string> = {};
  for (const pair of blockData.slice(open + 1, close).split(",")) {
    const [key, value] = pair.split("=");
    if (key && value !== undefined) result[key.trim()] = value.trim();
  }
  return result;
}

export function isAir(blockData: string): boolean {
  return airMaterials.has(materialOf(blockData));
}

function bareName(material: string): string {
  const colon = material.indexOf(":");
  return colon < 0 ? material : material.slice(colon + 1);
}

export function isTransparent(blockData: string): boolean {
  return transparentPattern.test(bareName(materialOf(blockData)));
}

export function shapeOf(blockData: string): BlockShape {
  const material = materialOf(blockData);
  if (airMaterials.has(material)) return "empty";
  if (material.endsWith("_slab")) {
    const type = blockProperties(blockData).type;
    if (type === "top") return "topSlab";
    if (type === "double") return "cube";
    return "bottomSlab";
  }
  if (thinPattern.test(material)) return "thin";
  if (smallPattern.test(material)) return "small";
  return "cube";
}

export function isFullOpaque(blockData: string): boolean {
  return shapeOf(blockData) === "cube" && !isTransparent(blockData);
}

export function colourOf(blockData: string, palette: Palette): number {
  const material = materialOf(blockData);
  const known = palette[material];
  if (known !== undefined) return known;
  if (material.includes("water")) return 0x3f76e4;
  if (material.includes("glass")) return 0xc8dce6;
  if (material.includes("ice")) return 0x8eb4fa;
  return fallbackColour;
}

export function shapeBox(shape: BlockShape): { height: number; offset: number; width: number } {
  switch (shape) {
    case "bottomSlab":
      return { height: 0.5, offset: 0.25, width: 1 };
    case "topSlab":
      return { height: 0.5, offset: 0.75, width: 1 };
    case "thin":
      return { height: 0.0625, offset: 0.03125, width: 1 };
    case "small":
      return { height: 0.6, offset: 0.3, width: 0.4 };
    default:
      return { height: 1, offset: 0.5, width: 1 };
  }
}

export function rgbTriple(rgb: number): [number, number, number] {
  return [(rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255];
}

export function rgbHex(rgb: number): string {
  return `#${(rgb & 0xffffff).toString(16).padStart(6, "0")}`;
}

export function displayMaterial(value: string): string {
  return materialOf(value).replace(/^minecraft:/, "").replaceAll("_", " ");
}
