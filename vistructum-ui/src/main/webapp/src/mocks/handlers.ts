import { delay, http, HttpResponse, passthrough } from "msw";
import type { ActivityItem, AssetStatus, FindingState, FindingSummary, ScanJob, Stats, Status, Verdict } from "@/api/types";
import { matchesState } from "@/logic/filters";
import { paintLayer, sceneColours } from "@/logic/sceneLayers";
import { publicUrl, type MockDb, type MockFinding } from "./data";
import { relativeEvidence, syntheticEvidence } from "./evidence";
import { mockPunishments } from "./punishments";
import { sceneTerrain } from "./terrain";
import { activeSettings, type EndpointKey } from "./settings";
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

const failureCodes: Record<number, string> = { 401: "unauthorized", 403: "forbidden", 404: "not_found", 409: "not_shareable", 500: "internal", 503: "unavailable" };

async function gate(key: EndpointKey, defaultDelay: number, access: "private" | "public" = "private") {
  const settings = activeSettings();
  const pause = settings.latency ?? defaultDelay;
  if (pause > 0) await delay(pause);
  const status = settings.failures[key];
  if (status !== undefined) return error(status, failureCodes[status] ?? "internal");
  return access === "private" && mockSignedOut() ? error(401, "unauthorized") : null;
}

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
    hasTerrain: finding.hasTerrain,
    sharedSince: finding.sharedSince,
    shareUrl: finding.shareActive && finding.shareToken ? publicUrl(finding.shareToken) : null,
    rolledBackAt: finding.rolledBackAt,
    rolledBackBy: finding.rolledBackBy,
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

function sharedFinding(db: MockDb, index: Map<number, MockFinding>, shareToken: string): MockFinding | undefined {
  const id = db.shares.get(shareToken);
  const finding = id === undefined ? undefined : index.get(id);
  return finding?.shareActive && finding.hasEvidence ? finding : undefined;
}

