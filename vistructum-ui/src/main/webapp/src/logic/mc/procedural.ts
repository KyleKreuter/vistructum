import { hashString, seededRandom } from "./random";

export type MaterialClass =
  | "stone"
  | "dirt"
  | "grass"
  | "sand"
  | "planks"
  | "log"
  | "leaves"
  | "glass"
  | "wool"
  | "concrete"
  | "ore"
  | "water"
  | "lava"
  | "metal"
  | "plant"
  | "torch"
  | "generic"
  | "missing";

export type TexturePart = "all" | "top" | "side" | "bottom" | "end" | "item";

export const textureSize = 16;

const woodTypes = "oak|spruce|birch|jungle|acacia|dark_oak|mangrove|cherry|pale_oak|bamboo|crimson|warped";
const plantPattern =
  /^(short_grass|grass|tall_grass|fern|large_fern|dead_bush|seagrass|tall_seagrass|kelp|kelp_plant|sugar_cane|wheat|carrots|potatoes|beetroots|sweet_berry_bush|nether_sprouts|crimson_roots|warped_roots|hanging_roots|dandelion|poppy|blue_orchid|allium|azure_bluet|oxeye_daisy|cornflower|lily_of_the_valley|wither_rose|torchflower|sunflower|lilac|rose_bush|peony|pitcher_plant|pitcher_crop|brown_mushroom|red_mushroom|crimson_fungus|warped_fungus|bamboo_sapling|cave_vines|cave_vines_plant|twisting_vines|weeping_vines|glow_lichen|vine|cobweb|pink_petals|spore_blossom|small_dripleaf|big_dripleaf_stem|lily_pad)$|(_sapling|_tulip|_propagule)$/;
const oreColours: [RegExp, number][] = [
  [/coal/, 0x2b2b2b],
  [/iron/, 0xd8af93],
  [/copper/, 0xe0734d],
  [/gold/, 0xfcee4b],
  [/redstone/, 0xff1a1a],
  [/lapis/, 0x2150b8],
  [/diamond/, 0x5decf5],
  [/emerald/, 0x17dd62],
  [/quartz/, 0xeae5de],
];
const dyeColours: [RegExp, number][] = [
  [/light_blue/, 0x3ab3da],
  [/light_gray/, 0x9d9d97],
  [/white|oxeye|azure_bluet|lily_of_the_valley/, 0xf2f2f2],
  [/orange/, 0xf9801d],
  [/magenta|allium/, 0xc74ebd],
  [/yellow|dandelion|sunflower/, 0xfed83d],
  [/lime/, 0x80c71f],
  [/pink|peony|lilac|pink_petals/, 0xf38baa],
  [/gray/, 0x474f52],
  [/cyan/, 0x169c9c],
  [/purple/, 0x8932b8],
  [/blue|cornflower|orchid/, 0x3c44aa],
  [/brown/, 0x835432],
  [/green/, 0x5e7c16],
  [/red|poppy|rose/, 0xb02e26],
  [/black|wither/, 0x1d1d21],
];

export function dyeColour(name: string): number | null {
  for (const [pattern, colour] of dyeColours) if (pattern.test(name)) return colour;
  return null;
}

export function materialClass(name: string): MaterialClass {
  if (name === "missing") return "missing";
  if (/^(water|bubble_column)$/.test(name)) return "water";
  if (name === "lava") return "lava";
  if (/torch$/.test(name) && name !== "torchflower") return "torch";
  if (/_leaves$/.test(name)) return "leaves";
  if (plantPattern.test(name)) return "plant";
  if (/(^|_)glass($|_pane$)|^(ice|packed_ice|blue_ice|frosted_ice)$/.test(name)) return "glass";
  if (/_ore$|^ancient_debris$/.test(name)) return "ore";
  if (/^(grass_block|mycelium|podzol)$/.test(name)) return "grass";
  if (/(_log|_wood|^crimson_stem|^warped_stem|^mushroom_stem|_hyphae)$|^bamboo_block$/.test(name)) return "log";
  if (new RegExp(`^(${woodTypes})_`).test(name) || /^(crafting_table|bookshelf|chest|barrel|composter|note_block|jukebox|ladder)$/.test(name)) return "planks";
  if (/(wool|carpet)$/.test(name)) return "wool";
  if (/(concrete|concrete_powder|terracotta)$/.test(name)) return "concrete";
  if (/(^|_)(sand|sandstone|gravel)(_|$)|^soul_sand$/.test(name)) return "sand";
  if (/^(dirt|coarse_dirt|rooted_dirt|mud|farmland|dirt_path|clay|soul_soil|packed_mud)$/.test(name)) return "dirt";
  if (/^(iron|gold|diamond|emerald|netherite|lapis|redstone|copper|exposed_copper|weathered_copper|oxidized_copper|raw_iron|raw_gold|raw_copper)_block$|^(iron_bars|anvil|lantern|soul_lantern|cauldron|hopper|chain|lightning_rod)$|copper/.test(name))
    return "metal";
  if (/(stone|cobble|deepslate|andesite|diorite|granite|tuff|brick|blackstone|basalt|netherrack|obsidian|prismarine|calcite|dripstone|bedrock|quartz|purpur|end_stone)/.test(name)) return "stone";
  return "generic";
}

