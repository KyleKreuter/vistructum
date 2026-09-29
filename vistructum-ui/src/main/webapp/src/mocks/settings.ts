export const scenarios = ["default", "empty", "sparse", "stress"] as const;
export type Scenario = (typeof scenarios)[number];

export const latencies = [0, 800, 3000] as const;

export const endpointKeys = [
  "me",
  "logout",
  "status",
  "findings",
  "finding",
  "thumbnail",
  "scene",
  "heatmap",
  "terrain",
  "evidence",
  "verdict",
  "share",
  "stats",
  "activity",
  "players",
  "skin",
  "face",
  "palette",
  "public",
  "assets",
] as const;
export type EndpointKey = (typeof endpointKeys)[number];

export const failureStatuses = [401, 403, 404, 409, 500, 503] as const;
export type FailureStatus = (typeof failureStatuses)[number];

export type Failures = Partial<Record<EndpointKey, FailureStatus>>;

export interface MockSettings {
  scenario: Scenario;
  latency: number | null;
  failures: Failures;
}

export const defaultSettings: MockSettings = { scenario: "default", latency: null, failures: {} };

const storageKey = "vistructum-mock-settings";

function isScenario(value: string): value is Scenario {
  return (scenarios as readonly string[]).includes(value);
}

function isEndpointKey(value: string): value is EndpointKey {
  return (endpointKeys as readonly string[]).includes(value);
}

function isFailureStatus(value: number): value is FailureStatus {
  return (failureStatuses as readonly number[]).includes(value);
}

export function parseScenario(value: string | null): Scenario {
  const trimmed = (value ?? "").trim().toLowerCase();
  return isScenario(trimmed) ? trimmed : "default";
}

export function parseLatency(value: string | null): number | null {
  const trimmed = (value ?? "").trim();
  if (trimmed === "") return null;
  const parsed = Number(trimmed);
  return Number.isInteger(parsed) && parsed >= 0 && parsed <= 60_000 ? parsed : null;
}

export function parseFailures(value: string | null): Failures {
  const failures: Failures = {};
  for (const part of (value ?? "").split(",")) {
    const [key, status] = part.trim().split(":");
    const code = Number(status ?? "500");
    if (key && isEndpointKey(key) && isFailureStatus(code)) failures[key] = code;
  }
  return failures;
}

export function formatFailures(failures: Failures): string {
  return endpointKeys
    .flatMap((key) => {
      const status = failures[key];
      return status === undefined ? [] : [`${key}:${status}`];
    })
    .join(",");
}

export function settingsQuery(settings: MockSettings): URLSearchParams {
  const params = new URLSearchParams({ scenario: settings.scenario });
  if (settings.latency !== null) params.set("latency", String(settings.latency));
  const failures = formatFailures(settings.failures);
  if (failures) params.set("fail", failures);
  return params;
}

function fromStorage(): MockSettings {
  try {
    const raw = sessionStorage.getItem(storageKey);
    if (!raw) return defaultSettings;
    const params = new URLSearchParams(raw);
    return { scenario: parseScenario(params.get("scenario")), latency: parseLatency(params.get("latency")), failures: parseFailures(params.get("fail")) };
  } catch {
    return defaultSettings;
  }
}

export function saveSettings(settings: MockSettings) {
  try {
    sessionStorage.setItem(storageKey, settingsQuery(settings).toString());
  } catch {
    return;
  }
}

export function resolveSettings(params: URLSearchParams, stored: MockSettings): MockSettings {
  return {
    scenario: params.has("scenario") ? parseScenario(params.get("scenario")) : stored.scenario,
    latency: params.has("latency") ? parseLatency(params.get("latency")) : stored.latency,
    failures: params.has("fail") ? parseFailures(params.get("fail")) : stored.failures,
  };
}

let active: MockSettings = defaultSettings;

export function loadSettings(search: string): MockSettings {
  active = resolveSettings(new URLSearchParams(search), fromStorage());
  saveSettings(active);
  return active;
}

export function activeSettings(): MockSettings {
  return active;
}
