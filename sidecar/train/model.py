import torch
from torch import nn

from vistructum_ml.contract import KINDS, LABELS


class InputNorm(nn.Module):
    def __init__(self, kind):
        super().__init__()
        spec = KINDS[kind]
        scale = torch.tensor(spec.scale, dtype=torch.float32).view(1, -1, 1, 1)
        offset = torch.tensor(spec.offset, dtype=torch.float32).view(1, -1, 1, 1)
        self.register_buffer("scale", scale)
        self.register_buffer("offset", offset)

    def forward(self, x):
        return x.float() * self.scale + self.offset


class SymbolNet(nn.Module):
    def __init__(self, kind, widths, dropout=0.0, depths=None):
        """depths: 3x3 convs per stage (default 1 each); every stage but the last ends in a 2x max-pool"""
        super().__init__()
        spec = KINDS[kind]
        self.norm = InputNorm(kind)
        depths = list(depths) if depths else [1] * len(widths)
        if len(depths) != len(widths) or min(depths) < 1:
            raise ValueError(f"depths {depths} must give one count >= 1 per width in {widths}")
        blocks = []
        in_channels = spec.channels
        for index, (width, depth) in enumerate(zip(widths, depths)):
            layers = []
            for _ in range(depth):
                layers += [nn.Conv2d(in_channels, width, 3, padding=1), nn.BatchNorm2d(width), nn.Hardswish()]
                in_channels = width
            if index < len(widths) - 1:
                layers.append(nn.MaxPool2d(2))
            blocks.append(nn.Sequential(*layers))
        self.blocks = nn.Sequential(*blocks)
        self.dropout = nn.Dropout(dropout) if dropout > 0 else nn.Identity()
        self.head = nn.Linear(in_channels, len(LABELS))

    def forward(self, x):
        x = self.norm(x)
        x = self.blocks(x)
        x = x.mean(dim=(2, 3))
        x = self.dropout(x)
        return self.head(x)


def d4_views(x):
    """the 8 rotations/mirrors of a (n, c, h, w) batch, stacked along the batch axis; flips and a transpose only,
    so the ONNX graph needs no rot90 support"""
    t = x.transpose(2, 3)
    return torch.cat([x, x.flip(3), x.flip(2), x.flip(2).flip(3), t, t.flip(3), t.flip(2), t.flip(2).flip(3)])


class ExportNet(nn.Module):
    """softmax scores; with tta the score is the mean over all 8 D4 views of the window (8x the compute)"""

    def __init__(self, model, tta=False):
        super().__init__()
        self.model = model
        self.tta = tta

    def forward(self, x):
        if not self.tta:
            return torch.softmax(self.model(x), dim=1)
        probs = torch.softmax(self.model(d4_views(x)), dim=1)
        return probs.reshape(8, x.shape[0], probs.shape[1]).mean(dim=0)
