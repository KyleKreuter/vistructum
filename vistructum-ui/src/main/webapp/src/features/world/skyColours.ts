export interface SkyColours {
  zenith: string;
  horizon: string;
  ground: string;
}

export const daySky: SkyColours = { zenith: "#78a7ff", horizon: "#c3d9ff", ground: "#9fb6d9" };
export const duskSky: SkyColours = { zenith: "#2b4a82", horizon: "#8fa8cf", ground: "#56688a" };

export function skyTheme(dark: boolean): SkyColours {
  return dark ? duskSky : daySky;
}

export function fogRange(extent: number): [number, number] {
  const size = Math.max(24, extent);
  return [size * 2.2, size * 6];
}
