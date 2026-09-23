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


class ExportNet(nn.Module):
    def __init__(self, model):
        super().__init__()
        self.model = model

    def forward(self, x):
        return torch.softmax(self.model(x), dim=1)
