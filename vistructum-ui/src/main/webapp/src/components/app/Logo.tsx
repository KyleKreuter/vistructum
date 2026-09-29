import logo from "@/assets/logo.png";
import { cn } from "@/lib/utils";

const artWidth = 114;
const artHeight = 24;

export function Logo({ scale = 1, className }: { scale?: 1 | 2 | 3; className?: string }) {
  return (
    <img
      src={logo}
      alt="Vistructum"
      width={artWidth * scale}
      height={artHeight * scale}
      className={cn("pixelated block max-w-none shrink-0 select-none", className)}
      draggable={false}
    />
  );
}
