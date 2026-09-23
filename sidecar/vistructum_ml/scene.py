from dataclasses import dataclass

import numpy as np

UNKNOWN = -1


@dataclass
class Scene:
    blocks: np.ndarray
    heights: np.ndarray
    luminance: np.ndarray
    modified: np.ndarray

    def __post_init__(self):
        self.blocks = np.asarray(self.blocks, dtype=np.int32)
        self.heights = np.asarray(self.heights, dtype=np.int32)
        self.luminance = np.asarray(self.luminance, dtype=np.uint8)
        self.modified = np.asarray(self.modified, dtype=bool)
        shape = self.blocks.shape
        if len(shape) != 2:
            raise ValueError(f"scene must be 2d, got {shape}")
        for name in ("heights", "luminance", "modified"):
            if getattr(self, name).shape != shape:
                raise ValueError(f"{name} shape {getattr(self, name).shape} != blocks shape {shape}")

    @property
    def shape(self):
        return self.blocks.shape

    def crop(self, top, left, size):
        cut = (slice(top, top + size), slice(left, left + size))
        return Scene(self.blocks[cut], self.heights[cut], self.luminance[cut], self.modified[cut])
