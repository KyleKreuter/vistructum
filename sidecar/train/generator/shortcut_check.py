import argparse
import json
from pathlib import Path

import numpy as np

HEIGHT_CLIP = 4


def _bbox_stats(binary):
    rows, cols = np.nonzero(binary)
    if len(rows) == 0:
        return 0.0, 0.0, 0.0, 0.0
    on_frac = float(binary.mean())
    h = float(rows.max() - rows.min() + 1)
    w = float(cols.max() - cols.min() + 1)
    return on_frac, h * w, h, w


def sample_features(x):
    channels = x.shape[0]
    if channels == 1:
        on_frac, area, h, w = _bbox_stats(x[0] > 0)
        return np.array([on_frac, area, h, w], dtype=np.float64)
    boundary = x[1] > 0
    on_frac, area, h, w = _bbox_stats(boundary)
    rel = x[0].astype(np.int32) - HEIGHT_CLIP
    mean_abs_rel = float(np.abs(rel).mean())
    frac_nonzero_rel = float((rel != 0).mean())
    lum_std = float(x[2].astype(np.float32).std())
    return np.array([on_frac, area, h, w, mean_abs_rel, frac_nonzero_rel, lum_std], dtype=np.float64)


FEATURE_NAMES_MASK = ("on_frac", "bbox_area", "bbox_h", "bbox_w")
FEATURE_NAMES_FULLSCAN = FEATURE_NAMES_MASK + ("mean_abs_rel_height", "frac_rel_height_nonzero", "luminance_std")


def feature_matrix(x):
    return np.stack([sample_features(sample) for sample in x])


def _standardize(x, mean, std):
    safe_std = np.where(std < 1e-8, 1.0, std)
    return (x - mean) / safe_std


def _add_quadratic(x):
    return np.concatenate([x, x ** 2], axis=1)


def _fit_logreg(x, y, epochs=3000, lr=0.3, l2=1e-3):
    n, d = x.shape
    xb = np.concatenate([np.ones((n, 1)), x], axis=1)
    w = np.zeros(d + 1)
    for _ in range(epochs):
        z = xb @ w
        p = 1.0 / (1.0 + np.exp(-np.clip(z, -30, 30)))
        grad = xb.T @ (p - y) / n
        grad[1:] += l2 * w[1:]
        w -= lr * grad
    return w


def _predict(x, w):
    n = x.shape[0]
    xb = np.concatenate([np.ones((n, 1)), x], axis=1)
    z = xb @ w
    return 1.0 / (1.0 + np.exp(-np.clip(z, -30, 30)))


def auc_score(scores, labels):
    scores = np.asarray(scores, dtype=np.float64)
    labels = np.asarray(labels)
    order = np.argsort(scores, kind="mergesort")
    ranks = np.empty(len(scores), dtype=np.float64)
    sorted_scores = scores[order]
    i = 0
    while i < len(sorted_scores):
        j = i
        while j + 1 < len(sorted_scores) and sorted_scores[j + 1] == sorted_scores[i]:
            j += 1
        ranks[order[i:j + 1]] = (i + 1 + j + 1) / 2.0
        i = j + 1
    positive = labels == 1
    n_pos = int(positive.sum())
    n_neg = int((~positive).sum())
    if n_pos == 0 or n_neg == 0:
        return 0.5
    sum_ranks_pos = ranks[positive].sum()
    return float((sum_ranks_pos - n_pos * (n_pos + 1) / 2.0) / (n_pos * n_neg))


def compute_shortcut_auc(data_dir):
    data_dir = Path(data_dir)
    train = np.load(data_dir / "train.npz", allow_pickle=True)
    test = np.load(data_dir / "test.npz", allow_pickle=True)

    x_train = feature_matrix(train["x"])
    y_train = train["y"].astype(np.float64)
    x_test = feature_matrix(test["x"])
    y_test = test["y"].astype(np.float64)

    mean, std = x_train.mean(axis=0), x_train.std(axis=0)
    x_train_std = _add_quadratic(_standardize(x_train, mean, std))
    x_test_std = _add_quadratic(_standardize(x_test, mean, std))

    w = _fit_logreg(x_train_std, y_train)
    scores = _predict(x_test_std, w)
    auc = auc_score(scores, y_test)

    channels = train["x"].shape[1]
    names = FEATURE_NAMES_MASK if channels == 1 else FEATURE_NAMES_FULLSCAN
    means_by_label = {}
    for label in (0, 1):
        rows = x_train[y_train == label]
        if len(rows):
            means_by_label[int(label)] = dict(zip(names, rows.mean(axis=0).tolist()))
    return auc, {"feature_names": names, "mean_by_label": means_by_label, "n_train": len(y_train), "n_test": len(y_test)}


def main():
    parser = argparse.ArgumentParser(
        description="check whether a build.py dataset has a size/density shortcut: "
                     "fit a tiny standardized+quadratic logistic regression on simple "
                     "on-fraction/bbox stats (train.npz) and report its AUC on test.npz. "
                     "AUC should be close to 0.5; a high AUC means the label leaks through "
                     "shape size/density alone.")
    parser.add_argument("data_dir", help="directory with train.npz and test.npz (a build.py --out dir)")
    parser.add_argument("--max-auc", type=float, default=0.60)
    args = parser.parse_args()

    auc, info = compute_shortcut_auc(args.data_dir)
    verdict = "PASS" if auc <= args.max_auc else "FAIL"
    report = {"data_dir": str(args.data_dir), "auc": auc, "max_auc": args.max_auc, "verdict": verdict, **info}
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
