const playerColours = ["#e8590c", "#1c7ed6", "#2f9e44", "#ae3ec9", "#f08c00", "#0c8599", "#e03131", "#5c7cfa"];

export function playerColour(index: number): string {
  return playerColours[index % playerColours.length];
}
