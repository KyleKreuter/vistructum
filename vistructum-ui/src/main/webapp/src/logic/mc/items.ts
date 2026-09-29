import { isAir } from "@/logic/blocks";
import { bakeFace, bakeModel, type RawQuad } from "./bake";
import type { TextureImage } from "./atlas";
import { parseBlockData } from "./blockData";
import { SOUTH, NORTH, WEST, EAST, UP, DOWN, type Direction } from "./direction";
import { fallbackPlacements } from "./fallbackModels";
import { isRecord, numberOf, stringOf, stripNamespace } from "./json";
import { modelLookup, type AssetSource } from "./library";
import { missingTexture, resolveModel, type DisplayTransform, type ModelElement } from "./models";
import { materialClass, proceduralId, toolPattern } from "./procedural";
import type { TintColours } from "./tint";

export interface ItemModelRef {
  model: string;
  tints: unknown[];
}

export function itemModelRef(definition: unknown, depth = 0): ItemModelRef | null {
  if (!isRecord(definition) || depth > 16) return null;
  const node = isRecord(definition.model) && depth === 0 ? definition.model : definition;
  const type = stripNamespace(stringOf(node.type) ?? "model");
  switch (type) {
    case "model": {
      const model = stringOf(node.model);
      return model ? { model: stripNamespace(model), tints: Array.isArray(node.tints) ? node.tints : [] } : null;
    }
    case "composite":
      return Array.isArray(node.models) ? itemModelRef(node.models[0], depth + 1) : null;
    case "condition":
      return itemModelRef(node.on_false, depth + 1) ?? itemModelRef(node.on_true, depth + 1);
    case "select": {
      const first = Array.isArray(node.cases) && isRecord(node.cases[0]) ? node.cases[0].model : undefined;
      return itemModelRef(node.fallback, depth + 1) ?? itemModelRef(first, depth + 1);
    }
    case "range_dispatch": {
      const first = Array.isArray(node.entries) && isRecord(node.entries[0]) ? node.entries[0].model : undefined;
      return itemModelRef(node.fallback, depth + 1) ?? itemModelRef(first, depth + 1);
    }
    case "special":
      return stringOf(node.base) ? { model: stripNamespace(stringOf(node.base) ?? ""), tints: [] } : null;
    default:
      return itemModelRef(node.fallback, depth + 1);
  }
}

export function itemTint(tint: unknown, colours: TintColours): number {
  if (!isRecord(tint)) return 0xffffff;
  const type = stripNamespace(stringOf(tint.type) ?? "");
  if (type === "constant") return numberOf(tint.value, -1) & 0xffffff;
  if (type === "grass") return colours.grass;
  if (type === "dye" || type === "firework" || type === "potion" || type === "map_color" || type === "team" || type === "custom_model_data") return numberOf(tint.default, -1) & 0xffffff;
  return 0xffffff;
}

export const blockDisplay: DisplayTransform = { rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: [0.375, 0.375, 0.375] };
export const generatedDisplay: DisplayTransform = { rotation: [0, 0, 0], translation: [0, 3, 1], scale: [0.55, 0.55, 0.55] };
export const handheldDisplay: DisplayTransform = { rotation: [0, -90, 55], translation: [0, 4, 0.5], scale: [0.85, 0.85, 0.85] };

export interface SpriteLayer {
  texture: string;
  tint: number;
}

export type ItemRender =
  | { kind: "sprite"; layers: SpriteLayer[]; display: DisplayTransform }
  | { kind: "block"; quads: RawQuad[]; tints: number[]; display: DisplayTransform };

