import { delay, http, HttpResponse } from "msw";
import type { ActivityItem, AssetStatus, FindingState, FindingSummary, ScanJob, Stats, Status, Verdict } from "@/api/types";
import { matchesState } from "@/logic/filters";
import { paintLayer, sceneColours } from "@/logic/sceneLayers";
import { publicUrl, type MockDb, type MockFinding } from "./data";
import { relativeEvidence } from "./evidence";
import { facePng, imagePng, lookFor, skinPng } from "./skins";

const api = "/review/api";

const signedOutKey = "vistructum-mock-signed-out";

export function mockSignedOut(): boolean {
  try {
    return sessionStorage.getItem(signedOutKey) === "1";
  } catch {
    return false;
  }
}

export function setMockSignedOut(value: boolean) {
  try {
    if (value) sessionStorage.setItem(signedOutKey, "1");
    else sessionStorage.removeItem(signedOutKey);
  } catch {
    return;
  }
}

const error = (status: number, code: string) => HttpResponse.json({ error: code }, { status });

function summary(finding: MockFinding): FindingSummary {
  return {
    id: finding.id,
    source: finding.source,
    world: finding.world,
    box: finding.box,
    score: finding.score,
    votes: finding.votes,
    detail: finding.detail,
    modelVersion: finding.modelVersion,
    createdAt: finding.createdAt,
    players: finding.players,
    review: finding.review,
    hasEvidence: finding.hasEvidence,
    sharedSince: finding.sharedSince,
    shareUrl: finding.shareActive && finding.shareToken ? publicUrl(finding.shareToken) : null,
  };
}

function csrfOk(request: Request): boolean {
  const cookie = document.cookie.split(";").map((part) => part.trim()).find((part) => part.startsWith("vistructum_csrf="));
  const expected = cookie ? decodeURIComponent(cookie.slice("vistructum_csrf=".length)) : "";
  return expected !== "" && request.headers.get("X-Vistructum-Csrf") === expected;
}

function png(blob: Blob, headers: Record<string, string> = {}) {
  return new HttpResponse(blob, { headers: { "Content-Type": "image/png", ...headers } });
}

function sharedFinding(db: MockDb, shareToken: string): MockFinding | undefined {
  const id = db.shares.get(shareToken);
  const finding = id === undefined ? undefined : db.findings.find((entry) => entry.id === id);
  return finding?.shareActive ? finding : undefined;
}

