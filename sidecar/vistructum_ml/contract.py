from dataclasses import dataclass

GRID = 64
STRIDE = 48
LABELS = ("ok", "hakenkreuz")
POSITIVE = 1
FEATURE_SPEC = "fs-1"

HEIGHT_CLIP = 4
HEIGHT_CONTEXT = 31
HEIGHT_SAMPLE_STEP = 5


@dataclass(frozen=True)
class ModelKind:
    name: str
    version: str
    channels: int
    scale: tuple
    offset: tuple


MASK = ModelKind("mask", "bf-mask-1", 1, (1.0,), (0.0,))
FULLSCAN = ModelKind(
    "fullscan",
    "bf-scan-1",
    3,
    (1.0 / HEIGHT_CLIP, 1.0, 1.0 / 64.0),
    (-1.0, 0.0, -2.0),
)
KINDS = {kind.name: kind for kind in (MASK, FULLSCAN)}

META_VERSION = "vistructum.model_version"
META_KIND = "vistructum.kind"
META_LABELS = "vistructum.labels"
META_FEATURE_SPEC = "vistructum.feature_spec"
META_THRESHOLD = "vistructum.threshold"
META_COMMIT = "vistructum.commit"
META_CONFIG = "vistructum.config"
META_METRICS = "vistructum.metrics"
REQUIRED_META = (META_VERSION, META_KIND, META_LABELS, META_FEATURE_SPEC, META_THRESHOLD)

INPUT_NAME = "features"
OUTPUT_NAME = "scores"