type Pixels = Uint8ClampedArray;

function put(pixels: Pixels, x: number, y: number, rgb: number, factor: number, alpha = 255) {
  if (x < 0 || y < 0 || x >= textureSize || y >= textureSize) return;
  const i = (y * textureSize + x) * 4;
  pixels[i] = ((rgb >> 16) & 255) * factor;
  pixels[i + 1] = ((rgb >> 8) & 255) * factor;
  pixels[i + 2] = (rgb & 255) * factor;
  pixels[i + 3] = alpha;
}

function noiseFill(pixels: Pixels, rnd: () => number, rgb: number, spread: number, alpha = 255) {
  for (let y = 0; y < textureSize; y++) for (let x = 0; x < textureSize; x++) put(pixels, x, y, rgb, 1 + (rnd() - 0.5) * 2 * spread, alpha);
}

function specks(pixels: Pixels, rnd: () => number, rgb: number, chance: number, factor: number) {
  for (let y = 0; y < textureSize; y++) for (let x = 0; x < textureSize; x++) if (rnd() < chance) put(pixels, x, y, rgb, factor * (0.95 + rnd() * 0.1));
}

function blobs(pixels: Pixels, rnd: () => number, rgb: number, count: number, factor: number) {
  for (let n = 0; n < count; n++) {
    const cx = Math.floor(rnd() * textureSize);
    const cy = Math.floor(rnd() * textureSize);
    const size = 1 + Math.floor(rnd() * 3);
    for (let k = 0; k < size + 1; k++) put(pixels, (cx + Math.floor(rnd() * size)) % textureSize, (cy + Math.floor(rnd() * size)) % textureSize, rgb, factor);
  }
}

const dirtColour = 0x866043;
const barkFactor = 0.62;

function stone(pixels: Pixels, rnd: () => number, rgb: number) {
  noiseFill(pixels, rnd, rgb, 0.12);
  blobs(pixels, rnd, rgb, 7, 0.78);
  blobs(pixels, rnd, rgb, 4, 1.15);
}

function dirt(pixels: Pixels, rnd: () => number, rgb: number) {
  noiseFill(pixels, rnd, rgb, 0.16);
  specks(pixels, rnd, rgb, 0.12, 0.7);
  specks(pixels, rnd, rgb, 0.06, 1.22);
}

function grass(pixels: Pixels, rnd: () => number, rgb: number, part: TexturePart) {
  if (part === "bottom") {
    dirt(pixels, rnd, dirtColour);
    return;
  }
  if (part === "top") {
    noiseFill(pixels, rnd, rgb, 0.18);
    specks(pixels, rnd, rgb, 0.12, 0.78);
    return;
  }
  dirt(pixels, rnd, dirtColour);
  for (let x = 0; x < textureSize; x++) {
    const depth = 3 + (rnd() < 0.55 ? 1 : 0) + (rnd() < 0.25 ? 1 : 0);
    for (let y = 0; y < depth; y++) put(pixels, x, y, rgb, 0.9 + rnd() * 0.2);
  }
}

function sand(pixels: Pixels, rnd: () => number, rgb: number) {
  noiseFill(pixels, rnd, rgb, 0.07);
  specks(pixels, rnd, rgb, 0.1, 0.86);
  specks(pixels, rnd, rgb, 0.04, 1.1);
}

function planks(pixels: Pixels, rnd: () => number, rgb: number) {
  for (let board = 0; board < 4; board++) {
    const seam = Math.floor(rnd() * textureSize);
    const tone = 0.92 + rnd() * 0.12;
    for (let row = 0; row < 4; row++) {
      const y = board * 4 + row;
      const grain = 0.96 + rnd() * 0.08;
      for (let x = 0; x < textureSize; x++) {
        let factor = tone * grain * (1 + (rnd() - 0.5) * 0.06);
        if (row === 3) factor *= 0.72;
        if (x === seam) factor *= 0.74;
        put(pixels, x, y, rgb, factor);
      }
    }
  }
}

