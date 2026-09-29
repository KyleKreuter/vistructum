const ambient = 0.4 * Math.PI;
const diffuse = 0.6 * Math.PI;

export function EntityLights() {
  return (
    <>
      <ambientLight intensity={ambient} />
      <directionalLight position={[0.2, 1, -0.7]} intensity={diffuse} />
      <directionalLight position={[-0.2, 1, 0.7]} intensity={diffuse} />
    </>
  );
}
