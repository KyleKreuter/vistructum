import type { Verdict } from "@/api/types";

export type DetailAction =
  | { type: "verdict"; verdict: Verdict }
  | { type: "navigate"; delta: 1 | -1 }
  | { type: "nextOpen" }
  | { type: "layer"; index: number }
  | { type: "toggleHeatmap" }
  | { type: "toggle3d" }
  | { type: "copyTeleport" }
  | { type: "playPause" }
  | { type: "step"; delta: 1 | -1 }
  | { type: "speed"; delta: 1 | -1 };

export interface KeyInput {
  key: string;
  metaKey: boolean;
  ctrlKey: boolean;
  altKey: boolean;
  editable: boolean;
}

const layerKeys = ["q", "w", "e", "r", "t"];

export function detailAction(input: KeyInput): DetailAction | null {
  if (input.metaKey || input.ctrlKey || input.altKey || input.editable) return null;
  const key = input.key.length === 1 ? input.key.toLowerCase() : input.key;
  switch (key) {
    case "1":
      return { type: "verdict", verdict: "CONFIRMED" };
    case "2":
      return { type: "verdict", verdict: "FALSE_ALARM" };
    case "ArrowRight":
    case "j":
      return { type: "navigate", delta: 1 };
    case "ArrowLeft":
    case "k":
      return { type: "navigate", delta: -1 };
    case " ":
      return { type: "nextOpen" };
    case "h":
      return { type: "toggleHeatmap" };
    case "v":
      return { type: "toggle3d" };
    case "c":
      return { type: "copyTeleport" };
    case "p":
      return { type: "playPause" };
    case ",":
      return { type: "step", delta: -1 };
    case ".":
      return { type: "step", delta: 1 };
    case "[":
      return { type: "speed", delta: -1 };
    case "]":
      return { type: "speed", delta: 1 };
    default: {
      const index = layerKeys.indexOf(key);
      return index >= 0 ? { type: "layer", index } : null;
    }
  }
}

export function isEditableTarget(target: EventTarget | null): boolean {
  if (!target || typeof target !== "object") return false;
  const element = target as { tagName?: string; isContentEditable?: boolean; type?: string; getAttribute?: (name: string) => string | null };
  if (element.isContentEditable) return true;
  const tag = element.tagName?.toUpperCase();
  if (tag === "TEXTAREA" || tag === "SELECT") return true;
  if (tag === "INPUT") return !["checkbox", "radio", "range", "button"].includes(element.type ?? "text");
  const role = element.getAttribute?.("role");
  return role === "combobox" || role === "listbox" || role === "option" || role === "menuitem";
}

export const shortcutHelp: { keys: string[]; label: string }[] = [
  { keys: ["1"], label: "Confirm" },
  { keys: ["2"], label: "False alarm" },
  { keys: ["←", "→"], label: "Previous / next" },
  { keys: ["J", "K"], label: "Next / previous" },
  { keys: ["Space"], label: "Next open" },
  { keys: ["Q", "W", "E", "R", "T"], label: "Layers" },
  { keys: ["H"], label: "Heatmap overlay" },
  { keys: ["V"], label: "3D view" },
  { keys: ["C"], label: "Copy teleport" },
  { keys: ["P"], label: "Play / pause" },
  { keys: [",", "."], label: "Step block" },
  { keys: ["[", "]"], label: "Speed" },
];
