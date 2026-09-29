import { FlaskConical, X } from "lucide-react";
import { useState } from "react";
import { cn } from "@/lib/utils";
import { defaultSettings, endpointKeys, failureStatuses, latencies, saveSettings, scenarios, type EndpointKey, type FailureStatus, type Failures, type MockSettings } from "./settings";

const settingParams = ["scenario", "latency", "fail"];

function applySettings(settings: MockSettings) {
  saveSettings(settings);
  const url = new URL(location.href);
  settingParams.forEach((param) => url.searchParams.delete(param));
  location.assign(url.toString());
}

function summary(settings: MockSettings): string {
  const failures = Object.keys(settings.failures).length;
  return [
    settings.scenario,
    settings.latency === null ? null : `${settings.latency} ms`,
    failures === 0 ? null : `${failures} ${failures === 1 ? "failure" : "failures"}`,
  ]
    .filter((part) => part !== null)
    .join(" · ");
}

function Choice({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "rounded-md border px-2 py-1 text-xs transition-colors",
        active ? "border-primary bg-primary text-primary-foreground" : "bg-background text-muted-foreground hover:text-foreground",
      )}
    >
      {children}
    </button>
  );
}

export function DevMenu({ settings }: { settings: MockSettings }) {
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState(settings);
  const changed = JSON.stringify(draft) !== JSON.stringify(settings);

  const setFailure = (key: EndpointKey, value: string) => {
    const failures: Failures = Object.fromEntries(Object.entries(draft.failures).filter(([entry]) => entry !== key));
    if (value !== "") failures[key] = Number(value) as FailureStatus;
    setDraft({ ...draft, failures });
  };

  if (!open) {
    return (
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="fixed bottom-3 left-1/2 z-[100] flex -translate-x-1/2 items-center gap-1.5 rounded-full border bg-popover/95 px-3 py-1.5 text-xs text-popover-foreground shadow-lg backdrop-blur hover:border-ring"
        aria-label="Open the mock menu"
      >
        <FlaskConical className="size-3.5 text-primary" />
        Mock · {summary(settings)}
      </button>
    );
  }

  return (
    <div className="fixed bottom-3 left-3 z-[100] flex max-h-[calc(100dvh-1.5rem)] w-80 flex-col gap-3 overflow-y-auto rounded-lg border bg-popover p-3 text-sm text-popover-foreground shadow-xl">
      <div className="flex items-center gap-2">
        <FlaskConical className="size-4 text-primary" />
        <span className="font-medium">Mock data</span>
        <button type="button" onClick={() => setOpen(false)} className="ml-auto rounded-sm p-0.5 text-muted-foreground hover:text-foreground" aria-label="Close the mock menu">
          <X className="size-4" />
        </button>
      </div>
      <div className="space-y-1.5">
        <div className="text-xs text-muted-foreground">Scenario</div>
        <div className="flex flex-wrap gap-1">
          {scenarios.map((scenario) => (
            <Choice key={scenario} active={draft.scenario === scenario} onClick={() => setDraft({ ...draft, scenario })}>
              {scenario}
            </Choice>
          ))}
        </div>
      </div>
      <div className="space-y-1.5">
        <div className="text-xs text-muted-foreground">Latency</div>
        <div className="flex flex-wrap gap-1">
          <Choice active={draft.latency === null} onClick={() => setDraft({ ...draft, latency: null })}>
            built-in
          </Choice>
          {latencies.map((latency) => (
            <Choice key={latency} active={draft.latency === latency} onClick={() => setDraft({ ...draft, latency })}>
              {latency} ms
            </Choice>
          ))}
        </div>
      </div>
      <div className="space-y-1.5">
        <div className="text-xs text-muted-foreground">Failing endpoints</div>
        <div className="grid grid-cols-2 gap-x-3 gap-y-1">
          {endpointKeys.map((key) => (
            <label key={key} className="flex items-center justify-between gap-2 text-xs">
              <span className={cn(draft.failures[key] !== undefined ? "text-destructive" : "text-muted-foreground")}>{key}</span>
              <select
                value={draft.failures[key] ?? ""}
                onChange={(event) => setFailure(key, event.target.value)}
                className="h-6 rounded border bg-background px-1 text-xs text-foreground"
              >
                <option value="">ok</option>
                {failureStatuses.map((status) => (
                  <option key={status} value={status}>
                    {status}
                  </option>
                ))}
              </select>
            </label>
          ))}
        </div>
      </div>
      <div className="flex gap-2">
        <button
          type="button"
          onClick={() => applySettings(draft)}
          disabled={!changed}
          className="flex-1 rounded-md bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground disabled:opacity-50"
        >
          Apply and reload
        </button>
        <button type="button" onClick={() => applySettings(defaultSettings)} className="rounded-md border px-3 py-1.5 text-xs hover:bg-accent">
          Reset
        </button>
      </div>
    </div>
  );
}
