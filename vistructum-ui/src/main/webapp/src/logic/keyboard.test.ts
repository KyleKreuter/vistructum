import { describe, expect, it } from "vitest";
import { detailAction, isEditableTarget } from "./keyboard";

const key = (value: string, extra: Partial<{ metaKey: boolean; ctrlKey: boolean; altKey: boolean; editable: boolean }> = {}) =>
  detailAction({ key: value, metaKey: false, ctrlKey: false, altKey: false, editable: false, ...extra });

describe("detail keyboard map", () => {
  it("maps the arrow keys to the neighbouring findings", () => {
    expect(key("ArrowRight")).toEqual({ type: "navigate", delta: 1 });
    expect(key("ArrowLeft")).toEqual({ type: "navigate", delta: -1 });
  });

  it("maps space to play and pause", () => {
    expect(key(" ")).toEqual({ type: "playPause" });
  });

  it("ignores every other key", () => {
    expect(["1", "2", "j", "k", "q", "h", "v", "c", "p", ",", ".", "[", "]"].map((value) => key(value))).toEqual(Array(13).fill(null));
  });

  it("ignores modified keys and editable targets", () => {
    expect(key("ArrowLeft", { metaKey: true })).toBeNull();
    expect(key(" ", { ctrlKey: true })).toBeNull();
    expect(key(" ", { editable: true })).toBeNull();
  });

  it("detects editable targets", () => {
    expect(isEditableTarget({ tagName: "INPUT", type: "text" } as unknown as EventTarget)).toBe(true);
    expect(isEditableTarget({ tagName: "INPUT", type: "checkbox" } as unknown as EventTarget)).toBe(false);
    expect(isEditableTarget({ tagName: "DIV", isContentEditable: true } as unknown as EventTarget)).toBe(true);
    expect(isEditableTarget({ tagName: "BUTTON", getAttribute: () => null } as unknown as EventTarget)).toBe(false);
    expect(isEditableTarget(null)).toBe(false);
  });
});
