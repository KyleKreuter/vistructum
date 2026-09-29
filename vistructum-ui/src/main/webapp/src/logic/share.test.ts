import { describe, expect, it } from "vitest";
import { shareView } from "./share";

const confirmed = { verdict: "CONFIRMED" as const, reviewer: "kyleonaut", reviewedAt: "2026-09-01T10:00:00Z" };
const falseAlarm = { verdict: "FALSE_ALARM" as const, reviewer: "kyleonaut", reviewedAt: "2026-09-01T10:00:00Z" };
const url = "https://example.test/review/e/abc";

describe("shareView", () => {
  it("shows an active link to every signed-in reviewer", () => {
    expect(shareView({ shareUrl: url, review: confirmed, hasEvidence: true }, false)).toBe("link");
    expect(shareView({ shareUrl: url, review: confirmed, hasEvidence: true }, true)).toBe("link");
  });

  it("keeps an active link visible after the verdict changed", () => {
    expect(shareView({ shareUrl: url, review: falseAlarm, hasEvidence: true }, true)).toBe("link");
    expect(shareView({ shareUrl: url, review: null, hasEvidence: true }, false)).toBe("link");
  });

  it("offers activation only for confirmed findings with evidence to sharers", () => {
    expect(shareView({ shareUrl: null, review: confirmed, hasEvidence: true }, true)).toBe("activate");
    expect(shareView({ shareUrl: null, review: confirmed, hasEvidence: true }, false)).toBe("hidden");
    expect(shareView({ shareUrl: null, review: confirmed, hasEvidence: false }, true)).toBe("hidden");
    expect(shareView({ shareUrl: null, review: falseAlarm, hasEvidence: true }, true)).toBe("hidden");
    expect(shareView({ shareUrl: null, review: null, hasEvidence: true }, true)).toBe("hidden");
  });
});
