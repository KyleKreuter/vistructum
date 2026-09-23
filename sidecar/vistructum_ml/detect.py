import numpy as np

from .contract import GRID


def _components(points, size):
    rows = points[:, 0]
    cols = points[:, 1]
    adjacent = (np.abs(rows[:, None] - rows[None, :]) < size) & (np.abs(cols[:, None] - cols[None, :]) < size)
    labels = np.arange(len(points))
    while True:
        merged = np.where(adjacent, labels[None, :], len(points)).min(axis=1)
        if np.array_equal(merged, labels):
            return labels
        labels = merged


def clusters(positions, scores, threshold, size=GRID):
    scores = np.asarray(scores)
    hits = np.flatnonzero(scores >= threshold)
    if len(hits) == 0:
        return []
    points = np.asarray(positions, dtype=np.int64).reshape(-1, 2)[hits]
    labels = _components(points, size)
    result = []
    for label in np.unique(labels):
        members = labels == label
        member_points = points[members]
        result.append({
            "top": int(member_points[:, 0].min()),
            "left": int(member_points[:, 1].min()),
            "bottom": int(member_points[:, 0].max() + size),
            "right": int(member_points[:, 1].max() + size),
            "votes": int(members.sum()),
            "score": float(scores[hits[members]].max()),
            "windows": [tuple(int(v) for v in point) for point in member_points],
        })
    result.sort(key=lambda c: c["score"], reverse=True)
    return result


def flagged(positions, scores, threshold, min_votes, size=GRID):
    return [c for c in clusters(positions, scores, threshold, size) if c["votes"] >= min_votes]
