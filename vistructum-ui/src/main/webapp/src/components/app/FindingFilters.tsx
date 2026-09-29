import { X } from "lucide-react";
import { useEffect, useState } from "react";
import type { FindingFilter, FindingState, Source } from "@/api/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { defaultFilter, states } from "@/logic/filters";
import { PlayerChip } from "./PlayerFace";

export function FindingFilters({
  filter,
  onChange,
  playerName,
  hidePlayer = false,
}: {
  filter: FindingFilter;
  onChange: (filter: FindingFilter) => void;
  playerName?: string | null;
  hidePlayer?: boolean;
}) {
  const [world, setWorld] = useState(filter.world);
  const [syncedWorld, setSyncedWorld] = useState(filter.world);
  if (syncedWorld !== filter.world) {
    setSyncedWorld(filter.world);
    setWorld(filter.world);
  }

  useEffect(() => {
    if (world.trim() === filter.world) return;
    const timer = window.setTimeout(() => onChange({ ...filter, world: world.trim() }), 400);
    return () => window.clearTimeout(timer);
  }, [world, filter, onChange]);

  const dirty = filter.source !== "" || filter.world !== "" || filter.since !== "" || (!hidePlayer && filter.player !== "") || filter.state !== defaultFilter.state;

  return (
    <div className="flex flex-wrap items-end gap-3">
      <div className="grid gap-1.5">
        <Label className="text-xs text-muted-foreground">State</Label>
        <ToggleGroup
          type="single"
          variant="outline"
          size="sm"
          value={filter.state}
          onValueChange={(value) => value && onChange({ ...filter, state: value as FindingState })}
        >
          {states.map((state) => (
            <ToggleGroupItem key={state.value} value={state.value} className="px-3">
              {state.label}
            </ToggleGroupItem>
          ))}
        </ToggleGroup>
      </div>
      <div className="grid gap-1.5">
        <Label className="text-xs text-muted-foreground" htmlFor="filter-source">
          Source
        </Label>
        <Select value={filter.source || "all"} onValueChange={(value) => onChange({ ...filter, source: value === "all" ? "" : (value as Source) })}>
          <SelectTrigger id="filter-source" size="sm" className="w-32">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All sources</SelectItem>
            <SelectItem value="mask">Live check</SelectItem>
            <SelectItem value="fullscan">Full scan</SelectItem>
          </SelectContent>
        </Select>
      </div>
      <div className="grid gap-1.5">
        <Label className="text-xs text-muted-foreground" htmlFor="filter-world">
          World
        </Label>
        <Input id="filter-world" className="h-8 w-36" placeholder="Any world" value={world} onChange={(event) => setWorld(event.target.value)} />
      </div>
      <div className="grid gap-1.5">
        <Label className="text-xs text-muted-foreground" htmlFor="filter-since">
          Since
        </Label>
        <Input id="filter-since" type="date" className="h-8 w-40" value={filter.since} onChange={(event) => onChange({ ...filter, since: event.target.value })} />
      </div>
      {!hidePlayer && filter.player && (
        <div className="grid gap-1.5">
          <Label className="text-xs text-muted-foreground">Player</Label>
          <div className="flex h-8 items-center gap-1 rounded-md border px-2">
            <PlayerChip player={{ uuid: filter.player, name: playerName ?? null }} />
            <Button variant="ghost" size="icon" className="size-6" aria-label="Clear player filter" onClick={() => onChange({ ...filter, player: "" })}>
              <X />
            </Button>
          </div>
        </div>
      )}
      {dirty && (
        <Button variant="ghost" size="sm" onClick={() => onChange({ ...defaultFilter, player: hidePlayer ? filter.player : "" })}>
          Reset
        </Button>
      )}
    </div>
  );
}
