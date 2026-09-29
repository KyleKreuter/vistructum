export interface ListEntry {
  id: number;
  open: boolean;
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

export function nextOpen(entries: ListEntry[], currentId: number, reviewed: ReadonlySet<number> = new Set()): number | null {
  const candidates = entries.filter((entry) => entry.open && entry.id !== currentId && !reviewed.has(entry.id));
  if (!candidates.length) return null;
  return (candidates.find((entry) => entry.id < currentId) ?? candidates[0]).id;
}

export function positionOf(entries: ListEntry[], currentId: number): number {
  return entries.findIndex((entry) => entry.id === currentId);
}
