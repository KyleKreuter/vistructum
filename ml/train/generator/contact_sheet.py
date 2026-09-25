import argparse

import numpy as np
from PIL import Image, ImageDraw, ImageOps

CELL = 96
PAD = 4
LABEL_H = 28


def _channel_image(channel):
    img = Image.fromarray(channel, mode="L")
    if channel.max() > channel.min():
        img = ImageOps.autocontrast(img)
    return img.resize((CELL, CELL), Image.NEAREST)


def render_sheet(x, y, subtype, indices, cols=8):
    n = len(indices)
    channels = x.shape[1]
    cell_w = channels * CELL + (channels - 1) * PAD
    cell_h = CELL + LABEL_H
    rows = (n + cols - 1) // cols
    sheet_w = cols * cell_w + (cols + 1) * PAD
    sheet_h = rows * cell_h + (rows + 1) * PAD
    sheet = Image.new("RGB", (sheet_w, sheet_h), (30, 30, 30))
    draw = ImageDraw.Draw(sheet)
    for i, idx in enumerate(indices):
        row, col = divmod(i, cols)
        ox = PAD + col * (cell_w + PAD)
        oy = PAD + row * (cell_h + PAD)
        for c in range(channels):
            img = _channel_image(x[idx, c])
            sheet.paste(img.convert("RGB"), (ox + c * (CELL + PAD), oy))
        label = f"{'pos' if y[idx] == 1 else 'neg'} {subtype[idx]}"
        color = (255, 90, 90) if y[idx] == 1 else (120, 200, 255)
        draw.text((ox, oy + CELL + 2), label[:28], fill=color)
    return sheet


def main():
    parser = argparse.ArgumentParser(description="render a contact sheet PNG of dataset samples for manual review")
    parser.add_argument("--data", required=True, help="path to a split .npz file (train/val/test/holdout)")
    parser.add_argument("--out", required=True)
    parser.add_argument("--n", type=int, default=64)
    parser.add_argument("--cols", type=int, default=8)
    parser.add_argument("--seed", type=int, default=0)
    parser.add_argument("--label", choices=("any", "pos", "neg"), default="any")
    args = parser.parse_args()

    data = np.load(args.data, allow_pickle=True)
    x, y, subtype = data["x"], data["y"], data["subtype"]
    pool = np.arange(len(y))
    if args.label == "pos":
        pool = pool[y == 1]
    elif args.label == "neg":
        pool = pool[y == 0]
    rng = np.random.default_rng(args.seed)
    n = min(args.n, len(pool))
    indices = rng.choice(pool, size=n, replace=False)
    sheet = render_sheet(x, y, subtype, indices, cols=args.cols)
    sheet.save(args.out)
    print(f"wrote {args.out} ({n} samples)")


if __name__ == "__main__":
    main()