function log(pixels: Pixels, rnd: () => number, rgb: number, part: TexturePart) {
  if (part === "end" || part === "top" || part === "bottom") {
    for (let y = 0; y < textureSize; y++) {
      for (let x = 0; x < textureSize; x++) {
        const distance = Math.max(Math.abs(x - 7.5), Math.abs(y - 7.5));
        const factor = distance >= 7 ? barkFactor : Math.floor(distance) % 2 === 0 ? 1.06 : 0.9;
        put(pixels, x, y, rgb, factor * (1 + (rnd() - 0.5) * 0.08));
      }
    }
    return;
  }
  for (let x = 0; x < textureSize; x++) {
    const column = barkFactor * (0.85 + rnd() * 0.3);
    for (let y = 0; y < textureSize; y++) put(pixels, x, y, rgb, column * (1 + (rnd() - 0.5) * 0.12));
  }
}

function leaves(pixels: Pixels, rnd: () => number, rgb: number) {
  for (let y = 0; y < textureSize; y++) {
    for (let x = 0; x < textureSize; x++) {
      if (rnd() < 0.2) put(pixels, x, y, rgb, 0.4, 0);
      else put(pixels, x, y, rgb, 0.7 + rnd() * 0.45);
    }
  }
}

function glass(pixels: Pixels, rnd: () => number, rgb: number, name: string) {
  const icy = /ice$/.test(name);
  const stained = name.includes("stained") || name === "tinted_glass";
  for (let y = 0; y < textureSize; y++) {
    for (let x = 0; x < textureSize; x++) {
      const edge = x === 0 || y === 0 || x === textureSize - 1 || y === textureSize - 1;
      const streak = (x + y === 11 || x + y === 12 || x + y === 5) && x > 1 && y > 1 && x < 14 && y < 14;
      if (icy) put(pixels, x, y, rgb, 1 + (rnd() - 0.5) * 0.12 + (streak ? 0.12 : 0), 200);
      else if (edge) put(pixels, x, y, stained ? rgb : 0xdbeef2, 1.05, stained ? 230 : 255);
      else if (streak && !stained) put(pixels, x, y, 0xffffff, 1, 255);
      else if (stained) put(pixels, x, y, rgb, 1 + (streak ? 0.15 : 0), 120);
      else put(pixels, x, y, 0xffffff, 1, 0);
    }
  }
}

function wool(pixels: Pixels, rnd: () => number, rgb: number) {
  for (let y = 0; y < textureSize; y++) {
    for (let x = 0; x < textureSize; x++) {
      const fibre = (x + y * 3) % 5 === 0 ? 0.9 : 1;
      put(pixels, x, y, rgb, fibre * (1 + (rnd() - 0.5) * 0.18));
    }
  }
}

function ore(pixels: Pixels, rnd: () => number, name: string) {
  const host = name.startsWith("deepslate_") ? 0x4d4d52 : name.startsWith("nether_") ? 0x723232 : 0x7f7f7f;
  stone(pixels, rnd, host);
  const colour = oreColours.find(([pattern]) => pattern.test(name))?.[1] ?? 0xcccccc;
  for (let n = 0; n < 5; n++) {
    const cx = 2 + Math.floor(rnd() * 12);
    const cy = 2 + Math.floor(rnd() * 12);
    put(pixels, cx, cy, colour, 1.1);
    put(pixels, cx + 1, cy, colour, 0.95);
    put(pixels, cx, cy + 1, colour, 0.8);
    if (rnd() < 0.6) put(pixels, cx + 1, cy + 1, colour, 0.7);
  }
}

function water(pixels: Pixels, rnd: () => number) {
  const phase = rnd() * Math.PI * 2;
  for (let y = 0; y < textureSize; y++) {
    for (let x = 0; x < textureSize; x++) {
      const wave = Math.sin((x + y * 0.6) / 2.4 + phase) * 0.06 + Math.sin((x * 0.4 - y) / 3.1) * 0.05;
      const value = 0.78 + wave + (rnd() - 0.5) * 0.05;
      put(pixels, x, y, 0xffffff, value, 180);
    }
  }
}

function lava(pixels: Pixels, rnd: () => number) {
  noiseFill(pixels, rnd, 0xd65a12, 0.12);
  blobs(pixels, rnd, 0xffc72e, 9, 1);
  blobs(pixels, rnd, 0x9e2a0a, 5, 1);
}

