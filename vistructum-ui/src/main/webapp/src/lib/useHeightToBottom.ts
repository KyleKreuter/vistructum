import { useLayoutEffect, useState } from "react";

export function useHeightToBottom(target: HTMLElement | null, anchor: HTMLElement | null): number | null {
  const [height, setHeight] = useState<number | null>(null);
  useLayoutEffect(() => {
    if (!target || !anchor) return;
    const measure = () => setHeight(Math.max(0, Math.round(target.getBoundingClientRect().bottom - anchor.getBoundingClientRect().top)));
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(target);
    observer.observe(anchor);
    window.addEventListener("resize", measure);
    return () => {
      observer.disconnect();
      window.removeEventListener("resize", measure);
    };
  }, [target, anchor]);
  return target && anchor ? height : null;
}