function token(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  return [...bytes].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

export function handlers(db: MockDb) {
  const byId = (id: string | readonly string[] | undefined) => db.findings.find((finding) => finding.id === Number(id));
  const knownPlayer = (uuid: string) => db.players.some((player) => player.uuid === uuid);
  const guard = () => (mockSignedOut() ? error(401, "unauthorized") : null);

  return [
    http.get(`${api}/me`, async () => {
      await delay(120);
      return guard() ?? HttpResponse.json(db.me);
    }),

    http.post(`${api}/logout`, async () => {
      setMockSignedOut(true);
      return new HttpResponse(null, { status: 204 });
    }),

    http.get(`${api}/status`, async () => {
      await delay(80);
      const denied = guard();
      if (denied) return denied;
      const now = Date.now();
      const scans = db.scans.map((scan): ScanJob => {
        const elapsed = now - scan.startedAt;
        const running = elapsed >= 0;
        const doneTiles = running ? Math.floor(elapsed / scan.tileMs) % (scan.totalTiles + 1) : 0;
        return {
            id: scan.id,
            world: scan.world,
            cause: scan.cause,
            status: running ? "RUNNING" : "QUEUED",
            doneTiles,
            totalTiles: scan.totalTiles,
            startedAt: new Date(Math.min(now, scan.startedAt)).toISOString(),
            findings: running ? scan.findings + Math.floor(doneTiles / 500) : 0,
            failures: running && doneTiles > 900 ? 1 : 0,
        };
      });
      const status: Status = {
        trackedChanges: 1843 + Math.floor((now / 1000) % 60),
        openFindings: db.findings.filter((finding) => !finding.review).length,
        inference: {
          mode: "LOCAL",
          available: true,
          models: [
            { kind: "fullscan", version: "scan-v4" },
            { kind: "mask", version: "bf-scan-3" },
          ],
          detail: null,
        },
        scans,
        recordingEnabled: true,
        textures: { enabled: true, available: !!import.meta.env.VITE_ASSETS_TARGET, version: null },
      };
      return HttpResponse.json(status);
    }),

    http.get(`${api}/findings`, async ({ request }) => {
      await delay(180);
      const denied = guard();
      if (denied) return denied;
      const url = new URL(request.url);
      const state = (url.searchParams.get("state") ?? "open") as FindingState;
      const source = url.searchParams.get("source");
      const world = url.searchParams.get("world");
      const player = url.searchParams.get("player");
      const since = url.searchParams.get("since");
      const before = url.searchParams.get("before");
      const limit = Math.max(1, Math.min(100, Number(url.searchParams.get("limit") ?? 50)));
      if (!["open", "reviewed", "confirmed", "false_alarm", "any"].includes(state)) return error(400, "bad_request");
      const matching = db.findings.filter(
        (finding) =>
          matchesState(state, finding.review?.verdict ?? null) &&
          (!source || finding.source === source) &&
          (!world || finding.world === world) &&
          (!player || finding.players.some((entry) => entry.uuid === player)) &&
          (!since || finding.createdAt >= since),
      );
      const page = matching.filter((finding) => !before || finding.id < Number(before));
      const items = page.slice(0, limit);
      return HttpResponse.json({
        items: items.map(summary),
        nextBefore: page.length > limit ? items[items.length - 1].id : null,
        total: matching.length,
      });
    }),

    http.get(`${api}/findings/:id`, async ({ params }) => {
      await delay(120);
      const denied = guard();
      if (denied) return denied;
      const finding = byId(params.id);
      return finding ? HttpResponse.json({ ...summary(finding), teleport: finding.teleport }) : error(404, "not_found");
    }),

    http.get(`${api}/findings/:id/thumbnail.png`, async ({ params }) => {
      const denied = guard();
      if (denied) return denied;
      const finding = byId(params.id);
      if (!finding?.scene || !finding.thumbnail) return error(404, "not_found");
      const pixels = paintLayer(finding.scene, sceneColours(finding.scene, db.palette), "colour", { span: 4, heatmap: null, overlay: false });
      return png(await imagePng(finding.scene.width, finding.scene.height, pixels, 1));
    }),

    http.get(`${api}/findings/:id/scene`, async ({ params }) => {
      await delay(220);
      const denied = guard();
      if (denied) return denied;
      const finding = byId(params.id);
      return finding?.scene ? HttpResponse.json(finding.scene) : error(404, "not_found");
    }),

    http.get(`${api}/findings/:id/heatmap`, async ({ params }) => {
      await delay(1400);
      const denied = guard();
      if (denied) return denied;
      const finding = byId(params.id);
      if (!finding) return error(404, "not_found");
      return finding.heatmap ? HttpResponse.json(finding.heatmap) : error(503, "unavailable");
    }),

    http.get(`${api}/findings/:id/evidence`, async ({ params }) => {
      await delay(300);
      const denied = guard();
      if (denied) return denied;
      const finding = byId(params.id);
      return finding?.evidence ? HttpResponse.json(finding.evidence) : error(404, "not_found");
    }),

    http.post(`${api}/findings/:id/verdict`, async ({ params, request }) => {
      await delay(150);
      const denied = guard();
      if (denied) return denied;
      if (!csrfOk(request)) return error(403, "csrf");
      const finding = byId(params.id);
      if (!finding) return error(404, "not_found");
      const body = (await request.json()) as { verdict?: Verdict };
      if (body.verdict !== "CONFIRMED" && body.verdict !== "FALSE_ALARM") return error(400, "bad_request");
      const at = new Date().toISOString();
      finding.review = { verdict: body.verdict, reviewer: db.me.name, reviewedAt: at };
      db.activity.unshift({ at, actor: db.me.name, kind: body.verdict, findingId: finding.id });
      return HttpResponse.json(summary(finding));
    }),

    http.post(`${api}/findings/:id/share`, async ({ params, request }) => {
      await delay(200);
      const denied = guard();
      if (denied) return denied;
      if (!csrfOk(request)) return error(403, "csrf");
      if (!db.me.canShare) return error(403, "forbidden");
      const finding = byId(params.id);
      if (!finding) return error(404, "not_found");
      if (finding.review?.verdict !== "CONFIRMED" || !finding.evidence) return error(409, "not_shareable");
      if (finding.shareActive && finding.shareToken && finding.sharedSince) {
        return HttpResponse.json({ url: publicUrl(finding.shareToken), sharedSince: finding.sharedSince });
      }
      const shareToken = finding.shareToken ?? token();
      finding.shareToken = shareToken;
      finding.shareActive = true;
      finding.sharedSince = new Date().toISOString();
      db.shares.set(shareToken, finding.id);
      db.activity.unshift({ at: finding.sharedSince, actor: db.me.name, kind: "SHARED", findingId: finding.id });
      return HttpResponse.json({ url: publicUrl(shareToken), sharedSince: finding.sharedSince });
    }),

    http.delete(`${api}/findings/:id/share`, async ({ params, request }) => {
      await delay(150);
      const denied = guard();
      if (denied) return denied;
      if (!csrfOk(request)) return error(403, "csrf");
      const finding = byId(params.id);
      if (!finding) return error(404, "not_found");
      if (!db.me.canShare) return error(403, "forbidden");
      if (!finding.shareActive) return new HttpResponse(null, { status: 204 });
      finding.shareActive = false;
      finding.sharedSince = null;
      db.activity.unshift({ at: new Date().toISOString(), actor: db.me.name, kind: "UNSHARED", findingId: finding.id });
      return new HttpResponse(null, { status: 204 });
    }),

    http.get(`${api}/stats`, async ({ request }) => {
      await delay(200);
      const denied = guard();
      if (denied) return denied;
      const url = new URL(request.url);
      const from = url.searchParams.get("from") ?? "";
      const to = url.searchParams.get("to") ?? "￿";
      const days = new Map<string, Stats["days"][number]>();
      const bump = (day: string, source: "mask" | "fullscan", field: "created" | "confirmed" | "falseAlarms") => {
        const key = `${day}|${source}`;
        const row = days.get(key) ?? { day, source, created: 0, confirmed: 0, falseAlarms: 0 };
        row[field]++;
        days.set(key, row);
      };
      const reviewers = new Map<string, { reviewer: string; confirmed: number; falseAlarms: number }>();
      for (const finding of db.findings) {
        if (finding.createdAt >= from && finding.createdAt <= to) bump(finding.createdAt.slice(0, 10), finding.source, "created");
        const review = finding.review;
        if (review && review.reviewedAt >= from && review.reviewedAt <= to) {
          const confirmed = review.verdict === "CONFIRMED";
          bump(review.reviewedAt.slice(0, 10), finding.source, confirmed ? "confirmed" : "falseAlarms");
          const row = reviewers.get(review.reviewer) ?? { reviewer: review.reviewer, confirmed: 0, falseAlarms: 0 };
          if (confirmed) row.confirmed++;
          else row.falseAlarms++;
          reviewers.set(review.reviewer, row);
        }
      }
      const open = db.findings.filter((finding) => !finding.review);
      const precision = (["mask", "fullscan"] as const).map((source) => ({
        source,
        confirmed: db.findings.filter((finding) => finding.source === source && finding.review?.verdict === "CONFIRMED").length,
        falseAlarms: db.findings.filter((finding) => finding.source === source && finding.review?.verdict === "FALSE_ALARM").length,
      }));
      const stats: Stats = {
        days: [...days.values()].sort((a, b) => a.day.localeCompare(b.day)),
        reviewers: [...reviewers.values()],
        open: open.length,
        oldestOpen: open.reduce<string | null>((oldest, finding) => (!oldest || finding.createdAt < oldest ? finding.createdAt : oldest), null),
        precision,
      };
      return HttpResponse.json(stats);
    }),

    http.get(`${api}/activity`, async ({ request }) => {
      await delay(150);
      const denied = guard();
      if (denied) return denied;
      const url = new URL(request.url);
      const before = url.searchParams.get("before");
      const limit = Math.max(1, Math.min(100, Number(url.searchParams.get("limit") ?? 50)));
      const items: ActivityItem[] = db.activity.filter((item) => !before || item.at < before).slice(0, limit);
      return HttpResponse.json({ items });
    }),

    http.get(`${api}/players/:uuid`, async ({ params }) => {
      await delay(120);
      const denied = guard();
      if (denied) return denied;
      const uuid = String(params.uuid);
      const player = db.players.find((entry) => entry.uuid === uuid);
      if (!player) return error(404, "not_found");
      const mine = db.findings.filter((finding) => finding.players.some((entry) => entry.uuid === uuid));
      return HttpResponse.json({
        uuid,
        name: player.name,
        findings: {
          total: mine.length,
          open: mine.filter((finding) => !finding.review).length,
          confirmed: mine.filter((finding) => finding.review?.verdict === "CONFIRMED").length,
          falseAlarms: mine.filter((finding) => finding.review?.verdict === "FALSE_ALARM").length,
        },
      });
    }),

    http.get(`${api}/players/:uuid/skin.png`, async ({ params }) => {
      const denied = guard();
      if (denied) return denied;
      const uuid = String(params.uuid);
      if (!knownPlayer(uuid) || uuid === extraPlayersWithoutSkin) return error(404, "not_found");
      return png(await skinPng(uuid), { "X-Skin-Model": lookFor(uuid).skin === "#e8b796" ? "slim" : "classic" });
    }),

    http.get(`${api}/players/:uuid/face.png`, async ({ params }) => {
      const denied = guard();
      if (denied) return denied;
      const uuid = String(params.uuid);
      if (!knownPlayer(uuid) || uuid === extraPlayersWithoutSkin) return error(404, "not_found");
      return png(await facePng(uuid));
    }),

    http.get(`${api}/palette`, async () => {
      await delay(60);
      return HttpResponse.json(db.palette, { headers: { "Cache-Control": "max-age=86400" } });
    }),

    http.get(`${api}/public/:token`, async ({ params }) => {
      await delay(250);
      const finding = sharedFinding(db, String(params.token));
      if (!finding?.evidence) return error(404, "not_found");
      return HttpResponse.json({
        finding: { id: finding.id, createdAt: finding.createdAt, verdict: finding.review?.verdict ?? null, players: finding.players },
        evidence: relativeEvidence(finding.evidence),
      });
    }),

    ...(import.meta.env.VITE_ASSETS_TARGET
      ? []
      : [
          http.get(`${api}/assets`, async () => {
            await delay(60);
            const assets: AssetStatus = { available: false, version: null, downloading: false };
            return HttpResponse.json(assets);
          }),
        ]),

    http.get(`${api}/public/:token/skins/:file`, async ({ params }) => {
      const finding = sharedFinding(db, String(params.token));
      const uuid = String(params.file).replace(/\.png$/, "");
      if (!finding?.evidence?.recordings.some((recording) => recording.player === uuid)) return error(404, "not_found");
      return png(await skinPng(uuid));
    }),
  ];
}

const extraPlayersWithoutSkin = "9a8b7c6d-5e4f-4321-b0a9-8c7d6e5f4a3b";

