import { keepPreviousData, queryOptions, useInfiniteQuery, useMutation, useQuery, useQueryClient, type QueryClient } from "@tanstack/react-query";
import { findingsQuery } from "@/logic/filters";
import { ApiError, request } from "./client";
import type {
  ActivityPage,
  Evidence,
  FindingDetail,
  FindingFilter,
  FindingPage,
  FindingSummary,
  Paging,
  Heatmap,
  Me,
  Palette,
  PlayerInfo,
  PublicEvidence,
  Scene,
  ShareResult,
  Stats,
  Status,
  Terrain,
  Verdict,
} from "./types";

export const keys = {
  me: ["me"] as const,
  findings: (filter: FindingFilter, paging: Paging) => ["findings", filter, paging.page, paging.pageSize] as const,
  finding: (id: number) => ["finding", id] as const,
  scene: (id: number) => ["scene", id] as const,
  terrain: (id: number) => ["terrain", id] as const,
  heatmap: (id: number) => ["heatmap", id] as const,
  evidence: (id: number) => ["evidence", id] as const,
  palette: ["palette"] as const,
  status: ["status"] as const,
  stats: (from: string, to: string) => ["stats", from, to] as const,
  activity: ["activity"] as const,
  player: (uuid: string) => ["player", uuid] as const,
  publicEvidence: (token: string) => ["public", token] as const,
};

export function shouldRetry(failures: number, error: unknown): boolean {
  if (error instanceof ApiError && (error.status < 500 || error.status === 503)) return false;
  return failures < 1;
}

export const retryDelayMs = 400;

export function useMe() {
  return useQuery({
    queryKey: keys.me,
    queryFn: () => request<Me>("/me"),
    staleTime: 60_000,
  });
}

export function findingsOptions(filter: FindingFilter, paging: Paging) {
  return queryOptions({
    queryKey: keys.findings(filter, paging),
    queryFn: () => request<FindingPage>(`/findings?${findingsQuery(filter, paging)}`),
  });
}

export function useFindings(filter: FindingFilter, paging: Paging) {
  return useQuery({ ...findingsOptions(filter, paging), placeholderData: keepPreviousData });
}

export function useFinding(id: number) {
  return useQuery({
    queryKey: keys.finding(id),
    queryFn: () => request<FindingDetail>(`/findings/${id}`),
  });
}

export function useScene(id: number, enabled = true) {
  return useQuery({
    queryKey: keys.scene(id),
    queryFn: () => request<Scene>(`/findings/${id}/scene`),
    enabled,
    staleTime: Number.POSITIVE_INFINITY,
  });
}

export function useTerrain(id: number, enabled: boolean) {
  return useQuery({
    queryKey: keys.terrain(id),
    queryFn: () => request<Terrain>(`/findings/${id}/terrain`),
    enabled,
    staleTime: Number.POSITIVE_INFINITY,
  });
}

export function useHeatmap(id: number, enabled: boolean) {
  return useQuery({
    queryKey: keys.heatmap(id),
    queryFn: () => request<Heatmap>(`/findings/${id}/heatmap`),
    enabled,
    staleTime: Number.POSITIVE_INFINITY,
  });
}

export function useEvidence(id: number, enabled: boolean) {
  return useQuery({
    queryKey: keys.evidence(id),
    queryFn: () => request<Evidence>(`/findings/${id}/evidence`),
    enabled,
    staleTime: Number.POSITIVE_INFINITY,
  });
}

export function usePalette() {
  return useQuery({
    queryKey: keys.palette,
    queryFn: () => request<Palette>("/palette", {}, { public: true }),
    staleTime: Number.POSITIVE_INFINITY,
  });
}

export function useStatus() {
  return useQuery({
    queryKey: keys.status,
    queryFn: () => request<Status>("/status"),
    refetchInterval: 2000,
  });
}

export function useStats(from: string, to: string) {
  return useQuery({
    queryKey: keys.stats(from, to),
    queryFn: () => request<Stats>(`/stats?${new URLSearchParams({ from, to }).toString()}`),
    placeholderData: keepPreviousData,
  });
}

export function useActivity() {
  return useInfiniteQuery({
    queryKey: keys.activity,
    queryFn: ({ pageParam }) => {
      const params = new URLSearchParams({ limit: "50" });
      if (pageParam) params.set("before", pageParam);
      return request<ActivityPage>(`/activity?${params.toString()}`);
    },
    initialPageParam: null as string | null,
    getNextPageParam: (last) => (last.items.length >= 50 ? last.items[last.items.length - 1].at : null),
  });
}

export function usePlayer(uuid: string) {
  return useQuery({
    queryKey: keys.player(uuid),
    queryFn: () => request<PlayerInfo>(`/players/${uuid}`),
  });
}

export function usePublicEvidence(token: string) {
  return useQuery({
    queryKey: keys.publicEvidence(token),
    queryFn: () => request<PublicEvidence>(`/public/${encodeURIComponent(token)}`, {}, { public: true }),
    staleTime: Number.POSITIVE_INFINITY,
  });
}

function patchFinding(client: QueryClient, updated: Partial<FindingSummary> & { id: number }) {
  client.setQueryData<FindingDetail>(keys.finding(updated.id), (current) => (current ? { ...current, ...updated } : current));
}

export function useVerdict() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ id, verdict }: { id: number; verdict: Verdict }) =>
      request<FindingSummary>(`/findings/${id}/verdict`, { method: "POST", body: JSON.stringify({ verdict }) }),
    onSuccess: (summary) => {
      patchFinding(client, summary);
      void client.invalidateQueries({ queryKey: ["findings"], refetchType: "none" });
      void client.invalidateQueries({ queryKey: keys.activity });
      void client.invalidateQueries({ queryKey: ["stats"] });
      void client.invalidateQueries({ queryKey: ["player"] });
    },
  });
}

export function useShare(id: number) {
  const client = useQueryClient();
  const share = useMutation({
    mutationFn: () => request<ShareResult>(`/findings/${id}/share`, { method: "POST" }),
    onSuccess: (result) => {
      patchFinding(client, { id, sharedSince: result.sharedSince, shareUrl: result.url });
      void client.invalidateQueries({ queryKey: ["findings"], refetchType: "none" });
      void client.invalidateQueries({ queryKey: keys.activity });
    },
  });
  const revoke = useMutation({
    mutationFn: () => request<undefined>(`/findings/${id}/share`, { method: "DELETE" }),
    onSuccess: () => {
      patchFinding(client, { id, sharedSince: null, shareUrl: null });
      void client.invalidateQueries({ queryKey: ["findings"], refetchType: "none" });
      void client.invalidateQueries({ queryKey: keys.activity });
    },
  });
  return { activate: share, deactivate: revoke };
}

export function useLogout() {
  return useMutation({ mutationFn: () => request<undefined>("/logout", { method: "POST" }) });
}
