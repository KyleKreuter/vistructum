import { useQuery } from "@tanstack/react-query";
import { DataTexture, NearestFilter, RGBAFormat, SRGBColorSpace, UnsignedByteType } from "three";
import type { TextureImage } from "@/logic/mc/atlas";
import { buildLibrary, planBlocks, type BlockLibrary } from "@/logic/mc/world";
import type { RawBlock } from "@/logic/mc/library";
import type { WorldAssets } from "./assets";

export interface LoadedLibrary {
  library: BlockLibrary;
  texture: DataTexture;
}

function atlasTexture(library: BlockLibrary): DataTexture {
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

async function loadImages(assets: WorldAssets, ids: string[]): Promise<Map<string, TextureImage>> {
  const entries = await Promise.all(ids.map(async (id) => [id, await assets.image(id)] as const));
  return new Map(entries);
}

async function loadLibrary(assets: WorldAssets, raws: RawBlock[], textures: string[]): Promise<LoadedLibrary> {
  const images = await loadImages(assets, textures);
  const library = buildLibrary(raws, images, assets.animated, assets.colours);
  return { library, texture: atlasTexture(library) };
}

export function useBlockLibrary(assets: WorldAssets | null, states: string[]): LoadedLibrary | null {
  const query = useQuery({
    queryKey: ["block-library", assets?.key ?? "none", states.join("|")],
    queryFn: async () => {
      if (!assets) throw new Error("assets");
      const plan = planBlocks(states, assets.source);
      return loadLibrary(assets, plan.raws, plan.textures);
    },
    enabled: !!assets,
    staleTime: Number.POSITIVE_INFINITY,
    gcTime: 60_000,
    structuralSharing: false,
    retry: false,
  });
  return query.data ?? null;
}