function metal(pixels: Pixels, rnd: () => number, rgb: number) {
  for (let y = 0; y < textureSize; y++) {
    for (let x = 0; x < textureSize; x++) {
      let factor = 1 + (rnd() - 0.5) * 0.06;
      if (x === 0 || y === 0) factor = 1.25;
      else if (x === textureSize - 1 || y === textureSize - 1) factor = 0.68;
      else if (x === 1 || y === 1) factor *= 1.08;
      put(pixels, x, y, rgb, factor);
    }
  }
}

function clear(pixels: Pixels) {
  pixels.fill(0);
}

function blade(pixels: Pixels, rnd: () => number, rgb: number, x: number, height: number) {
  let px = x;
  const lean = rnd() < 0.5 ? -1 : 1;
  for (let step = 0; step < height; step++) {
    if (step > height / 2 && rnd() < 0.3) px += lean;
    put(pixels, px, textureSize - 1 - step, rgb, 0.75 + (step / height) * 0.4);
  }
}

function plant(pixels: Pixels, rnd: () => number, rgb: number, name: string) {
  clear(pixels);
  const green = 0x3f8f2a;
  if (/mushroom|fungus/.test(name)) {
    const cap = name.includes("red") ? 0xc82020 : name.includes("warped") ? 0x167e86 : name.includes("crimson") ? 0x8e1a1a : 0x9a6a4a;
    for (let y = 9; y < 16; y++) {
      put(pixels, 7, y, 0xe6dccb, 1);
      put(pixels, 8, y, 0xe6dccb, 0.9);
    }
    for (let y = 5; y < 9; y++) for (let x = 5 - (y - 5); x <= 10 + (y - 5); x++) put(pixels, x, y, cap, 0.9 + rnd() * 0.2);
    return;
  }
  const blossom = dyeColour(name);
  if (blossom !== null && !/grass|fern/.test(name)) {
    for (let y = 8; y < 16; y++) put(pixels, 7 + (y > 12 ? 1 : 0), y, green, 0.9);
    put(pixels, 6, 12, green, 1);
    put(pixels, 9, 11, green, 1);
    for (let y = 3; y < 8; y++) {
      for (let x = 5; x < 11; x++) {
        const corner = (y === 3 || y === 7) && (x === 5 || x === 10);
        if (!corner) put(pixels, x, y, blossom, 0.85 + rnd() * 0.25);
      }
    }
    put(pixels, 7, 5, 0xfed83d, 1);
    put(pixels, 8, 5, 0xfed83d, 0.9);
    return;
  }
  if (/sapling|bush|propagule|roots/.test(name)) {
    for (let y = 10; y < 16; y++) put(pixels, 7, y, 0x6b5033, 1);
    for (let y = 2; y < 11; y++) {
      for (let x = 3; x < 13; x++) {
        const dx = x - 7.5;
        const dy = y - 6.5;
        if (dx * dx + dy * dy < 20 && rnd() < 0.8) put(pixels, x, y, rgb, 0.7 + rnd() * 0.45);
      }
    }
    return;
  }
  if (name === "sugar_cane" || name === "bamboo_sapling") {
    for (const x of [3, 8, 12]) for (let y = 0; y < 16; y++) put(pixels, x, y, rgb, y % 5 === 0 ? 0.7 : 1);
    return;
  }
  const count = 7 + Math.floor(rnd() * 3);
  for (let n = 0; n < count; n++) blade(pixels, rnd, rgb, 1 + Math.floor(rnd() * 14), 6 + Math.floor(rnd() * 10));
}

function torch(pixels: Pixels, name: string) {
  clear(pixels);
  const flame = name.startsWith("soul") ? 0x6fe3ea : name.startsWith("redstone") ? 0xff2a1a : 0xffd84a;
  for (let y = 8; y < 16; y++) {
    put(pixels, 7, y, 0x6b5033, 1);
    put(pixels, 8, y, 0x6b5033, 0.8);
  }
  put(pixels, 7, 6, flame, 1);
  put(pixels, 8, 6, flame, 0.9);
  put(pixels, 7, 7, flame, 0.85);
  put(pixels, 8, 7, 0xff9f1a, 1);
}

const tierColours: [RegExp, number][] = [
  [/^wooden_/, 0x9c7a4a],
  [/^stone_/, 0x8a8a8a],
  [/^iron_/, 0xd8d8d8],
  [/^golden_/, 0xfad64a],
  [/^diamond_/, 0x4aedd9],
  [/^netherite_/, 0x4d494d],
];

export const toolPattern = /_(sword|pickaxe|axe|shovel|hoe)$/;

