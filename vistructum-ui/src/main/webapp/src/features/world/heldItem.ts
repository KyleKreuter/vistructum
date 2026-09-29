import { BufferAttribute, BufferGeometry, DataTexture, FrontSide, Group, Matrix4, Mesh, MeshLambertMaterial, NearestFilter, Quaternion, RGBAFormat, SRGBColorSpace, UnsignedByteType, Euler } from "three";
import { atlasUv, firstFrameIndex, frameImage, packAtlas, type TextureImage } from "@/logic/mc/atlas";
import type { RawQuad } from "@/logic/mc/bake";
import { srgbToLinear } from "@/logic/mc/colour";
import { extrudeSprite, resolveItem } from "@/logic/mc/items";
import type { DisplayTransform } from "@/logic/mc/models";
import type { WorldAssets } from "./assets";

interface ItemAsset {
  geometry: BufferGeometry;
  material: MeshLambertMaterial;
  display: DisplayTransform;
}

const cache = new Map<string, Promise<ItemAsset | null>>();

async function frame(assets: WorldAssets, id: string): Promise<TextureImage> {
  const image = await assets.image(id);
  const count = Math.max(1, Math.floor(image.height / Math.max(1, image.width)));
  return frameImage(image, firstFrameIndex(assets.animated[id], count));
}

function geometryOf(quads: { quad: RawQuad; tint: number }[], atlas: ReturnType<typeof packAtlas>): BufferGeometry {
  const positions = new Float32Array(quads.length * 12);
  const normals = new Float32Array(quads.length * 12);
  const uvs = new Float32Array(quads.length * 8);
  const colours = new Float32Array(quads.length * 12);
  const indices = new Uint32Array(quads.length * 6);
  quads.forEach(({ quad, tint }, q) => {
    const rect = atlas.rects.get(quad.texture);
    const rgb = [srgbToLinear(((tint >> 16) & 255) / 255), srgbToLinear(((tint >> 8) & 255) / 255), srgbToLinear((tint & 255) / 255)];
    for (let i = 0; i < 4; i++) {
      positions.set(quad.positions.subarray(i * 3, i * 3 + 3), q * 12 + i * 3);
      normals.set(quad.normalVector, q * 12 + i * 3);
      colours.set(rgb, q * 12 + i * 3);
      const [u, v] = rect ? atlasUv(atlas, rect, quad.uvs[i * 2], quad.uvs[i * 2 + 1]) : [0, 0];
      uvs[q * 8 + i * 2] = u;
      uvs[q * 8 + i * 2 + 1] = v;
    }
    indices.set([q * 4, q * 4 + 1, q * 4 + 2, q * 4, q * 4 + 2, q * 4 + 3], q * 6);
  });
  const geometry = new BufferGeometry();
  geometry.setAttribute("position", new BufferAttribute(positions, 3));
  geometry.setAttribute("normal", new BufferAttribute(normals, 3));
  geometry.setAttribute("uv", new BufferAttribute(uvs, 2));
  geometry.setAttribute("color", new BufferAttribute(colours, 3));
  geometry.setIndex(new BufferAttribute(indices, 1));
  geometry.computeBoundingSphere();
  return geometry;
}

async function buildItem(name: string, assets: WorldAssets): Promise<ItemAsset | null> {
  const render = resolveItem(name, assets.source, assets.colours);
  if (!render) return null;
  const textures = new Set(render.kind === "sprite" ? render.layers.map((layer) => layer.texture) : render.quads.map((quad) => quad.texture));
  const images = new Map<string, TextureImage>();
  await Promise.all([...textures].map(async (id) => images.set(id, await frame(assets, id))));
  const quads: { quad: RawQuad; tint: number }[] = [];
  if (render.kind === "sprite") {
    render.layers.forEach((layer, index) => {
      const image = images.get(layer.texture);
      if (!image) return;
      for (const quad of extrudeSprite(image, layer.texture, index)) quads.push({ quad, tint: layer.tint });
    });
  } else {
    for (const quad of render.quads) quads.push({ quad, tint: quad.tintindex >= 0 ? (render.tints[quad.tintindex] ?? 0xffffff) : 0xffffff });
  }
  const atlas = packAtlas([...images].map(([id, image]) => ({ id, image })));
  const texture = new DataTexture(new Uint8Array(atlas.pixels.buffer), atlas.width, atlas.height, RGBAFormat, UnsignedByteType);
  texture.magFilter = NearestFilter;
  texture.minFilter = NearestFilter;
  texture.generateMipmaps = false;
  texture.colorSpace = SRGBColorSpace;
  texture.needsUpdate = true;
  const material = new MeshLambertMaterial({ map: texture, vertexColors: true, alphaTest: 0.1, side: FrontSide });
  return { geometry: geometryOf(quads, atlas), material, display: render.display };
}

export function loadHeldItem(name: string, assets: WorldAssets): Promise<ItemAsset | null> {
  const key = `${assets.key}:${name}`;
  const known = cache.get(key);
  if (known) return known;
  const promise = buildItem(name, assets).catch(() => null);
  cache.set(key, promise);
  return promise;
}

const degrees = Math.PI / 180;

export function handTransform(display: DisplayTransform): Matrix4 {
  const matrix = new Matrix4().makeRotationX(-90 * degrees);
  matrix.multiply(new Matrix4().makeRotationY(180 * degrees));
  matrix.multiply(new Matrix4().makeTranslation(1 / 16, 0.125, -0.625));
  const [tx, ty, tz] = display.translation;
  matrix.multiply(new Matrix4().makeTranslation(tx / 16, ty / 16, tz / 16));
  const [rx, ry, rz] = display.rotation;
  matrix.multiply(new Matrix4().makeRotationFromQuaternion(new Quaternion().setFromEuler(new Euler(rx * degrees, ry * degrees, rz * degrees, "XYZ"))));
  matrix.multiply(new Matrix4().makeScale(...display.scale));
  matrix.multiply(new Matrix4().makeTranslation(-0.5, -0.5, -0.5));
  return matrix;
}

export function heldItemObject(asset: ItemAsset): Group {
  const container = new Group();
  container.scale.set(16, -16, -16);
  const mesh = new Mesh(asset.geometry, asset.material);
  mesh.matrixAutoUpdate = false;
  mesh.matrix.copy(handTransform(asset.display));
  container.add(mesh);
  return container;
}

