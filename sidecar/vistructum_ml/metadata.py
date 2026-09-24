import json

from .contract import (
    FEATURE_SPEC,
    KINDS,
    LABELS,
    META_FEATURE_SPEC,
    META_KIND,
    META_LABELS,
    META_MIN_VOTES,
    META_PREFILTER,
    META_THRESHOLD,
    META_TTA,
    META_VERSION,
    REQUIRED_META,
)


class ContractError(ValueError):
    pass


def validate(meta):
    missing = [key for key in REQUIRED_META if key not in meta]
    if missing:
        raise ContractError(f"model metadata missing: {', '.join(missing)}")
    kind = meta[META_KIND]
    if kind not in KINDS:
        raise ContractError(f"unknown model kind {kind!r}")
    if meta[META_VERSION] != KINDS[kind].version:
        # the version fixes the channel layout: an older model would get inputs it was never trained on
        raise ContractError(f"{kind} model version {meta[META_VERSION]!r} != {KINDS[kind].version!r}")
    if meta[META_FEATURE_SPEC] != FEATURE_SPEC:
        raise ContractError(f"feature spec {meta[META_FEATURE_SPEC]!r} != {FEATURE_SPEC!r}")
    labels = tuple(json.loads(meta[META_LABELS]))
    if labels != LABELS:
        raise ContractError(f"labels {labels} != {LABELS}")
    threshold = float(meta[META_THRESHOLD])
    if not 0.0 < threshold < 1.0:
        raise ContractError(f"threshold {threshold} outside (0, 1)")
    min_votes = int(meta.get(META_MIN_VOTES, "1"))
    if min_votes < 1:
        raise ContractError(f"min_votes {min_votes} < 1")
    tta = meta.get(META_TTA, "0")
    if tta not in ("0", "1"):
        raise ContractError(f"tta {tta!r} is neither '0' nor '1'")
    prefilter = meta.get(META_PREFILTER)
    if prefilter is not None:
        prefilter = float(prefilter)
        if tta != "1":
            raise ContractError("a prefilter needs tta")
        # a window below the prefilter keeps its single-view score, which must then be below the threshold as well
        if not 0.0 < prefilter <= threshold:
            raise ContractError(f"prefilter {prefilter} outside (0, threshold {threshold}]")
    return {"kind": kind, "version": meta[META_VERSION], "labels": labels, "threshold": threshold,
            "min_votes": min_votes, "tta": tta == "1", "prefilter": prefilter}


def read_session_metadata(session):
    return dict(session.get_modelmeta().custom_metadata_map)
