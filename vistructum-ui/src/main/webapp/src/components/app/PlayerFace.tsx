import { useCallback, useEffect, useRef, useState } from "react";
import { Link } from "react-router";
import { urls } from "@/api/client";
import type { PlayerRef } from "@/api/types";
import { playerLabel } from "@/logic/format";
import { cn } from "@/lib/utils";

function initials(name: string | null, uuid: string): string {
  return (name ?? uuid).slice(0, 2).toUpperCase();
}

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
        className={cn("inline-flex shrink-0 items-center justify-center rounded-sm bg-muted font-mono text-[9px] font-semibold text-muted-foreground", className)}
        style={style}
        aria-hidden
      >
        {initials(name, uuid)}
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
      <span className={cn("truncate", !player.name && "font-mono text-xs")}>{playerLabel(player)}</span>
    </>
  );
  if (!link) return <span className="inline-flex min-w-0 items-center gap-1.5 text-sm">{content}</span>;
  return (
    <Link to={`/players/${player.uuid}`} className="inline-flex min-w-0 items-center gap-1.5 rounded-sm text-sm hover:underline" onClick={(event) => event.stopPropagation()}>
      {content}
    </Link>
  );
}
