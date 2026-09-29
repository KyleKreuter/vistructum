interface SkinLook {
  skin: string;
  hair: string;
  eyes: string;
  shirt: string;
  sleeve: string;
  pants: string;
  shoes: string;
}

const looks: SkinLook[] = [
  { skin: "#c68863", hair: "#3b2414", eyes: "#2f4fa8", shirt: "#2f8f83", sleeve: "#277a70", pants: "#39386d", shoes: "#4a4a4a" },
  { skin: "#e8b796", hair: "#d9b04c", eyes: "#3c7a3c", shirt: "#b83b3b", sleeve: "#9c3030", pants: "#2b2b2b", shoes: "#1c1c1c" },
  { skin: "#8d5a3b", hair: "#141414", eyes: "#5a3a20", shirt: "#e0c24a", sleeve: "#c9ad3b", pants: "#4d6e8f", shoes: "#3a2a1a" },
  { skin: "#f1c9a5", hair: "#b0472a", eyes: "#2d7fbf", shirt: "#6b4fa0", sleeve: "#5a4288", pants: "#5c5c5c", shoes: "#2e2e2e" },
  { skin: "#b07a55", hair: "#5e3b1f", eyes: "#2a2a2a", shirt: "#dcdcdc", sleeve: "#bcbcbc", pants: "#27456b", shoes: "#6b4a2a" },
  { skin: "#dca77f", hair: "#1f1f3a", eyes: "#6a2ca0", shirt: "#3f7f3f", sleeve: "#346b34", pants: "#6b5236", shoes: "#2b2b2b" },
];

function hash(value: string): number {
  let h = 2166136261;
  for (let i = 0; i < value.length; i++) h = Math.imul(h ^ value.charCodeAt(i), 16777619);
  return h >>> 0;
}

export function lookFor(uuid: string): SkinLook {
  return looks[hash(uuid) % looks.length];
}

function paintSkin(context: CanvasRenderingContext2D, look: SkinLook) {
  const fill = (colour: string, x: number, y: number, w: number, h: number) => {
    context.fillStyle = colour;
    context.fillRect(x, y, w, h);
  };
  context.clearRect(0, 0, 64, 64);
  fill(look.skin, 0, 0, 32, 16);
  fill(look.hair, 8, 0, 8, 8);
  fill(look.hair, 0, 8, 32, 2);
  fill(look.hair, 24, 8, 8, 6);
  fill(look.hair, 0, 10, 2, 3);
  fill(look.hair, 22, 10, 2, 3);
  fill(look.hair, 16, 0, 8, 8);
  fill("#ffffff", 9, 12, 2, 1);
  fill("#ffffff", 13, 12, 2, 1);
  fill(look.eyes, 10, 12, 1, 1);
  fill(look.eyes, 13, 12, 1, 1);
  fill("#7a4a3a", 11, 14, 2, 1);
  fill(look.shirt, 16, 16, 24, 16);
  fill(look.sleeve, 40, 16, 16, 16);
  fill(look.skin, 40, 28, 16, 4);
  fill(look.skin, 48, 16, 4, 4);
  fill(look.sleeve, 32, 48, 16, 16);
  fill(look.skin, 32, 60, 16, 4);
  fill(look.skin, 40, 48, 4, 4);
  fill(look.pants, 0, 16, 16, 16);
  fill(look.shoes, 0, 30, 16, 2);
  fill(look.shoes, 8, 16, 4, 4);
  fill(look.pants, 16, 48, 16, 16);
  fill(look.shoes, 16, 62, 16, 2);
  fill(look.shoes, 24, 48, 4, 4);
}

function toPng(canvas: HTMLCanvasElement): Promise<Blob> {
  return new Promise((resolve, reject) => canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error("png"))), "image/png"));
}

function canvas(width: number, height: number): [HTMLCanvasElement, CanvasRenderingContext2D] {
  const element = document.createElement("canvas");
  element.width = width;
  element.height = height;
  const context = element.getContext("2d");
  if (!context) throw new Error("canvas");
  context.imageSmoothingEnabled = false;
  return [element, context];
}

export function skinPng(uuid: string): Promise<Blob> {
  const [element, context] = canvas(64, 64);
  paintSkin(context, lookFor(uuid));
  return toPng(element);
}

export function facePng(uuid: string): Promise<Blob> {
  const [source, sourceContext] = canvas(64, 64);
  paintSkin(sourceContext, lookFor(uuid));
  const [element, context] = canvas(64, 64);
  context.drawImage(source, 8, 8, 8, 8, 0, 0, 64, 64);
  return toPng(element);
}

export function imagePng(width: number, height: number, pixels: Uint8ClampedArray, scale: number): Promise<Blob> {
  const [source, sourceContext] = canvas(width, height);
  sourceContext.putImageData(new ImageData(new Uint8ClampedArray(pixels), width, height), 0, 0);
  const [element, context] = canvas(width * scale, height * scale);
  context.drawImage(source, 0, 0, width * scale, height * scale);
  return toPng(element);
}
