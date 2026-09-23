import numpy as np

SPLIT_SEED_STRIDE = 10_000_000


def split_offset(split):
    return {"train": 0, "val": 1, "test": 2, "holdout": 3, "calib": 4, "scan": 5}[split] * SPLIT_SEED_STRIDE


def sample_seed(seed, split, index):
    return int(seed) + split_offset(split) + int(index)


def rng_for(seed, split, index):
    entropy = (int(seed), split_offset(split), int(index))
    return np.random.default_rng(np.random.SeedSequence(entropy))
