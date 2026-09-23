from pathlib import Path

import numpy as np
import torch
from torch.utils.data import DataLoader, TensorDataset


def load_split(data_dir, name):
    raw = np.load(Path(data_dir) / f"{name}.npz")
    x = torch.from_numpy(np.ascontiguousarray(raw["x"]))
    y = torch.from_numpy(raw["y"].astype(np.int64))
    return x, y


def make_loader(data_dir, name, batch, shuffle, generator=None):
    x, y = load_split(data_dir, name)
    drop_last = len(y) > batch and len(y) % batch == 1
    return DataLoader(TensorDataset(x, y), batch_size=batch, shuffle=shuffle, generator=generator, drop_last=drop_last)


def d4_augment(batch, generator=None):
    out = batch.clone()
    n = out.shape[0]
    ks = torch.randint(0, 4, (n,), generator=generator)
    flips = torch.randint(0, 2, (n,), generator=generator)
    for i in range(n):
        img = out[i]
        k = int(ks[i])
        if k:
            img = torch.rot90(img, k, dims=(1, 2))
        if int(flips[i]):
            img = torch.flip(img, dims=(2,))
        out[i] = img
    return out
