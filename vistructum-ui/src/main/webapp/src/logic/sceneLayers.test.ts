import { describe, expect, it } from "vitest";
import type { Scene } from "@/api/types";
import { heatAlpha, median, paintLayer, pointToCell, readCell, sceneColours, terrainColumns, windowBaseHeight } from "./sceneLayers";

const scene: Scene = {
  source: "fullscan",
  width: 3,
  height: 2,
  window: { top: 0, left: 0, bottom: 1, right: 1 },
  palette: ["minecraft:stone", "minecraft:grass_block"],
  blocks: [0, 1, -1, 0, 0, 1],
  heights: [2, 4, 0, 2, 3, 6],
  luminance: [10, 20, 30, 40, 50, 60],
};

describe("scene layers", () => {
  const colours = sceneColours(scene, { "minecraft:stone": 0x808080, "minecraft:grass_block": 0x00ff00 });

  it("resolves palette colours", () => {
    expect(colours).toEqual([0x808080, 0x00ff00]);
  });

  it("computes the window base height", () => {
    expect(median([3, 1, 2])).toBe(2);
    expect(median([])).toBe(0);
    expect(windowBaseHeight(scene)).toBe(3);
  });

  it("paints unknown cells dark and luminance as grey", () => {
    const pixels = paintLayer(scene, colours, "luminance", { span: 4, heatmap: null, overlay: false });
    expect(pixels.length).toBe(24);
    expect([...pixels.slice(8, 12)]).toEqual([24, 24, 24, 255]);
    expect([...pixels.slice(0, 4)]).toEqual([10, 10, 10, 255]);
  });

  it("colours heights around the base", () => {
    const pixels = paintLayer(scene, colours, "height", { span: 1, heatmap: null, overlay: false });
    expect([...pixels.slice(4, 7)]).toEqual([245, 95, 40]);
    expect([...pixels.slice(0, 3)]).toEqual([45, 135, 230]);
  });

  it("tints with the heatmap overlay", () => {
    const heatmap = { width: 3, height: 2, values: [0, 255, 0, 0, 0, 0] };
    const plain = paintLayer(scene, colours, "luminance", { span: 4, heatmap, overlay: false });
    const tinted = paintLayer(scene, colours, "luminance", { span: 4, heatmap, overlay: true });
    expect(plain[4]).toBe(20);
    expect(tinted[4]).toBeGreaterThan(200);
    expect(heatAlpha(20, 10)).toBe(0.5);
  });

  it("reads cells and maps pointer positions", () => {
    expect(readCell(scene, 3, null, 1, 2)).toMatchObject({ material: "minecraft:grass_block", height: 6, relative: 3, luminance: 60 });
    expect(readCell(scene, 3, null, 0, 2)?.material).toBeNull();
    expect(readCell(scene, 3, null, 2, 0)).toBeNull();
    const rect = { left: 0, top: 0, width: 300, height: 300 };
    expect(pointToCell(rect, { width: 3, height: 2 }, 150, 100)).toEqual({ row: 0, col: 1 });
    expect(pointToCell(rect, { width: 3, height: 2 }, 150, 10)).toBeNull();
  });

  it("builds terrain columns", () => {
    const terrain = terrainColumns(scene, colours, null);
    expect(terrain.columns).toHaveLength(5);
    expect(terrain.floor).toBe(1);
    expect(terrain.columns[1]).toMatchObject({ row: 0, col: 1, top: 4, colour: 0x00ff00 });
  });
});