function itemSprite(pixels: Pixels, rnd: () => number, name: string, rgb: number) {
  clear(pixels);
  const tool = toolPattern.exec(name);
  if (tool) {
    const head = tierColours.find(([pattern]) => pattern.test(name))?.[1] ?? rgb;
    for (let i = 0; i < 9; i++) put(pixels, 2 + i, 13 - i, 0x6b5033, i % 2 ? 0.85 : 1);
    if (tool[1] === "sword") {
      for (let i = 0; i < 9; i++) {
        put(pixels, 6 + i, 9 - i, head, 1.1);
        put(pixels, 7 + i, 9 - i, head, 0.85);
      }
      for (let i = 0; i < 4; i++) put(pixels, 4 + i, 8 + i, 0x3b2a18, 1);
    } else if (tool[1] === "shovel") {
      for (let y = 1; y < 6; y++) for (let x = 10; x < 15; x++) if (x + y > 11 && x + y < 19) put(pixels, x, y, head, 0.9 + rnd() * 0.2);
    } else {
      const wide = tool[1] === "pickaxe";
      for (let i = 0; i < (wide ? 10 : 5); i++) put(pixels, 5 + i, 2 + Math.abs(i - 4) * (wide ? 0.5 : 0) | 0, head, 1);
      for (let i = 0; i < 5; i++) put(pixels, 9 + (i % 2), 3 + i, head, 0.85);
    }
    return;
  }
  const colour = dyeColour(name) ?? oreColours.find(([pattern]) => pattern.test(name))?.[1] ?? rgb;
  for (let y = 4; y < 12; y++) {
    for (let x = 4; x < 12; x++) {
      const edge = x === 4 || y === 4 || x === 11 || y === 11;
      const corner = (x === 4 || x === 11) && (y === 4 || y === 11);
      if (!corner) put(pixels, x, y, colour, edge ? 0.7 : 0.9 + rnd() * 0.25);
    }
  }
}

function generic(pixels: Pixels, rnd: () => number, rgb: number) {
  noiseFill(pixels, rnd, rgb, 0.1);
  for (let i = 0; i < textureSize; i++) {
    put(pixels, i, textureSize - 1, rgb, 0.8);
    put(pixels, textureSize - 1, i, rgb, 0.8);
  }
}

function missing(pixels: Pixels) {
  for (let y = 0; y < textureSize; y++) for (let x = 0; x < textureSize; x++) put(pixels, x, y, (x < 8) !== (y < 8) ? 0xf800f8 : 0x000000, 1);
}

export function proceduralTexture(name: string, part: TexturePart, rgb: number): Uint8ClampedArray {
  const pixels = new Uint8ClampedArray(textureSize * textureSize * 4);
  const rnd = seededRandom(hashString(`${name}/${part}`));
  if (part === "item") {
    itemSprite(pixels, rnd, name, rgb);
    return pixels;
  }
  const cls = materialClass(name);
  switch (cls) {
    case "stone":
      stone(pixels, rnd, rgb);
      break;
    case "dirt":
      dirt(pixels, rnd, rgb);
      break;
    case "grass":
      grass(pixels, rnd, rgb, part);
      break;
    case "sand":
      sand(pixels, rnd, rgb);
      break;
    case "planks":
      planks(pixels, rnd, rgb);
      break;
    case "log":
      log(pixels, rnd, rgb, part);
      break;
    case "leaves":
      leaves(pixels, rnd, rgb);
      break;
    case "glass":
      glass(pixels, rnd, rgb, name);
      break;
    case "wool":
      wool(pixels, rnd, rgb);
      break;
    case "concrete":
      noiseFill(pixels, rnd, rgb, 0.04);
      break;
    case "ore":
      ore(pixels, rnd, name);
      break;
    case "water":
      water(pixels, rnd);
      break;
    case "lava":
      lava(pixels, rnd);
      break;
    case "metal":
      metal(pixels, rnd, rgb);
      break;
    case "plant":
      plant(pixels, rnd, rgb, name);
      break;
    case "torch":
      torch(pixels, name);
      break;
    case "missing":
      missing(pixels);
      break;
    default:
      generic(pixels, rnd, rgb);
  }
  return pixels;
}

export function proceduralId(name: string, part: TexturePart): string {
  return `procedural/${name}/${part}`;
}

export function parseProceduralId(id: string): { name: string; part: TexturePart } | null {
  const match = /^procedural\/([a-z0-9_]+)\/(all|top|side|bottom|end|item)$/.exec(id);
  return match ? { name: match[1], part: match[2] as TexturePart } : null;
}
