import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight } from "lucide-react";
import type { ReactNode } from "react";
import type { Paging } from "@/api/types";
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { cn } from "@/lib/utils";
import { pageSizes } from "@/logic/filters";
import { pageCount, pageRange, pageSlots } from "@/logic/pagination";

function StepButton({ label, disabled, onClick, children }: { label: string; disabled: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <Button variant="outline" size="icon" className="size-8" aria-label={label} title={label} disabled={disabled} onClick={onClick}>
      {children}
    </Button>
  );
}

export function Pagination({
  paging,
  total,
  onChange,
  className,
}: {
  paging: Paging;
  total: number;
  onChange: (paging: Paging) => void;
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
          <SelectTrigger size="sm" aria-label="Findings per page">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {pageSizes.map((size) => (
              <SelectItem key={size} value={String(size)}>
                {size} per page
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <div className="flex items-center gap-1">
        <StepButton label="First page" disabled={page <= 1} onClick={() => go(1)}>
          <ChevronsLeft />
        </StepButton>
        <StepButton label="Previous page" disabled={page <= 1} onClick={() => go(page - 1)}>
          <ChevronLeft />
        </StepButton>
        {pageSlots(Math.min(page, count), count).map((slot, index) =>
          slot === "gap" ? (
            <span key={`gap-${index}`} className="w-6 text-center select-none" aria-hidden>
              …
            </span>
          ) : (
            <Button
              key={slot}
              variant={slot === page ? "default" : "ghost"}
              size="sm"
              className="h-8 min-w-8 px-2 tabular-nums"
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
        <StepButton label="Last page" disabled={page >= count} onClick={() => go(count)}>
          <ChevronsRight />
        </StepButton>
      </div>
    </nav>
  );
}
