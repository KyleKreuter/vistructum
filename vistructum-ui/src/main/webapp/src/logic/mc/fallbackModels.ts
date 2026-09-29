import type { BlockState } from "./blockData";
import type { ModelPlacement } from "./blockstates";
import { directionNames } from "./direction";
import { materialClass, proceduralId, type TexturePart } from "./procedural";

type Vec = [number, number, number];

interface FaceSpec {
  texture: string;
  uv?: [number, number, number, number];
  tintindex?: number;
}

interface ElementJson {
  from: Vec;
  to: Vec;
  rotation?: { origin: Vec; axis: "x" | "y" | "z"; angle: number; rescale?: boolean };
  shade?: boolean;
  faces: Record<string, FaceSpec & { cullface?: string }>;
}

interface TemplateJson {
  ambientocclusion?: boolean;
  elements: ElementJson[];
}

const allFaces = directionNames;

function box(from: Vec, to: Vec, texture: (face: string) => string, faces: readonly string[] = allFaces): ElementJson {
  const result: ElementJson = { from, to, faces: {} };
  for (const face of faces) {
    const flush =
      (face === "down" && from[1] === 0) ||
      (face === "up" && to[1] === 16) ||
      (face === "north" && from[2] === 0) ||
      (face === "south" && to[2] === 16) ||
      (face === "west" && from[0] === 0) ||
      (face === "east" && to[0] === 16);
    result.faces[face] = flush ? { texture: texture(face), cullface: face } : { texture: texture(face) };
  }
  return result;
}

const sided = (face: string) => (face === "up" ? "#top" : face === "down" ? "#bottom" : "#side");
const all = () => "#all";

function crossElements(): ElementJson[] {
  const rotation = { origin: [8, 8, 8] as Vec, axis: "y" as const, angle: 45, rescale: true };
  return [
    { from: [0.8, 0, 8], to: [15.2, 16, 8], rotation, shade: false, faces: { north: { texture: "#all" }, south: { texture: "#all" } } },
    { from: [8, 0, 0.8], to: [8, 16, 15.2], rotation, shade: false, faces: { west: { texture: "#all" }, east: { texture: "#all" } } },
  ];
}

function torchElement(from: Vec, to: Vec, rotation?: ElementJson["rotation"]): ElementJson {
  return {
    from,
    to,
    rotation,
    shade: false,
    faces: {
      down: { texture: "#all", uv: [7, 13, 9, 15] },
      up: { texture: "#all", uv: [7, 6, 9, 8] },
      north: { texture: "#all", uv: [7, 6, 9, 16] },
      south: { texture: "#all", uv: [7, 6, 9, 16] },
      west: { texture: "#all", uv: [7, 6, 9, 16] },
      east: { texture: "#all", uv: [7, 6, 9, 16] },
    },
  };
}

const templates: Record<string, () => TemplateJson> = {
  cube: () => ({ elements: [box([0, 0, 0], [16, 16, 16], sided)] }),
  cross: () => ({ ambientocclusion: false, elements: crossElements() }),
  slab: () => ({ elements: [box([0, 0, 0], [16, 8, 16], sided)] }),
  slab_top: () => ({ elements: [box([0, 8, 0], [16, 16, 16], sided)] }),
  stairs: () => ({ elements: [box([0, 0, 0], [16, 8, 16], sided), box([8, 8, 0], [16, 16, 16], sided)] }),
  inner_stairs: () => ({ elements: [box([0, 0, 0], [16, 8, 16], sided), box([8, 8, 0], [16, 16, 16], sided), box([0, 8, 8], [8, 16, 16], sided)] }),
  outer_stairs: () => ({ elements: [box([0, 0, 0], [16, 8, 16], sided), box([8, 8, 8], [16, 16, 16], sided)] }),
  fence_post: () => ({ elements: [box([6, 0, 6], [10, 16, 10], all)] }),
  fence_side: () => ({ elements: [box([7, 12, 0], [9, 15, 6], all), box([7, 6, 0], [9, 9, 6], all)] }),
  wall_post: () => ({ elements: [box([4, 0, 4], [12, 16, 12], all)] }),
  wall_side: () => ({ elements: [box([5, 0, 0], [11, 14, 8], all)] }),
  wall_side_tall: () => ({ elements: [box([5, 0, 0], [11, 16, 8], all)] }),
  pane_post: () => ({ elements: [box([7, 0, 7], [9, 16, 9], all)] }),
  pane_side: () => ({ elements: [box([7, 0, 0], [9, 16, 7], all)] }),
  carpet: () => ({ elements: [box([0, 0, 0], [16, 1, 16], all)] }),
  pressure_plate: () => ({ elements: [box([1, 0, 1], [15, 1, 15], all)] }),
  flat: () => ({ ambientocclusion: false, elements: [box([0, 0.25, 0], [16, 0.25, 16], all, ["up", "down"])] }),
  trapdoor_bottom: () => ({ elements: [box([0, 0, 0], [16, 3, 16], all)] }),
  trapdoor_top: () => ({ elements: [box([0, 13, 0], [16, 16, 16], all)] }),
  trapdoor_open: () => ({ elements: [box([0, 0, 13], [16, 16, 16], all)] }),
  door: () => ({ elements: [box([0, 0, 0], [3, 16, 16], all)] }),
  small: () => ({ elements: [box([5, 0, 5], [11, 6, 11], all)] }),
  bed: () => ({ elements: [box([0, 3, 0], [16, 9, 16], sided)] }),
  chest: () => ({ elements: [box([1, 0, 1], [15, 14, 15], sided)] }),
  head: () => ({ elements: [box([4, 0, 4], [12, 8, 12], all)] }),
  torch: () => ({ ambientocclusion: false, elements: [torchElement([7, 0, 7], [9, 10, 9])] }),
  wall_torch: () => ({
    ambientocclusion: false,
    elements: [torchElement([-1, 3.5, 7], [1, 13.5, 9], { origin: [0, 3.5, 8], axis: "z", angle: -22.5 })],
  }),
};

