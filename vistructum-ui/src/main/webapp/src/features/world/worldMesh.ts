import { BufferAttribute, BufferGeometry, FrontSide, Group, Mesh, MeshBasicMaterial, type Camera, type Texture } from "three";
import { blitWithGutter, frameImage, animationFrameAt } from "@/logic/mc/atlas";
import { meshSection, sortTranslucent, type Grid, type LayerGeometry } from "@/logic/mc/mesher";
import { dirtySections, sectionCoordinates, sectionLayout } from "@/logic/mc/sections";
import type { LoadedLibrary } from "./library";

export interface BlockMaterials {
  opaque: MeshBasicMaterial;
  cutout: MeshBasicMaterial;
  translucent: MeshBasicMaterial;
}

export function createBlockMaterials(texture: Texture): BlockMaterials {
  return {
    opaque: new MeshBasicMaterial({ map: texture, vertexColors: true, side: FrontSide }),
    cutout: new MeshBasicMaterial({ map: texture, vertexColors: true, alphaTest: 0.1, side: FrontSide }),
    translucent: new MeshBasicMaterial({ map: texture, vertexColors: true, transparent: true, depthWrite: true, side: FrontSide }),
  };
}

export function disposeMaterials(materials: BlockMaterials) {
  materials.opaque.dispose();
  materials.cutout.dispose();
  materials.translucent.dispose();
}

interface SectionMeshes {
  meshes: (Mesh | null)[];
  translucent: LayerGeometry | null;
  sortedFrom: [number, number, number] | null;
}

export interface WorldMesh {
  group: Group;
  update: (cells?: Iterable<number>) => number;
  sort: (camera: Camera) => void;
  dispose: () => void;
}

function geometryOf(layer: LayerGeometry): BufferGeometry {
  const geometry = new BufferGeometry();
  geometry.setAttribute("position", new BufferAttribute(layer.positions, 3));
  geometry.setAttribute("uv", new BufferAttribute(layer.uvs, 2));
  geometry.setAttribute("color", new BufferAttribute(layer.colours, 3));
  geometry.setIndex(new BufferAttribute(layer.indices, 1));
  geometry.computeBoundingSphere();
  return geometry;
}

export function createWorldMesh(grid: Grid, loaded: LoadedLibrary, materials: BlockMaterials): WorldMesh {
  const group = new Group();
  const layout = sectionLayout(grid);
  const sections: SectionMeshes[] = Array.from({ length: layout.total }, () => ({ meshes: [null, null, null], translucent: null, sortedFrom: null }));
  const layerMaterials = [materials.opaque, materials.cutout, materials.translucent];
  const { blocks, occluders } = loaded.library;

  const remesh = (index: number) => {
    const section = sections[index];
    const [sx, sy, sz] = sectionCoordinates(layout, index);
    const layers = meshSection(grid, blocks, occluders, sx, sy, sz);
    layers.forEach((layer, n) => {
      const previous = section.meshes[n];
      if (previous) {
        previous.geometry.dispose();
        group.remove(previous);
        section.meshes[n] = null;
      }
      if (!layer.quadCount) return;
      const mesh = new Mesh(geometryOf(layer), layerMaterials[n]);
      mesh.matrixAutoUpdate = false;
      if (n === 2) mesh.renderOrder = 1;
      section.meshes[n] = mesh;
      group.add(mesh);
    });
    section.translucent = layers[2].quadCount ? layers[2] : null;
    section.sortedFrom = null;
  };

  const update = (cells?: Iterable<number>) => {
    const start = performance.now();
    const targets = cells ? dirtySections(grid, cells) : sections.keys();
    for (const index of targets) remesh(index);
    const elapsed = performance.now() - start;
    performance.measure(cells ? "vistructum:remesh" : "vistructum:mesh", { start, duration: elapsed });
    return elapsed;
  };

  const sort = (camera: Camera) => {
    const eye = camera.position.clone();
    group.worldToLocal(eye);
    for (const section of sections) {
      const layer = section.translucent;
      const mesh = section.meshes[2];
      if (!layer || !mesh) continue;
      const from = section.sortedFrom;
      if (from && Math.hypot(from[0] - eye.x, from[1] - eye.y, from[2] - eye.z) < 0.75) continue;
      const index = mesh.geometry.getIndex();
      if (!index) continue;
      (index.array as Uint32Array).set(sortTranslucent(layer, eye.x, eye.y, eye.z));
      index.needsUpdate = true;
      section.sortedFrom = [eye.x, eye.y, eye.z];
    }
  };

  const dispose = () => {
    for (const section of sections) for (const mesh of section.meshes) mesh?.geometry.dispose();
  };

  update();
  return { group, update, sort, dispose };
}

export function animateAtlas(loaded: LoadedLibrary, tick: number, shown: Map<string, number>): boolean {
  let changed = false;
  for (const animated of loaded.library.animations) {
    const frame = animationFrameAt(animated.animation, tick);
    if (shown.get(animated.id) === frame) continue;
    const rect = loaded.library.atlas.rects.get(animated.id);
    if (!rect) continue;
    blitWithGutter(loaded.library.atlas, rect, frameImage(animated.strip, frame));
    shown.set(animated.id, frame);
    changed = true;
  }
  if (changed) loaded.texture.needsUpdate = true;
  return changed;
}

