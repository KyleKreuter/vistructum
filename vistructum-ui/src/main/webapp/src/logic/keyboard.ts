export type DetailAction = { type: "navigate"; delta: 1 | -1 } | { type: "playPause" };

export interface KeyInput {
  key: string;
  metaKey: boolean;
  ctrlKey: boolean;
  altKey: boolean;
  editable: boolean;
}

export function detailAction(input: KeyInput): DetailAction | null {
  if (input.metaKey || input.ctrlKey || input.altKey || input.editable) return null;
  switch (input.key) {
    case "ArrowRight":
      return { type: "navigate", delta: 1 };
    case "ArrowLeft":
      return { type: "navigate", delta: -1 };
    case " ":
      return { type: "playPause" };
    default:
      return null;
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
