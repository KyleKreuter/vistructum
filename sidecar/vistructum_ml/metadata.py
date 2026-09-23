import json

from .contract import FEATURE_SPEC, KINDS, LABELS, META_FEATURE_SPEC, META_KIND, META_LABELS, META_THRESHOLD, META_VERSION, REQUIRED_META


class ContractError(ValueError):
    pass


def validate(meta):
    missing = [key for key in REQUIRED_META if key not in meta]
    if missing:
        raise ContractError(f"model metadata missing: {', '.join(missing)}")
    kind = meta[META_KIND]
    if kind not in KINDS:
        raise ContractError(f"unknown model kind {kind!r}")
    if meta[META_FEATURE_SPEC] != FEATURE_SPEC:
        raise ContractError(f"feature spec {meta[META_FEATURE_SPEC]!r} != {FEATURE_SPEC!r}")
    labels = tuple(json.loads(meta[META_LABELS]))
    if labels != LABELS:
        raise ContractError(f"labels {labels} != {LABELS}")
    threshold = float(meta[META_THRESHOLD])
    if not 0.0 < threshold < 1.0:
        raise ContractError(f"threshold {threshold} outside (0, 1)")
    return {"kind": kind, "version": meta[META_VERSION], "labels": labels, "threshold": threshold}


def read_session_metadata(session):
    return dict(session.get_modelmeta().custom_metadata_map)