function fallbackItem(name: string): ItemRender | null {
  if (isAir(name)) return null;
  const state = parseBlockData(name);
  if (toolPattern.test(state.name)) return { kind: "sprite", layers: [{ texture: proceduralId(state.name, "item"), tint: 0xffffff }], display: handheldDisplay };
  if (materialClass(state.name) === "plant" || materialClass(state.name) === "torch") {
    return { kind: "sprite", layers: [{ texture: proceduralId(state.name, "all"), tint: 0xffffff }], display: generatedDisplay };
  }
  if (/^(stick|bone|blaze_rod|breeze_rod|.*_ingot|.*_nugget|diamond|emerald|coal|charcoal|redstone|lapis_lazuli|quartz|amethyst_shard|flint|feather|string|paper|book|bow|crossbow|trident|shears|flint_and_steel|bucket|.*_bucket|apple|bread|.*_dye|arrow|compass|clock|map|filled_map|egg|snowball|ender_pearl|slime_ball|clay_ball|brick|nether_brick|glass_bottle|potion|bowl)$/.test(state.name)) {
    return { kind: "sprite", layers: [{ texture: proceduralId(state.name, "item"), tint: 0xffffff }], display: generatedDisplay };
  }
  const lookup = modelLookup(null);
  const quads: RawQuad[] = [];
  for (const placement of fallbackPlacements({ name: state.name, properties: {} })) {
    const model = resolveModel(placement.model, lookup);
    if (model) quads.push(...bakeModel(model, { x: 0, y: 0, uvlock: false }));
  }
  return quads.length ? { kind: "block", quads, tints: [], display: blockDisplay } : null;
}

export function resolveItem(name: string, source: AssetSource | null, colours: TintColours): ItemRender | null {
  const bare = stripNamespace(parseBlockData(name).name);
  if (!source) return fallbackItem(name);
  const reference = itemModelRef(source.item(bare)) ?? { model: `item/${bare}`, tints: [] };
  const model = resolveModel(reference.model, modelLookup(source));
  if (!model) return fallbackItem(name);
  const tints = reference.tints.map((tint) => itemTint(tint, colours));
  if (model.generated || !model.elements.length) {
    const layers: SpriteLayer[] = [];
    for (let i = 0; i < 8; i++) {
      const texture = model.textures[`layer${i}`];
      if (!texture) break;
      layers.push({ texture: source.hasTexture(texture) ? texture : missingTexture, tint: tints[i] ?? 0xffffff });
    }
    if (!layers.length) return fallbackItem(name);
    return { kind: "sprite", layers, display: model.display.thirdperson_righthand ?? generatedDisplay };
  }
  const quads = bakeModel(model, { x: 0, y: 0, uvlock: false }).map((quad) => (quad.texture.startsWith("procedural/") || source.hasTexture(quad.texture) ? quad : { ...quad, texture: missingTexture }));
  return { kind: "block", quads, tints, display: model.display.thirdperson_righthand ?? blockDisplay };
}

function pixelElement(from: [number, number, number], to: [number, number, number]): ModelElement {
  return { from, to, rotation: null, shade: true, faces: [null, null, null, null, null, null] };
}

export function extrudeSprite(image: TextureImage, texture: string, tintindex: number): RawQuad[] {
  const { width, height, pixels } = image;
  const unitX = 16 / width;
  const unitY = 16 / height;
  const quads: RawQuad[] = [];
  const placement = { x: 0, y: 0, uvlock: false };
  const face = (uv: [number, number, number, number]) => ({ uv, texture, cullface: null, rotation: 0, tintindex });
  const slab = pixelElement([0, 0, 7.5], [16, 16, 8.5]);
  for (const direction of [SOUTH, NORTH] as Direction[]) {
    const quad = bakeFace(slab, direction, face(direction === SOUTH ? [0, 0, 16, 16] : [16, 0, 0, 16]), placement);
    if (quad) quads.push(quad);
  }
  const opaque = (x: number, y: number) => x >= 0 && y >= 0 && x < width && y < height && pixels[(y * width + x) * 4 + 3] > 0;
  for (let py = 0; py < height; py++) {
    for (let px = 0; px < width; px++) {
      if (!opaque(px, py)) continue;
      const x0 = px * unitX;
      const x1 = x0 + unitX;
      const y1 = 16 - py * unitY;
      const y0 = y1 - unitY;
      const uv: [number, number, number, number] = [x0, py * unitY, x1, (py + 1) * unitY];
      const element = pixelElement([x0, y0, 7.5], [x1, y1, 8.5]);
      const edges: [boolean, Direction][] = [
        [!opaque(px - 1, py), WEST],
        [!opaque(px + 1, py), EAST],
        [!opaque(px, py - 1), UP],
        [!opaque(px, py + 1), DOWN],
      ];
      for (const [exposed, direction] of edges) {
        if (!exposed) continue;
        const quad = bakeFace(element, direction, face(uv), placement);
        if (quad) quads.push(quad);
      }
    }
  }
  return quads;
}
