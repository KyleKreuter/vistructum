import { AlertTriangle, Loader2 } from "lucide-react";
import type { ReactNode } from "react";
import { errorMessage } from "@/api/client";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

export function PageSpinner({ className }: { className?: string }) {
  return (
    <div className={cn("flex min-h-40 items-center justify-center text-muted-foreground", className)} role="status">
      <Loader2 className="size-5 animate-spin" />
      <span className="sr-only">Loading</span>
    </div>
  );
}

export function ErrorState({ error, onRetry, className }: { error: unknown; onRetry?: () => void; className?: string }) {
  return (
    <div className={cn("flex flex-col items-center justify-center gap-3 rounded-lg border border-dashed p-8 text-center", className)}>
      <AlertTriangle className="size-5 text-destructive" />
      <p className="text-sm text-muted-foreground">{errorMessage(error)}</p>
      {onRetry && (
        <Button variant="outline" size="sm" onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  );
}

export function EmptyState({ title, children, className }: { title: string; children?: ReactNode; className?: string }) {
  return (
    <div className={cn("flex flex-col items-center justify-center gap-1 rounded-lg border border-dashed p-8 text-center", className)}>
      <p className="text-sm font-medium">{title}</p>
      {children && <div className="text-sm text-muted-foreground">{children}</div>}
    </div>
  );
}

export function PageHeader({ title, description, actions }: { title: ReactNode; description?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-3">
      <div className="min-w-0">
        <h1 className="text-xl font-semibold tracking-tight">{title}</h1>
        {description && <p className="text-sm text-muted-foreground">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  );
}
