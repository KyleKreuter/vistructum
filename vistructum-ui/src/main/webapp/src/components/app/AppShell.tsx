import { useQueryClient } from "@tanstack/react-query";
import { LogOut } from "lucide-react";
import type { ReactNode } from "react";
import { NavLink, useMatch } from "react-router";
import type { Me } from "@/api/types";
import { useLogout } from "@/api/queries";
import { Button } from "@/components/ui/button";
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger } from "@/components/ui/dropdown-menu";
import { formatDateTime } from "@/logic/format";
import { cn } from "@/lib/utils";
import { PlayerFace } from "./PlayerFace";
import { Logo } from "./Logo";
import { ThemeToggle } from "./ThemeToggle";

const links = [
  { to: "/findings?state=open", match: "/findings", label: "Findings" },
  { to: "/stats", match: "/stats", label: "Stats" },
  { to: "/status", match: "/status", label: "Status" },
  { to: "/activity", match: "/activity", label: "Activity" },
];

export function AppShell({ me, children }: { me: Me; children: ReactNode }) {
  const logout = useLogout();
  const client = useQueryClient();
  const fixed = useMatch({ path: "/findings", end: true }) !== null;
  return (
    <div className={cn("flex flex-col", fixed ? "min-h-screen md:h-dvh md:min-h-0 md:overflow-hidden" : "min-h-screen")}>
      <header className="sticky top-0 z-30 shrink-0 border-b bg-background/90 backdrop-blur supports-[backdrop-filter]:bg-background/75">
        <div className="mx-auto flex h-14 max-w-[1600px] items-center gap-4 px-4">
          <NavLink to="/findings?state=open" className="flex shrink-0 items-center rounded-sm" aria-label="Vistructum findings">
            <Logo />
          </NavLink>
          <nav className="flex items-center gap-1 overflow-x-auto" aria-label="Main">
            {links.map((link) => (
              <NavLink
                key={link.label}
                to={link.to}
                className={({ isActive }) =>
                  cn(
                    "rounded-md px-3 py-1.5 text-sm font-medium text-muted-foreground transition-colors hover:bg-accent hover:text-foreground",
                    isActive && "bg-accent text-foreground",
                  )
                }
                end={false}
              >
                {link.label}
              </NavLink>
            ))}
          </nav>
          <div className="ml-auto flex items-center gap-1">
            <ThemeToggle />
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" className="gap-2 px-2">
                  <PlayerFace uuid={me.player} name={me.name} size={22} />
                  <span className="hidden max-w-32 truncate md:inline">{me.name}</span>
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="w-60">
                <DropdownMenuLabel className="font-normal">
                  <div className="font-medium">{me.name}</div>
                  <div className="text-xs text-muted-foreground">Session until {formatDateTime(me.expiresAt)}</div>
                  <div className="text-xs text-muted-foreground">{me.canShare ? "May share evidence" : "Cannot share evidence"}</div>
                </DropdownMenuLabel>
                <DropdownMenuSeparator />
                <DropdownMenuItem
                  onSelect={() =>
                    logout.mutate(undefined, {
                      onSettled: () => {
                        client.clear();
                        window.location.assign(import.meta.env.BASE_URL);
                      },
                    })
                  }
                >
                  <LogOut /> Sign out
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </div>
      </header>
      <main className={cn("mx-auto w-full max-w-[1600px] flex-1 px-4 py-5", fixed && "flex flex-col md:min-h-0")}>{children}</main>
    </div>
  );
}
