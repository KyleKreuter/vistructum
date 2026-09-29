import type { AssetSource } from "./library";

const cubeFaces = (texture: string, tintindex = -1) =>
  Object.fromEntries(["down", "up", "north", "south", "west", "east"].map((face) => [face, { texture, cullface: face, tintindex }]));

export const fixtureModels: Record<string, unknown> = {
  "block/block": { display: { thirdperson_righthand: { rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: [0.375, 0.375, 0.375] } } },
  "block/cube": {
    parent: "block/block",
    elements: [
      {
        from: [0, 0, 0],
        to: [16, 16, 16],
        faces: {
          down: { texture: "#down", cullface: "down" },
          up: { texture: "#up", cullface: "up" },
          north: { texture: "#north", cullface: "north" },
          south: { texture: "#south", cullface: "south" },
          west: { texture: "#west", cullface: "west" },
          east: { texture: "#east", cullface: "east" },
        },
      },
    ],
  },
  "block/cube_all": { parent: "block/cube", textures: { particle: "#all", down: "#all", up: "#all", north: "#all", south: "#all", west: "#all", east: "#all" } },
  "block/cube_column": { parent: "block/cube", textures: { particle: "#side", down: "#end", up: "#end", north: "#side", south: "#side", west: "#side", east: "#side" } },
  "block/stone": { parent: "minecraft:block/cube_all", textures: { all: "minecraft:block/stone" } },
  "block/glass": { parent: "block/cube_all", textures: { all: "block/glass" } },
  "block/oak_leaves": { parent: "block/block", textures: { all: "block/oak_leaves" }, elements: [{ from: [0, 0, 0], to: [16, 16, 16], faces: cubeFaces("#all", 0) }] },
  "block/oak_log": { parent: "block/cube_column", textures: { end: "block/oak_log_top", side: "block/oak_log" } },
  "block/slab": {
    elements: [
      {
        from: [0, 0, 0],
        to: [16, 8, 16],
        faces: {
          down: { uv: [0, 0, 16, 16], texture: "#bottom", cullface: "down" },
          up: { uv: [0, 0, 16, 16], texture: "#top" },
          north: { uv: [0, 8, 16, 16], texture: "#side", cullface: "north" },
          south: { uv: [0, 8, 16, 16], texture: "#side", cullface: "south" },
          west: { uv: [0, 8, 16, 16], texture: "#side", cullface: "west" },
          east: { uv: [0, 8, 16, 16], texture: "#side", cullface: "east" },
        },
      },
    ],
  },
  "block/oak_slab": { parent: "block/slab", textures: { bottom: "block/oak_planks", top: "block/oak_planks", side: "block/oak_planks" } },
  "block/oak_fence_post": { textures: { texture: "block/oak_planks" }, elements: [{ from: [6, 0, 6], to: [10, 16, 10], faces: cubeFaces("#texture") }] },
  "block/oak_fence_side": { textures: { texture: "block/oak_planks" }, elements: [{ from: [7, 12, 0], to: [9, 15, 9], faces: { north: { texture: "#texture", cullface: "north" }, up: { texture: "#texture" } } }] },
  "block/wheat_stage7": { ambientocclusion: false, textures: { crop: "block/wheat_stage7" }, elements: [{ from: [4, -1, 0], to: [4, 15, 16], shade: false, faces: { east: { texture: "#crop" }, west: { texture: "#crop" } } }] },
  "block/wall_torch": {
    ambientocclusion: false,
    textures: { torch: "block/torch" },
    elements: [
      {
        from: [-1, 3.5, 7],
        to: [1, 13.5, 9],
        rotation: { origin: [0, 3.5, 8], axis: "z", angle: -22.5 },
        shade: false,
        faces: { up: { uv: [7, 6, 9, 8], texture: "#torch" }, north: { uv: [7, 6, 9, 16], texture: "#torch" } },
      },
    ],
  },
  "item/generated": { parent: "builtin/generated", display: { thirdperson_righthand: { rotation: [0, 0, 0], translation: [0, 3, 1], scale: [0.55, 0.55, 0.55] } } },
  "item/stick": { parent: "item/generated", textures: { layer0: "item/stick" } },
};

export const fixtureBlockstates: Record<string, unknown> = {
  stone: { variants: { "": { model: "minecraft:block/stone" } } },
  glass: { variants: { "": { model: "block/glass" } } },
  oak_leaves: { variants: { "": { model: "block/oak_leaves" } } },
  oak_log: {
    variants: {
      "axis=x": { model: "block/oak_log", x: 90, y: 90 },
      "axis=y": { model: "block/oak_log" },
      "axis=z": { model: "block/oak_log", x: 90 },
    },
  },
  oak_slab: {
    variants: {
      "type=bottom": [{ model: "block/oak_slab" }, { model: "block/stone" }],
      "type=double": { model: "block/stone" },
      "type=top": { model: "block/oak_slab", x: 180, uvlock: true },
    },
  },
  oak_fence: {
    multipart: [
      { apply: { model: "block/oak_fence_post" } },
      { when: { north: "true" }, apply: { model: "block/oak_fence_side", uvlock: true } },
      { when: { east: "true" }, apply: { model: "block/oak_fence_side", y: 90, uvlock: true } },
      { when: { south: "true" }, apply: { model: "block/oak_fence_side", y: 180, uvlock: true } },
      { when: { west: "true" }, apply: { model: "block/oak_fence_side", y: 270, uvlock: true } },
    ],
  },
  wheat: { variants: { "age=7": { model: "block/wheat_stage7" }, "age=0": { model: "block/wheat_stage7" } } },
  wall_torch: {
    variants: {
      "facing=east": { model: "block/wall_torch" },
      "facing=north": { model: "block/wall_torch", y: 270 },
      "facing=south": { model: "block/wall_torch", y: 90 },
      "facing=west": { model: "block/wall_torch", y: 180 },
    },
  },
};

export const fixtureTextures = new Set([
  "block/stone",
  "block/glass",
  "block/oak_leaves",
  "block/oak_log",
  "block/oak_log_top",
  "block/oak_planks",
  "block/wheat_stage7",
  "block/torch",
  "block/water_still",
  "item/stick",
]);

export const fixtureSource: AssetSource = {
  blockstate: (name) => fixtureBlockstates[name],
  model: (id) => fixtureModels[id],
  item: () => undefined,
  hasTexture: (id) => fixtureTextures.has(id),
};
