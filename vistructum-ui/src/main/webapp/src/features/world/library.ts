import { useQuery } from "@tanstack/react-query";
import { DataTexture, NearestFilter, RGBAFormat, SRGBColorSpace, UnsignedByteType } from "three";
import type { TextureImage } from "@/logic/mc/atlas";
import { buildLibrary, planBlocks, type BlockLibrary } from "@/logic/mc/world";
import { rawTextures, type RawBlock } from "@/logic/mc/library";
import { missingTexture } from "@/logic/mc/models";
import type { WorldAssets } from "./assets";

export interface LoadedLibrary {
  library: BlockLibrary;
  texture: DataTexture;
}

export function atlasTexture(library: BlockLibrary): DataTexture {
  const { atlas } = library;
  const texture = new DataTexture(new Uint8Array(atlas.pixels.buffer, atlas.pixels.byteOffset, atlas.pixels.byteLength), atlas.width, atlas.height, RGBAFormat, UnsignedByteType);
  texture.magFilter = NearestFilter;
  texture.minFilter = NearestFilter;
  texture.generateMipmaps = false;
  texture.colorSpace = SRGBColorSpace;
  texture.flipY = false;
  texture.needsUpdate = true;
  return texture;
}

export async function loadImages(assets: WorldAssets, ids: string[]): Promise<Map<string, TextureImage>> {
  const entries = await Promise.all(ids.map(async (id) => [id, await assets.image(id)] as const));
  return new Map(entries);
}

export async function loadLibrary(assets: WorldAssets, raws: RawBlock[], textures: string[]): Promise<LoadedLibrary> {
  const images = await loadImages(assets, textures);
  const library = buildLibrary(raws, images, assets.animated, assets.colours);
  return { library, texture: atlasTexture(library) };
}

export function useBlockLibrary(assets: WorldAssets | null, states: string[], prepare?: (raws: RawBlock[]) => RawBlock[]): LoadedLibrary | null {
  const query = useQuery({
    queryKey: ["block-library", assets?.key ?? "none", prepare ? "columns" : "blocks", states.join("|")],
    queryFn: async () => {
      if (!assets) throw new Error("assets");
      const plan = planBlocks(states, assets.source);
      const raws = prepare ? prepare(plan.raws) : plan.raws;
      const textures = prepare ? planTextures(raws) : plan.textures;
      return loadLibrary(assets, raws, textures);
    },
    enabled: !!assets,
    staleTime: Number.POSITIVE_INFINITY,
    gcTime: 60_000,
    structuralSharing: false,
    retry: false,
  });
  return query.data ?? null;
}

function planTextures(raws: RawBlock[]): string[] {
  return [...rawTextures(raws).add(missingTexture)].sort();
}
