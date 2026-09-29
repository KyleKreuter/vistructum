import { useEffect, useMemo } from "react";
import { BackSide, Color, Mesh, ShaderMaterial, SphereGeometry } from "three";
import type { SkyColours } from "./skyColours";

const vertexShader = `
varying vec3 vDirection;
void main() {
  vDirection = position;
  vec4 clip = projectionMatrix * vec4(mat3(viewMatrix) * position, 1.0);
  gl_Position = clip.xyww;
}`;

const fragmentShader = `
uniform vec3 zenith;
uniform vec3 horizon;
uniform vec3 ground;
varying vec3 vDirection;
void main() {
  vec3 direction = normalize(vDirection);
  float up = direction.y;
  vec3 colour = up >= 0.0 ? mix(horizon, zenith, smoothstep(0.0, 0.55, up)) : mix(horizon, ground, smoothstep(0.0, 0.25, -up));
  gl_FragColor = vec4(colour, 1.0);
  #include <colorspace_fragment>
}`;

export function Sky({ colours, near, far }: { colours: SkyColours; near: number; far: number }) {
  const dome = useMemo(() => {
    const material = new ShaderMaterial({
      uniforms: {
        zenith: { value: new Color(colours.zenith) },
        horizon: { value: new Color(colours.horizon) },
        ground: { value: new Color(colours.ground) },
      },
      vertexShader,
      fragmentShader,
      side: BackSide,
      depthWrite: false,
      depthTest: false,
      fog: false,
    });
    const mesh = new Mesh(new SphereGeometry(1, 32, 16), material);
    mesh.frustumCulled = false;
    mesh.renderOrder = -1000;
    return mesh;
  }, [colours]);
  useEffect(
    () => () => {
      dome.geometry.dispose();
      dome.material.dispose();
    },
    [dome],
  );
  return (
    <>
      <primitive object={dome} dispose={null} />
      <fog attach="fog" args={[colours.horizon, near, far]} />
    </>
  );
}
