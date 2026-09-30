import { Info } from "lucide-react";
import { lazy, Suspense, useCallback, useEffect, useRef } from "react";
import { useParams } from "react-router";
import { ApiError, urls } from "@/api/client";
import { usePalette, usePublicEvidence } from "@/api/queries";
import { ErrorState, PageSpinner } from "@/components/app/States";
import { PlainShell } from "@/components/app/PlainShell";
import { Badge } from "@/components/ui/badge";
import type { ReplayControls } from "@/features/replay/ReplayView";
import { formatDateTime } from "@/logic/format";
import { detailAction, isEditableTarget } from "@/logic/keyboard";

const ReplayView = lazy(() => import("@/features/replay/ReplayView"));

export default function PublicEvidencePage() {
  const token = useParams().token ?? "";
  const evidence = usePublicEvidence(token);
  const palette = usePalette();
  const skinUrl = useCallback((uuid: string) => urls.publicSkin(token, uuid), [token]);
  const finding = evidence.data?.finding;
  const gone = evidence.error instanceof ApiError && evidence.error.status === 404;
  const controls = useRef<ReplayControls | null>(null);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.defaultPrevented) return;
      const action = detailAction({
        key: event.key,
        metaKey: event.metaKey,
        ctrlKey: event.ctrlKey,
        altKey: event.altKey,
        editable: isEditableTarget(event.target),
      });
      const replay = controls.current;
      if (!action || !replay) return;
      if (action.type !== "playPause") return;
      replay.toggle();
      event.preventDefault();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  return (
    <PlainShell className="space-y-4">
        {evidence.isPending || palette.isPending ? (
          <PageSpinner className="h-[60vh]" />
        ) : gone ? (
          <div className="mx-auto max-w-md space-y-2 p-8 text-center">
            <p className="font-medium">This link is not active</p>
            <p className="text-sm text-muted-foreground">The evidence was never shared under this link, or the link has been deactivated.</p>
          </div>
        ) : evidence.isError || palette.isError || !evidence.data || !palette.data || !finding ? (
          <ErrorState error={evidence.error ?? palette.error} onRetry={() => {
              if (evidence.isError) void evidence.refetch();
              if (palette.isError) void palette.refetch();
            }} />
        ) : (
          <>
            <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
              <h1 className="text-xl font-semibold tracking-tight">Recorded build</h1>
              {finding.verdict === "CONFIRMED" ? (
                <Badge variant="rose">
                  <span className="size-1.5 rounded-full bg-confirmed" />
                  Confirmed by staff
                </Badge>
              ) : finding.verdict === "FALSE_ALARM" ? (
                <Badge variant="green">
                  <span className="size-1.5 rounded-full bg-false-alarm" />
                  False alarm
                </Badge>
              ) : (
                <Badge variant="amber">Not reviewed</Badge>
              )}
              <span className="text-sm text-muted-foreground">{formatDateTime(finding.createdAt)}</span>
            </div>
            {finding.verdict !== "CONFIRMED" && (
              <div className="flex items-center gap-2 rounded-lg border bg-muted/50 px-4 py-3 text-sm">
                <Info className="size-4 shrink-0 text-muted-foreground" />
                This finding is no longer marked as confirmed.
              </div>
            )}
            <Suspense fallback={<PageSpinner className="h-[64vh]" />}>
              <ReplayView evidence={evidence.data.evidence} palette={palette.data} skinUrl={skinUrl} facesFromSkin showPlayers controlsRef={controls} />
            </Suspense>
          </>
        )}
    </PlainShell>
  );
}
