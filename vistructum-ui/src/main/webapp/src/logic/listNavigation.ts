export interface ListEntry {
  id: number;
  open: boolean;
}

export interface LoadedPage {
  entries: ListEntry[];
  pageCount: number;
}

export interface Located {
  id: number;
  page: number;
}

export function neighbour(entries: ListEntry[], currentId: number, delta: 1 | -1): number | null {
  if (delta > 0) {
    for (const entry of entries) if (entry.id < currentId) return entry.id;
    return null;
  }
  let result: number | null = null;
  for (const entry of entries) {
    if (entry.id > currentId) result = entry.id;
    else break;
  }
  return result;
}

export function firstOpen(entries: ListEntry[], currentId: number, reviewed: ReadonlySet<number>, below: boolean): number | null {
  const match = entries.find(
    (entry) => entry.open && entry.id !== currentId && !reviewed.has(entry.id) && (!below || entry.id < currentId),
  );
  return match?.id ?? null;
}

export function positionOf(entries: ListEntry[], currentId: number): number {
  return entries.findIndex((entry) => entry.id === currentId);
}

export async function locate(
  start: number,
  step: 1 | -1,
  load: (page: number) => Promise<LoadedPage>,
  pick: (entries: ListEntry[]) => number | null,
): Promise<Located | null> {
  let page = Math.max(1, start);
  let count = page;
  while (page >= 1 && page <= count) {
    const loaded = await load(page);
    count = loaded.pageCount;
    const id = pick(loaded.entries);
    if (id !== null) return { id, page };
    page = step < 0 && page > count ? count : page + step;
  }
  return null;
}

export async function locateNeighbour(
  page: number,
  currentId: number,
  delta: 1 | -1,
  load: (page: number) => Promise<LoadedPage>,
): Promise<Located | null> {
  return locate(page, delta, load, (entries) => neighbour(entries, currentId, delta));
}

export async function locateNextOpen(
  page: number,
  currentId: number,
  reviewed: ReadonlySet<number>,
  load: (page: number) => Promise<LoadedPage>,
): Promise<Located | null> {
  const below = await locate(page, 1, load, (entries) => firstOpen(entries, currentId, reviewed, true));
  return below ?? locate(1, 1, load, (entries) => firstOpen(entries, currentId, reviewed, false));
}
