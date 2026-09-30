import { ArrowUpRight, UserRound } from "lucide-react";
import { useCallback, useEffect, useRef, useState, type ReactNode } from "react";
import { Link } from "react-router";
import { urls } from "@/api/client";
import type { PlayerRef } from "@/api/types";
import { playerLabel } from "@/logic/format";
import { cn } from "@/lib/utils";

function SkinFace({ skin, size, className, onError }: { skin: string; size: number; className?: string; onError: () => void }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  useEffect(() => {
    const image = new Image();
    image.onload = () => {
      const context = canvasRef.current?.getContext("2d");
      if (!context) return;
      context.imageSmoothingEnabled = false;
      context.clearRect(0, 0, 8, 8);
      context.drawImage(image, 8, 8, 8, 8, 0, 0, 8, 8);
      if (image.height >= 64) context.drawImage(image, 40, 8, 8, 8, 0, 0, 8, 8);
    };
    image.onerror = onError;
    image.src = skin;
    return () => {
      image.onload = null;
      image.onerror = null;
    };
  }, [skin, onError]);
  return <canvas ref={canvasRef} width={8} height={8} style={{ width: size, height: size }} className={cn("pixelated shrink-0 rounded-sm bg-muted", className)} aria-hidden />;
}

export function PlayerFace({
  uuid,
  name,
  size = 24,
  src,
  skin,
  className,
}: {
  uuid: string;
  name: string | null;
  size?: number;
  src?: string;
  skin?: string;
  className?: string;
}) {
  const [failed, setFailed] = useState<string | null>(null);
  const failSkin = useCallback(() => setFailed(skin ?? null), [skin]);
  const url = skin ?? src ?? urls.face(uuid);
  const style = { width: size, height: size };
  if (failed === url) {
    return (
      <span
        className={cn("inline-flex shrink-0 items-center justify-center rounded-sm bg-muted text-muted-foreground", className)}
        style={style}
        title={name ?? uuid}
        aria-hidden
      >
        <UserRound style={{ width: size * 0.7, height: size * 0.7 }} />
      </span>
    );
  }
  if (skin) return <SkinFace skin={skin} size={size} className={className} onError={failSkin} />;
  return <img src={url} alt="" width={size} height={size} style={style} className={cn("pixelated shrink-0 rounded-sm bg-muted", className)} onError={() => setFailed(url)} loading="lazy" />;
}

export function PlayerChip({ player, link = true }: { player: PlayerRef; link?: boolean }) {
  const content = (
    <>
      <PlayerFace uuid={player.uuid} name={player.name} size={18} />
      <span className={cn("truncate", !player.name && "text-xs")}>{playerLabel(player)}</span>
    </>
  );
  if (!link) return <span className="inline-flex min-w-0 items-center gap-1.5 text-sm">{content}</span>;
  return (
    <Link to={`/players/${player.uuid}`} className="inline-flex min-w-0 items-center gap-1.5 rounded-sm text-sm hover:underline" onClick={(event) => event.stopPropagation()}>
      {content}
    </Link>
  );
}

export function PlayerCard({
  player,
  skin,
  colour,
  detail,
  onSelect,
  className,
}: {
  player: PlayerRef;
  skin?: string;
  colour?: string;
  detail?: ReactNode;
  onSelect?: () => void;
  className?: string;
}) {
  const label = playerLabel(player);
  const body = (
    <>
      {colour && <span className="size-2.5 shrink-0 rounded-full" style={{ background: colour }} />}
      <PlayerFace uuid={player.uuid} name={player.name} size={20} skin={skin} />
      <span className="flex min-w-0 flex-1 flex-col">
        <span className={cn("truncate font-medium", !player.name && "text-xs")} title={label}>
          {label}
        </span>
        {detail && <span className="text-xs text-muted-foreground tabular-nums">{detail}</span>}
      </span>
    </>
  );
  const style = "flex items-center gap-2 rounded-lg border bg-card px-3 py-2 text-left text-sm transition-colors hover:border-ring";
  if (onSelect) {
    return (
      <div className={cn("relative", className)}>
        <button type="button" onClick={onSelect} className={cn(style, "w-full pr-8")}>
          {body}
        </button>
        <Link
          to={`/players/${player.uuid}`}
          aria-label={`Open ${label}`}
          title={`Open ${label}`}
          className="absolute top-1.5 right-1.5 rounded-sm p-0.5 text-muted-foreground hover:text-foreground"
        >
          <ArrowUpRight className="size-3.5" />
        </Link>
      </div>
    );
  }
  return (
    <Link to={`/players/${player.uuid}`} className={cn(style, className)}>
      {body}
    </Link>
  );
}
