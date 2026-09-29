import { CanvasTexture, Mesh, NearestFilter, SRGBColorSpace, type Object3D, type Texture } from "three";
import { inferModelType, loadSkinToCanvas } from "skinview-utils";

export interface LoadedSkin {
  texture: Texture;
  slim: boolean;
}

const cache = new Map<string, Promise<LoadedSkin | null>>();

export function disposeModel(model: Object3D) {
  model.traverse((node) => {
    if (!(node instanceof Mesh)) return;
    node.geometry.dispose();
    const materials: { dispose: () => void }[] = Array.isArray(node.material) ? node.material : [node.material];
    for (const material of materials) material.dispose();
  });
}

function loadImage(url: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image();
    image.crossOrigin = "anonymous";
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error(`skin ${url}`));
    image.src = url;
  });
}

export function loadSkin(url: string): Promise<LoadedSkin | null> {
  const known = cache.get(url);
  if (known) return known;
  const promise = loadImage(url)
    .then((image) => {
      const canvas = document.createElement("canvas");
      loadSkinToCanvas(canvas, image);
      const texture = new CanvasTexture(canvas);
      texture.magFilter = NearestFilter;
      texture.minFilter = NearestFilter;
      texture.colorSpace = SRGBColorSpace;
      return { texture, slim: inferModelType(canvas) === "slim" };
    })
    .catch(() => {
      cache.delete(url);
      return null;
    });
  cache.set(url, promise);
  return promise;
}
