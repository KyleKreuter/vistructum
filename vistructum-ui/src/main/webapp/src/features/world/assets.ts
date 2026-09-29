import { useQuery } from "@tanstack/react-query";
import { apiBase, request } from "@/api/client";
import type { AssetStatus, AssetsDocument, Palette } from "@/api/types";
import type { TextureImage } from "@/logic/mc/atlas";
import type { AssetSource } from "@/logic/mc/library";
import { plainsClimate, plainsFoliage, plainsGrass, sampleColormap, type TintColours } from "@/logic/mc/tint";
import { hashString } from "@/logic/mc/random";
import { missingImage, proceduralImage } from "@/logic/mc/world";

export interface WorldAssets {
  key: string;
  source: AssetSource | null;
  animated: Record<string, unknown>;
  colours: TintColours;
  palette: Palette;
  image: (id: string) => Promise<TextureImage>;
}

const unavailable: AssetStatus = { available: false, version: null, downloading: false };

async function fetchStatus(): Promise<AssetStatus> {
  try {
    return await request<AssetStatus>("/assets", {}, { public: true });
  } catch {
    return unavailable;
  }
}

const decoded = new Map<string, Promise<TextureImage | null>>();

function decodePng(url: string): Promise<TextureImage | null> {
  const known = decoded.get(url);
  if (known) return known;
  const promise = fetch(url, { credentials: "same-origin" })
    .then((response) => (response.ok ? response.blob() : Promise.reject(new Error(String(response.status)))))
    .then((blob) => createImageBitmap(blob))
    .then((bitmap) => {
      const canvas = document.createElement("canvas");
      canvas.width = bitmap.width;
      canvas.height = bitmap.height;
      const context = canvas.getContext("2d", { willReadFrequently: true });
      if (!context) {
        bitmap.close();
        return null;
      }
      context.drawImage(bitmap, 0, 0);
      const data = context.getImageData(0, 0, bitmap.width, bitmap.height);
      bitmap.close();
      return { width: data.width, height: data.height, pixels: data.data };
    })
    .catch(() => {
      decoded.delete(url);
      return null;
    });
  decoded.set(url, promise);
  return promise;
}

function textureUrl(version: string, id: string): string {
  return `${apiBase}/assets/${encodeURIComponent(version)}/textures/${id}.png`;
}

function sourceOf(document: AssetsDocument): AssetSource {
  const textures = new Set(document.textures);
  return {
    blockstate: (name) => document.blockstates[name],
    model: (id) => document.models[id],
    item: (name) => document.items[name],
    hasTexture: (id) => textures.has(id),
  };
}

function paletteKey(palette: Palette): string {
  return hashString(
    Object.keys(palette)
      .sort()
      .map((name) => `${name}=${palette[name]}`)
      .join(","),
  ).toString(36);
}

async function loadWorldAssets(palette: Palette): Promise<WorldAssets> {
  const status = await fetchStatus();
  const procedural = (id: string) => Promise.resolve(proceduralImage(id, palette) ?? missingImage());
  const fallback: WorldAssets = {
    key: `procedural:${paletteKey(palette)}`,
    source: null,
    animated: {},
    colours: { grass: plainsGrass, foliage: plainsFoliage },
    palette,
    image: procedural,
  };
  const version = status.available ? status.version : null;
  if (!version) return fallback;
  let document: AssetsDocument;
  try {
    document = await request<AssetsDocument>(`/assets/${encodeURIComponent(version)}/models.json`, {}, { public: true });
  } catch {
    return fallback;
  }
  const source = sourceOf(document);
  const colormap = async (id: string, fallbackColour: number) => {
    if (!source.hasTexture(id)) return fallbackColour;
    return sampleColormap(await decodePng(textureUrl(version, id)), plainsClimate, fallbackColour);
  };
  const [grass, foliage] = await Promise.all([colormap("colormap/grass", plainsGrass), colormap("colormap/foliage", plainsFoliage)]);
  return {
    key: `${version}:${paletteKey(palette)}`,
    source,
    animated: document.animated ?? {},
    colours: { grass, foliage },
    palette,
    image: async (id) => {
      if (id.startsWith("procedural/")) return procedural(id);
      return (await decodePng(textureUrl(version, id))) ?? missingImage();
    },
  };
}

export function useWorldAssets(palette: Palette): WorldAssets | null {
  const query = useQuery({
    queryKey: ["world-assets", paletteKey(palette)],
    queryFn: () => loadWorldAssets(palette),
    staleTime: Number.POSITIVE_INFINITY,
    gcTime: Number.POSITIVE_INFINITY,
    structuralSharing: false,
    retry: false,
  });
  return query.data ?? null;
}
