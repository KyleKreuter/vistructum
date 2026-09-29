const steps = 4096;

const table = new Float32Array(steps + 1);
for (let i = 0; i <= steps; i++) {
  const value = i / steps;
  table[i] = value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
}

export function srgbToLinear(value: number): number {
  const clamped = value <= 0 ? 0 : value >= 1 ? 1 : value;
  return table[Math.round(clamped * steps)];
}
