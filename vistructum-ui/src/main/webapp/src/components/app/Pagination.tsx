import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight } from "lucide-react";
import type { ReactNode } from "react";
import type { Paging } from "@/api/types";
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { cn } from "@/lib/utils";
import { pageSizes } from "@/logic/filters";
import { pageCount, pageRange, pageSlots } from "@/logic/pagination";

function StepButton({ label, disabled, onClick, children, className }: { label: string; disabled: boolean; onClick: () => void; children: ReactNode; className?: string }) {
  return (
    <Button variant="outline" size="icon" className={cn("size-8", className)} aria-label={label} title={label} disabled={disabled} onClick={onClick}>
      {children}
    </Button>
  );
}

export function Pagination({
  paging,
  total,
  onChange,
  unit = "Findings",
  sizes = pageSizes,
  className,
}: {
  paging: Paging;
  total: number;
  onChange: (paging: Paging) => void;
  unit?: string;
  sizes?: readonly number[];
  className?: string;
}) {
  const { page, pageSize } = paging;
  const count = pageCount(total, pageSize);
  const range = pageRange(page, pageSize, total);
  const go = (target: number) => onChange({ page: Math.min(Math.max(1, target), count), pageSize });
  const format = (value: number) => value.toLocaleString("en-GB");

  return (
    <nav aria-label="Pagination" className={cn("flex flex-wrap items-center justify-between gap-x-4 gap-y-2 text-sm text-muted-foreground", className)}>
      <div className="flex items-center gap-3">
        <span className="tabular-nums" aria-live="polite">
          {format(range.from)}–{format(range.to)} of {format(total)}
        </span>
        <Select value={String(pageSize)} onValueChange={(value) => onChange({ page: 1, pageSize: Number(value) })}>
          <SelectTrigger size="sm" aria-label={`${unit} per page`}>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {sizes.map((size) => (
              <SelectItem key={size} value={String(size)}>
                {size} per page
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <div className="flex items-center gap-1">
        <StepButton label="First page" className="hidden sm:inline-flex" disabled={page <= 1} onClick={() => go(1)}>
          <ChevronsLeft />
        </StepButton>
        <StepButton label="Previous page" disabled={page <= 1} onClick={() => go(page - 1)}>
          <ChevronLeft />
        </StepButton>
        <span className="px-2 tabular-nums sm:hidden">
          {format(Math.min(page, count))} / {format(count)}
        </span>
        {pageSlots(Math.min(page, count), count).map((slot, index) =>
          slot === "gap" ? (
            <span key={`gap-${index}`} className="hidden w-6 text-center select-none sm:inline" aria-hidden>
              …
            </span>
          ) : (
            <Button
              key={slot}
              variant={slot === page ? "default" : "ghost"}
              size="sm"
              className="hidden h-8 min-w-8 px-2 tabular-nums sm:inline-flex"
              aria-label={`Page ${slot}`}
              aria-current={slot === page ? "page" : undefined}
              onClick={() => go(slot)}
            >
              {slot}
            </Button>
          ),
        )}
        <StepButton label="Next page" disabled={page >= count} onClick={() => go(page + 1)}>
          <ChevronRight />
        </StepButton>
        <StepButton label="Last page" className="hidden sm:inline-flex" disabled={page >= count} onClick={() => go(count)}>
          <ChevronsRight />
        </StepButton>
      </div>
    </nav>
  );
}
