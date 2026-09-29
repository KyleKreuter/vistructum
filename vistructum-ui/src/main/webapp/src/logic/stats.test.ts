import { describe, expect, it } from "vitest";
import { boxCentre, boxSize, localPosition } from "./coords";
import { dayRange, formatRelative, precision } from "./format";
import { dayRows, rangeBounds, reviewerRows, totals } from "./stats";

describe("stats", () => {
  it("fills missing days and splits by source", () => {
    const rows = dayRows(
      {
        days: [
          { day: "2026-09-02", source: "mask", created: 3, confirmed: 1, falseAlarms: 1 },
          { day: "2026-09-02", source: "fullscan", created: 2, confirmed: 0, falseAlarms: 2 },
        ],
      },
      new Date("2026-09-01T10:00:00Z"),
      new Date("2026-09-03T10:00:00Z"),
    );
    expect(rows.map((row) => row.day)).toEqual(["2026-09-01", "2026-09-02", "2026-09-03"]);
    expect(rows[1]).toEqual({ day: "2026-09-02", mask: 3, fullscan: 2, confirmed: 1, falseAlarms: 3 });
    expect(totals(rows)).toEqual({ created: 5, confirmed: 1, falseAlarms: 3 });
  });

  it("computes range bounds", () => {
    const { from } = rangeBounds("7d", new Date("2026-09-29T12:00:00Z"));
    expect(from.toISOString()).toBe("2026-09-23T00:00:00.000Z");
    expect(dayRange(from, new Date("2026-09-29T12:00:00Z"))).toHaveLength(7);
  });

  it("sorts reviewers by volume", () => {
    const rows = reviewerRows({ reviewers: [{ reviewer: "b", confirmed: 1, falseAlarms: 0 }, { reviewer: "a", confirmed: 2, falseAlarms: 2 }] });
    expect(rows.map((row) => row.reviewer)).toEqual(["a", "b"]);
    expect(rows[0].precision).toBe(0.5);
    expect(precision(0, 0)).toBeNull();
  });

  it("formats relative times", () => {
    const now = Date.parse("2026-09-29T12:00:00Z");
    expect(formatRelative("2026-09-29T11:59:50Z", now)).toBe("just now");
    expect(formatRelative("2026-09-29T11:30:00Z", now)).toBe("30 min ago");
    expect(formatRelative("2026-09-27T12:00:00Z", now)).toBe("2 d ago");
  });
});

describe("coordinates", () => {
  const box = { minX: -10, minY: 60, minZ: 5, maxX: -4, maxY: 60, maxZ: 11 };

  it("computes box centre and size", () => {
    expect(boxCentre(box)).toEqual({ x: -7, y: 60, z: 8 });
    expect(boxSize(box)).toEqual({ x: 7, y: 1, z: 7 });
  });

  it("converts world to local coordinates", () => {
    expect(localPosition({ minX: -10, minY: 56, minZ: 1 }, -9.5, 64, 3.25)).toEqual([0.5, 8, 2.25]);
  });
});