function token(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  return [...bytes].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

export function handlers(db: MockDb) {
  const index = new Map(db.findings.map((finding) => [finding.id, finding]));
  const byId = (id: string | readonly string[] | undefined) => index.get(Number(id));
  const playerIds = new Set(db.players.map((player) => player.uuid));
  const knownPlayer = (uuid: string) => playerIds.has(uuid);

  return [
    http.get(`${api}/me`, async () => {
      const blocked = await gate("me", 120);
      return blocked ?? HttpResponse.json(db.me);
    }),

    http.post(`${api}/logout`, async () => {
      const blocked = await gate("logout", 0, "public");
      if (blocked) return blocked;
      setMockSignedOut(true);
      return new HttpResponse(null, { status: 204 });
    }),

    http.get(`${api}/status`, async () => {
      const blocked = await gate("status", 80);
      if (blocked) return blocked;
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
        trackedChanges: db.status.trackedChanges > 0 ? db.status.trackedChanges + Math.floor((now / 1000) % 60) : 0,
        openFindings: db.findings.filter((finding) => !finding.review).length,
        inference: db.status.inference,
        scans,
        recordingEnabled: db.status.recordingEnabled,
        textures: { enabled: true, available: db.status.texturesAvailable, version: null },
      };
      return HttpResponse.json(status);
    }),

    http.get(`${api}/findings`, async ({ request }) => {
      const blocked = await gate("findings", 180);
      if (blocked) return blocked;
      const url = new URL(request.url);
      const state = (url.searchParams.get("state") ?? "open") as FindingState;
      const source = url.searchParams.get("source");
      const world = url.searchParams.get("world");
      const player = url.searchParams.get("player");
      const since = url.searchParams.get("since");
      const page = Number(url.searchParams.get("page") ?? "1");
      const pageSize = Number(url.searchParams.get("pageSize") ?? "25");
      if (!["open", "reviewed", "confirmed", "false_alarm", "any"].includes(state)) return error(400, "bad_request");
      if (!Number.isSafeInteger(page) || page < 1 || !Number.isInteger(pageSize) || pageSize < 1 || pageSize > 100) {
        return error(400, "bad_request");
      }
      const matching = db.findings.filter(
        (finding) =>
          matchesState(state, finding.review?.verdict ?? null) &&
          (!source || finding.source === source) &&
          (!world || finding.world === world) &&
          (!player || finding.players.some((entry) => entry.uuid === player)) &&
          (!since || finding.createdAt >= since),
      );
      const items = matching.slice((page - 1) * pageSize, page * pageSize);
      return HttpResponse.json({ items: items.map(summary), total: matching.length, page, pageSize });
    }),

    http.get(`${api}/findings/:id`, async ({ params }) => {
      const blocked = await gate("finding", 120);
      if (blocked) return blocked;
      const finding = byId(params.id);
      return finding ? HttpResponse.json({ ...summary(finding), teleport: finding.teleport }) : error(404, "not_found");
    }),

    http.get(`${api}/findings/:id/thumbnail.png`, async ({ params }) => {
      const blocked = await gate("thumbnail", 0);
      if (blocked) return blocked;
      const finding = byId(params.id);
      if (!finding?.scene || !finding.thumbnail) return error(404, "not_found");
      const pixels = paintLayer(finding.scene, sceneColours(finding.scene, db.palette), "colour", { span: 4, heatmap: null, overlay: false });
      return png(await imagePng(finding.scene.width, finding.scene.height, pixels, 1));
    }),

    http.get(`${api}/findings/:id/scene`, async ({ params }) => {
      const blocked = await gate("scene", 220);
      if (blocked) return blocked;
      const finding = byId(params.id);
      return finding?.scene ? HttpResponse.json(finding.scene) : error(404, "not_found");
    }),

    http.get(`${api}/findings/:id/heatmap`, async ({ params }) => {
      const blocked = await gate("heatmap", 1400);
      if (blocked) return blocked;
      const finding = byId(params.id);
      if (!finding) return error(404, "not_found");
      return finding.heatmap ? HttpResponse.json(finding.heatmap) : error(503, "unavailable");
    }),

    http.get(`${api}/findings/:id/terrain`, async ({ params }) => {
      const blocked = await gate("terrain", 400);
      if (blocked) return blocked;
      const finding = byId(params.id);
      const terrain = finding?.hasTerrain && finding.scene ? sceneTerrain(finding.scene, finding.box) : null;
      return terrain ? HttpResponse.json(terrain) : error(404, "not_found");
    }),

    http.get(`${api}/findings/:id/evidence`, async ({ params }) => {
      const blocked = await gate("evidence", 300);
      if (blocked) return blocked;
      const finding = byId(params.id);
      const evidence = finding ? db.evidenceOf(finding) : null;
      return evidence ? HttpResponse.json(evidence) : error(404, "not_found");
    }),

    http.post(`${api}/findings/:id/verdict`, async ({ params, request }) => {
      const blocked = await gate("verdict", 150);
      if (blocked) return blocked;
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
      const blocked = await gate("share", 200);
      if (blocked) return blocked;
      if (!csrfOk(request)) return error(403, "csrf");
      if (!db.me.canShare) return error(403, "forbidden");
      const finding = byId(params.id);
      if (!finding) return error(404, "not_found");
      if (finding.review?.verdict !== "CONFIRMED" || !finding.hasEvidence) return error(409, "not_shareable");
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
      const blocked = await gate("share", 150);
      if (blocked) return blocked;
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

    http.post(`${api}/findings/:id/attribute`, async ({ params, request }) => {
      const blocked = await gate("blockLog", 400);
      if (blocked) return blocked;
      if (!csrfOk(request)) return error(403, "csrf");
      if (!db.me.blockLog) return error(503, "unavailable");
      const finding = byId(params.id);
      if (!finding) return error(404, "not_found");
      if (finding.players.length && finding.hasEvidence) return error(409, "not_attributable");
      const builder = db.players[0];
      if (!finding.players.length && builder) {
        finding.players = [builder];
        db.activity.unshift({ at: new Date().toISOString(), actor: db.me.name, kind: "ATTRIBUTED", findingId: finding.id });
      }
      finding.evidence ??= syntheticEvidence(finding.id);
      finding.hasEvidence = true;
      return HttpResponse.json(summary(finding));
    }),

    http.post(`${api}/findings/:id/rollback`, async ({ params, request }) => {
      const blocked = await gate("blockLog", 600);
      if (blocked) return blocked;
      if (!csrfOk(request)) return error(403, "csrf");
      if (!db.me.blockLog) return error(503, "unavailable");
      if (!db.me.canRollback) return error(403, "forbidden");
      const finding = byId(params.id);
      if (!finding) return error(404, "not_found");
      if (finding.review?.verdict !== "CONFIRMED" || !finding.players.length || finding.rolledBackAt) return error(409, "not_rollbackable");
      finding.rolledBackAt = new Date().toISOString();
      finding.rolledBackBy = db.me.name;
      db.activity.unshift({ at: finding.rolledBackAt, actor: db.me.name, kind: "ROLLED_BACK", findingId: finding.id });
      return HttpResponse.json({ restored: 48, skipped: 3 });
    }),

    http.get(`${api}/stats`, async ({ request }) => {
      const blocked = await gate("stats", 200);
      if (blocked) return blocked;
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
      const blocked = await gate("activity", 150);
      if (blocked) return blocked;
      const url = new URL(request.url);
      const pageSize = Math.max(1, Math.min(100, Number(url.searchParams.get("pageSize") ?? 25)));
      const page = Math.max(1, Number(url.searchParams.get("page") ?? 1));
      const items: ActivityItem[] = db.activity.slice((page - 1) * pageSize, page * pageSize);
      return HttpResponse.json({ items, total: db.activity.length, page, pageSize });
    }),

    http.get(`${api}/players/:uuid`, async ({ params }) => {
      const blocked = await gate("players", 120);
      if (blocked) return blocked;
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

    http.get(`${api}/players/:uuid/punishments`, async ({ params }) => {
      const blocked = await gate("punishments", 300);
      if (blocked) return blocked;
      if (!db.me.punishments) return error(503, "unavailable");
      return HttpResponse.json({ source: db.me.punishments, items: mockPunishments(String(params.uuid), Date.now()) });
    }),

    http.get(`${api}/players/:uuid/skin.png`, async ({ params }) => {
      const blocked = await gate("skin", 0);
      if (blocked) return blocked;
      const uuid = String(params.uuid);
      if (!knownPlayer(uuid) || uuid === extraPlayersWithoutSkin) return error(404, "not_found");
      return png(await skinPng(uuid), { "X-Skin-Model": lookFor(uuid).skin === "#e8b796" ? "slim" : "classic" });
    }),

    http.get(`${api}/players/:uuid/face.png`, async ({ params }) => {
      const blocked = await gate("face", 0);
      if (blocked) return blocked;
      const uuid = String(params.uuid);
      if (!knownPlayer(uuid) || uuid === extraPlayersWithoutSkin) return error(404, "not_found");
      return png(await facePng(uuid));
    }),

    http.get(`${api}/palette`, async () => {
      const blocked = await gate("palette", 60, "public");
      if (blocked) return blocked;
      return HttpResponse.json(db.palette, { headers: { "Cache-Control": "max-age=86400" } });
    }),

    http.get(`${api}/public/:token`, async ({ params }) => {
      const blocked = await gate("public", 250, "public");
      if (blocked) return blocked;
      const finding = sharedFinding(db, index, String(params.token));
      const evidence = finding ? db.evidenceOf(finding) : null;
      if (!finding || !evidence) return error(404, "not_found");
      return HttpResponse.json({
        finding: { id: finding.id, createdAt: finding.createdAt, verdict: finding.review?.verdict ?? null, players: finding.players },
        evidence: relativeEvidence(evidence),
      });
    }),

    http.get(`${api}/assets`, async () => {
      const blocked = await gate("assets", 60, "public");
      if (blocked) return blocked;
      if (db.status.texturesAvailable) return passthrough();
      const assets: AssetStatus = { available: false, version: null, downloading: false };
      return HttpResponse.json(assets);
    }),

    http.get(`${api}/public/:token/skins/:file`, async ({ params }) => {
      const blocked = await gate("public", 0, "public");
      if (blocked) return blocked;
      const finding = sharedFinding(db, index, String(params.token));
      const uuid = String(params.file).replace(/\.png$/, "");
      if (!finding?.players.some((player) => player.uuid === uuid)) return error(404, "not_found");
      return png(await skinPng(uuid));
    }),
  ];
}

const extraPlayersWithoutSkin = "9a8b7c6d-5e4f-4321-b0a9-8c7d6e5f4a3b";

