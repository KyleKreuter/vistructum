import numpy as np

from .contract import GRID


def _overlaps(a, b, size):
    return abs(a[0] - b[0]) < size and abs(a[1] - b[1]) < size


def clusters(positions, scores, threshold, size=GRID):
    hits = [i for i, score in enumerate(scores) if score >= threshold]
    parent = {i: i for i in hits}

    def find(i):
        while parent[i] != i:
            parent[i] = parent[parent[i]]
            i = parent[i]
        return i

    for a_index, a in enumerate(hits):
        for b in hits[a_index + 1:]:
            if _overlaps(positions[a], positions[b], size):
                parent[find(a)] = find(b)
    groups = {}
    for i in hits:
        groups.setdefault(find(i), []).append(i)
    result = []
    for members in groups.values():
        tops = [positions[i][0] for i in members]
        lefts = [positions[i][1] for i in members]
        result.append({
            "top": int(min(tops)),
            "left": int(min(lefts)),
            "bottom": int(max(tops) + size),
            "right": int(max(lefts) + size),
            "votes": len(members),
            "score": float(np.max([scores[i] for i in members])),
            "windows": [tuple(int(v) for v in positions[i]) for i in members],
        })
    result.sort(key=lambda c: c["score"], reverse=True)
    return result


def flagged(positions, scores, threshold, min_votes, size=GRID):
    return [c for c in clusters(positions, scores, threshold, size) if c["votes"] >= min_votes]
