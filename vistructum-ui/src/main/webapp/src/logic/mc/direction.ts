export const DOWN = 0;
export const UP = 1;
export const NORTH = 2;
export const SOUTH = 3;
export const WEST = 4;
export const EAST = 5;

export type Direction = 0 | 1 | 2 | 3 | 4 | 5;

export const directionNames = ["down", "up", "north", "south", "west", "east"] as const;

export type DirectionName = (typeof directionNames)[number];

export const directionVectors: readonly (readonly [number, number, number])[] = [
  [0, -1, 0],
  [0, 1, 0],
  [0, 0, -1],
  [0, 0, 1],
  [-1, 0, 0],
  [1, 0, 0],
];

export const directionAxis: readonly (0 | 1 | 2)[] = [1, 1, 2, 2, 0, 0];

export const directionShade: readonly number[] = [0.5, 1, 0.8, 0.8, 0.6, 0.6];

export function directionOfName(name: string): Direction | null {
  const index = directionNames.indexOf(name as DirectionName);
  return index < 0 ? null : (index as Direction);
}

export function nearestDirection(x: number, y: number, z: number): Direction {
  let best: Direction = UP;
  let bestDot = Number.NEGATIVE_INFINITY;
  directionVectors.forEach(([vx, vy, vz], index) => {
    const dot = vx * x + vy * y + vz * z;
    if (dot > bestDot + 1e-9) {
      bestDot = dot;
      best = index as Direction;
    }
  });
  return best;
}

export function opposite(direction: Direction): Direction {
  return (direction ^ 1) as Direction;
}
