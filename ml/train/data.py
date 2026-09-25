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
    n = batch.shape[0]
    ks = torch.randint(0, 4, (n,), generator=generator)
    flips = torch.randint(0, 2, (n,), generator=generator)
    out = batch.clone()
    for k in range(4):
        for flip in (0, 1):
            index = torch.nonzero((ks == k) & (flips == flip)).squeeze(1).to(batch.device)
            if index.numel() == 0 or (k == 0 and not flip):
                continue
            group = torch.rot90(batch.index_select(0, index), k, dims=(2, 3))
            if flip:
                group = torch.flip(group, dims=(3,))
            out.index_copy_(0, index, group)
    return out
