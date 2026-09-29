import { blockProperties, materialOf } from "@/logic/blocks";
import { stripNamespace } from "./json";

export interface BlockState {
  name: string;
  properties: Record<string, string>;
}

export function parseBlockData(blockData: string): BlockState {
  return { name: stripNamespace(materialOf(blockData)), properties: blockProperties(blockData.toLowerCase()) };
}

export function isWaterlogged(state: BlockState): boolean {
  return state.properties.waterlogged === "true" || /^(kelp|kelp_plant|seagrass|tall_seagrass|bubble_column)$/.test(state.name);
}
