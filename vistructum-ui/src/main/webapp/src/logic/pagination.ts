export type PageSlot = number | "gap";

export function pageCount(total: number, pageSize: number): number {
  return Math.max(1, Math.ceil(total / pageSize));
}

export function pageSlots(current: number, count: number): PageSlot[] {
  if (count <= 7) return Array.from({ length: count }, (_, index) => index + 1);
  if (current <= 4) return [1, 2, 3, 4, 5, "gap", count];
  if (current >= count - 3) return [1, "gap", count - 4, count - 3, count - 2, count - 1, count];
  return [1, "gap", current - 1, current, current + 1, "gap", count];
}

export function pageRange(page: number, pageSize: number, total: number): { from: number; to: number } {
  const from = (page - 1) * pageSize + 1;
  const to = Math.min(page * pageSize, total);
  return from > to ? { from: 0, to: 0 } : { from, to };
}
