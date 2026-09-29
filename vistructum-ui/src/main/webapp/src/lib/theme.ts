import { useSyncExternalStore } from "react";

export type ThemePreference = "light" | "dark" | "system";

const storageKey = "vistructum-theme";
const listeners = new Set<() => void>();

function readPreference(): ThemePreference {
  try {
    const value = localStorage.getItem(storageKey);
    return value === "light" || value === "dark" ? value : "system";
  } catch {
    return "system";
  }
}

function systemDark() {
  return typeof matchMedia === "function" && matchMedia("(prefers-color-scheme: dark)").matches;
}

export function resolvedTheme(preference: ThemePreference): "light" | "dark" {
  if (preference === "system") return systemDark() ? "dark" : "light";
  return preference;
}

function apply() {
  document.documentElement.classList.toggle("dark", resolvedTheme(readPreference()) === "dark");
  listeners.forEach((listener) => listener());
}

export function setThemePreference(preference: ThemePreference) {
  try {
    if (preference === "system") localStorage.removeItem(storageKey);
    else localStorage.setItem(storageKey, preference);
  } catch {
    document.documentElement.classList.toggle("dark", resolvedTheme(preference) === "dark");
  }
  apply();
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  const media = typeof matchMedia === "function" ? matchMedia("(prefers-color-scheme: dark)") : null;
  media?.addEventListener("change", apply);
  return () => {
    listeners.delete(listener);
    media?.removeEventListener("change", apply);
  };
}

function snapshot() {
  return `${readPreference()}:${document.documentElement.classList.contains("dark") ? "dark" : "light"}`;
}

export function useTheme() {
  const value = useSyncExternalStore(subscribe, snapshot, () => "system:light");
  const [preference, resolved] = value.split(":") as [ThemePreference, "light" | "dark"];
  return { preference, resolved, setPreference: setThemePreference };
}
