import { CanvasTexture, NearestFilter, SRGBColorSpace, type Texture } from "three";
import { inferModelType, loadSkinToCanvas } from "skinview-utils";

export interface LoadedSkin {
  texture: Texture;
  slim: boolean;
}

const cache = new Map<string, Promise<LoadedSkin | null>>();

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
    .catch(() => null);
  cache.set(url, promise);
  return promise;
}
