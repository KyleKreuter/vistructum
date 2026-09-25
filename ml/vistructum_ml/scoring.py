import numpy as np

from .contract import INPUT_NAME, OUTPUT_NAME, POSITIVE

BATCH_SIZE = 64
VIEWS = 8


def d4_views(x):
    t = x.transpose(0, 1, 3, 2)
    views = (x, x[..., ::-1], x[..., ::-1, :], x[..., ::-1, ::-1], t, t[..., ::-1], t[..., ::-1, :], t[..., ::-1, ::-1])
    return np.ascontiguousarray(np.concatenate(views))


def single_view(session, batch, batch_size=BATCH_SIZE):
    scores = np.empty(len(batch), dtype=np.float64)
    for i in range(0, len(batch), batch_size):
        scores[i:i + batch_size] = session.run([OUTPUT_NAME], {INPUT_NAME: batch[i:i + batch_size]})[0][:, POSITIVE]
    return scores


def all_views(session, batch, batch_size=BATCH_SIZE):
    step = max(1, batch_size // VIEWS)
    scores = np.empty(len(batch), dtype=np.float64)
    for i in range(0, len(batch), step):
        chunk = batch[i:i + step]
        probs = session.run([OUTPUT_NAME], {INPUT_NAME: d4_views(chunk)})[0][:, POSITIVE]
        scores[i:i + len(chunk)] = probs.reshape(VIEWS, len(chunk)).mean(axis=0)
    return scores


def score(session, batch, tta=False, prefilter=None, batch_size=BATCH_SIZE):
    if not tta:
        return single_view(session, batch, batch_size), 0
    if prefilter is None:
        return all_views(session, batch, batch_size), len(batch)
    scores = single_view(session, batch, batch_size)
    passed = np.flatnonzero(scores >= prefilter)
    if len(passed):
        scores[passed] = all_views(session, batch[passed], batch_size)
    return scores, len(passed)
