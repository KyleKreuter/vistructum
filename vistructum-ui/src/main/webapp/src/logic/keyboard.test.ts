import { describe, expect, it } from "vitest";
import { detailAction, isEditableTarget } from "./keyboard";

const key = (value: string, extra: Partial<{ metaKey: boolean; ctrlKey: boolean; altKey: boolean; editable: boolean }> = {}) =>
  detailAction({ key: value, metaKey: false, ctrlKey: false, altKey: false, editable: false, ...extra });

describe("detail keyboard map", () => {
  it("maps verdict keys", () => {
    expect(key("1")).toEqual({ type: "verdict", verdict: "CONFIRMED" });
    expect(key("2")).toEqual({ type: "verdict", verdict: "FALSE_ALARM" });
    expect(key("3")).toBeNull();
  });

  it("maps navigation keys", () => {
    expect(key("ArrowRight")).toEqual({ type: "navigate", delta: 1 });
    expect(key("ArrowLeft")).toEqual({ type: "navigate", delta: -1 });
    expect(key("j")).toEqual({ type: "navigate", delta: 1 });
    expect(key("K")).toEqual({ type: "navigate", delta: -1 });
    expect(key(" ")).toEqual({ type: "nextOpen" });
  });

  it("maps layers and views", () => {
    expect(["q", "w", "e", "r", "t"].map((k) => key(k))).toEqual([0, 1, 2, 3, 4].map((index) => ({ type: "layer", index })));
    expect(key("h")).toEqual({ type: "toggleHeatmap" });
    expect(key("v")).toEqual({ type: "toggle3d" });
    expect(key("c")).toEqual({ type: "copyTeleport" });
  });

  it("maps replay keys", () => {
    expect(key("p")).toEqual({ type: "playPause" });
    expect(key(",")).toEqual({ type: "step", delta: -1 });
    expect(key(".")).toEqual({ type: "step", delta: 1 });
    expect(key("[")).toEqual({ type: "speed", delta: -1 });
    expect(key("]")).toEqual({ type: "speed", delta: 1 });
  });

  it("ignores modified keys and editable targets", () => {
    expect(key("c", { metaKey: true })).toBeNull();
    expect(key("1", { ctrlKey: true })).toBeNull();
    expect(key("1", { editable: true })).toBeNull();
  });

  it("detects editable targets", () => {
    expect(isEditableTarget({ tagName: "INPUT", type: "text" } as unknown as EventTarget)).toBe(true);
    expect(isEditableTarget({ tagName: "INPUT", type: "checkbox" } as unknown as EventTarget)).toBe(false);
    expect(isEditableTarget({ tagName: "DIV", isContentEditable: true } as unknown as EventTarget)).toBe(true);
    expect(isEditableTarget({ tagName: "BUTTON", getAttribute: () => null } as unknown as EventTarget)).toBe(false);
    expect(isEditableTarget(null)).toBe(false);
  });
});
