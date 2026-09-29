import { Check, Copy, ExternalLink, Link2, Link2Off } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { errorMessage } from "@/api/client";
import { useShare } from "@/api/queries";
import type { FindingDetail } from "@/api/types";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { copyText } from "@/lib/clipboard";
import { formatDateTime } from "@/logic/format";
import { shareView } from "@/logic/share";

export function SharePanel({ finding, canShare }: { finding: FindingDetail; canShare: boolean }) {
  const { activate, deactivate } = useShare(finding.id);
  const [copied, setCopied] = useState(false);
  const busy = activate.isPending || deactivate.isPending;
  const url = finding.shareUrl;
  const view = shareView(finding, canShare);

  const copy = async (value: string) => {
    if (await copyText(value)) {
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1500);
      return true;
    }
    toast.error("Copy failed. Select the link and copy it by hand.");
    return false;
  };

  const turnOn = () =>
    activate.mutate(undefined, {
      onSuccess: async (result) => {
        const ok = await copyText(result.url);
        toast.success(ok ? "Public link activated and copied." : "Public link activated.");
      },
      onError: (error) => toast.error(errorMessage(error)),
    });

  const turnOff = () =>
    deactivate.mutate(undefined, {
      onSuccess: () => toast.success("Public link deactivated. The same link works again once it is reactivated."),
      onError: (error) => toast.error(errorMessage(error)),
    });

  return (
    <Card className="gap-3 py-4">
      <CardHeader className="px-4">
        <CardTitle className="flex items-center gap-2 text-sm">
          <Link2 className="size-4" /> Public evidence link
        </CardTitle>
        <CardDescription className="text-xs">
          The public page shows only the replay, the date and the players. It shows no coordinates and no world.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-3 px-4 text-sm">
        {url ? (
          <>
            <div className="flex gap-2">
              <Input readOnly value={url} aria-label="Public link" className="h-8 text-xs" onFocus={(event) => event.currentTarget.select()} />
              <Button size="icon" variant="outline" className="size-8 shrink-0" aria-label="Copy link" onClick={() => void copy(url)}>
                {copied ? <Check /> : <Copy />}
              </Button>
              <Button size="icon" variant="outline" className="size-8 shrink-0" aria-label="Open public page" asChild>
                <a href={url} target="_blank" rel="noreferrer">
                  <ExternalLink />
                </a>
              </Button>
            </div>
            {finding.sharedSince && <p className="text-xs text-muted-foreground">Active since {formatDateTime(finding.sharedSince)}.</p>}
            {canShare && (
              <Button size="sm" variant="outline" className="text-destructive" onClick={turnOff} disabled={busy}>
                <Link2Off /> Deactivate
              </Button>
            )}
          </>
        ) : view === "activate" ? (
          <Button size="sm" onClick={turnOn} disabled={busy}>
            <Link2 /> Activate link
          </Button>
        ) : (
          <p className="text-xs text-muted-foreground">The public link is not active.</p>
        )}
      </CardContent>
    </Card>
  );
}