for (let layers = 1; layers <= 8; layers++) {
  templates[`snow_${layers}`] = () => ({ elements: [box([0, 0, 0], [16, layers * 2, 16], all)] });
}

function texturesFor(name: string): Record<string, string> {
  const id = (part: TexturePart) => proceduralId(name, part);
  switch (materialClass(name)) {
    case "grass":
      return { top: id("top"), side: id("side"), bottom: id("bottom"), all: id("top") };
    case "log":
      return { top: id("end"), bottom: id("end"), side: id("side"), all: id("side") };
    default:
      return { top: id("all"), bottom: id("all"), side: id("all"), all: id("all") };
  }
}

export function fallbackModel(id: string): unknown {
  const match = /^fallback\/([a-z0-9_]+)\/([a-z0-9_]+)$/.exec(id);
  if (!match) return undefined;
  const template = templates[match[1]];
  if (!template) return undefined;
  return { textures: texturesFor(match[2]), ...template() };
}

const facingRotation: Record<string, number> = { east: 0, south: 90, west: 180, north: 270 };
const northRotation: Record<string, number> = { north: 0, east: 90, south: 180, west: 270 };

function place(template: string, name: string, x = 0, y = 0, uvlock = false): ModelPlacement {
  return { model: `fallback/${template}/${name}`, x, y: ((y % 360) + 360) % 360, uvlock };
}

function stairs(name: string, properties: Record<string, string>): ModelPlacement {
  const base = facingRotation[properties.facing ?? "east"] ?? 0;
  const shape = properties.shape ?? "straight";
  const top = properties.half === "top";
  if (shape === "straight") return place("stairs", name, top ? 180 : 0, base, true);
  const template = shape.startsWith("inner") ? "inner_stairs" : "outer_stairs";
  const left = shape.endsWith("left");
  const extra = top ? (left ? 0 : 90) : left ? 270 : 0;
  return place(template, name, top ? 180 : 0, base + extra, true);
}

function connected(properties: Record<string, string>, side: string): string | null {
  const value = properties[side];
  return value && value !== "false" && value !== "none" ? value : null;
}

function sides(name: string, properties: Record<string, string>, post: string, side: (value: string) => string): ModelPlacement[] {
  const result = [place(post, name)];
  for (const direction of ["north", "east", "south", "west"]) {
    const value = connected(properties, direction);
    if (value) result.push(place(side(value), name, 0, northRotation[direction], true));
  }
  return result;
}

export function fallbackPlacements(state: BlockState): ModelPlacement[] {
  const { name, properties } = state;
  if (/^(air|cave_air|void_air|water|lava|bubble_column|light|barrier|structure_void|moving_piston|end_portal|end_gateway)$/.test(name)) return [];
  if (/_bed$/.test(name)) return [place("bed", name)];
  if (/(chest|^decorated_pot)$/.test(name)) return [place("chest", name)];
  if (/(_head|_skull)$/.test(name)) return [place("head", name)];
  if (/_slab$/.test(name)) {
    if (properties.type === "double") return [place("cube", name)];
    return [place(properties.type === "top" ? "slab_top" : "slab", name)];
  }
  if (/_stairs$/.test(name)) return [stairs(name, properties)];
  if (/_fence$/.test(name)) return sides(name, properties, "fence_post", () => "fence_side");
  if (/_wall$/.test(name)) return sides(name, properties, "wall_post", (value) => (value === "tall" ? "wall_side_tall" : "wall_side"));
  if (/(_pane|^iron_bars)$/.test(name)) return sides(name, properties, "pane_post", () => "pane_side");
  if (/(carpet)$/.test(name)) return [place("carpet", name)];
  if (/_pressure_plate$/.test(name)) return [place("pressure_plate", name)];
  if (/wall_torch$/.test(name)) return [place("wall_torch", name, 0, facingRotation[properties.facing ?? "east"] ?? 0)];
  if (/torch$/.test(name) && name !== "torchflower") return [place("torch", name)];
  if (name === "snow") return [place(`snow_${Math.min(8, Math.max(1, Number(properties.layers ?? 1)))}`, name)];
  if (/(rail|^lily_pad|^redstone_wire)$/.test(name)) return [place("flat", name)];
  if (/_trapdoor$/.test(name)) {
    if (properties.open === "true") return [place("trapdoor_open", name, 0, northRotation[properties.facing ?? "north"] ?? 0)];
    return [place(properties.half === "top" ? "trapdoor_top" : "trapdoor_bottom", name)];
  }
  if (/_door$/.test(name)) {
    const base = facingRotation[properties.facing ?? "east"] ?? 0;
    return [place("door", name, 0, base + (properties.open === "true" ? 90 : 0))];
  }
  if (/(_button|_sign|_banner|^lever|^flower_pot|^potted_.*|^conduit)$/.test(name)) return [place("small", name)];
  if (materialClass(name) === "plant") return [place("cross", name)];
  if (/_log$|_wood$|_stem$|_hyphae$|^bamboo_block$|^basalt$|^hay_block$|_pillar$/.test(name)) {
    const axis = properties.axis ?? "y";
    return [place("cube", name, axis === "y" ? 0 : 90, axis === "x" ? 90 : 0)];
  }
  return [place("cube", name)];
}
