import type { ReactNode } from "react";
import { cn } from "@/lib/utils";
import { Logo } from "./Logo";
import { ThemeToggle } from "./ThemeToggle";

export function PlainShell({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div className="flex min-h-svh flex-col bg-background">
      <header className="border-b">
        <div className="mx-auto flex h-14 max-w-6xl items-center gap-3 px-4">
          <Logo />
          <div className="ml-auto">
            <ThemeToggle />
          </div>
        </div>
      </header>
      <main className={cn("mx-auto w-full max-w-6xl flex-1 px-4 py-6", className)}>{children}</main>
    </div>
  );
}
