import { ImageOff } from "lucide-react";
import { useState } from "react";
import { urls } from "@/api/client";
import { cn } from "@/lib/utils";

export function Thumbnail({ id, className }: { id: number; className?: string }) {
  const [failed, setFailed] = useState(false);
  if (failed) {
    return (
      <div className={cn("flex items-center justify-center rounded-md bg-muted text-muted-foreground", className)} aria-label="No thumbnail">
        <ImageOff className="size-4" />
      </div>
    );
  }
  return <img src={urls.thumbnail(id)} alt="" loading="lazy" onError={() => setFailed(true)} className={cn("pixelated rounded-md bg-well object-cover", className)} />;
}
