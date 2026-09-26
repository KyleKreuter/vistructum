import shutil
import sqlite3
import tempfile
from pathlib import Path

MIN_COVER = 0.5
Y_TOLERANCE = 2
SIZE_BUCKETS = ((15, "small <=15"), (31, "medium 16-31"), (None, "large >=32"))
BREAKDOWNS = ("material", "size_bucket", "thickness", "axis", "handedness", "rotation")
FINDING_COLUMNS = ("id", "source", "world", "min_x", "min_y", "min_z", "max_x", "max_y", "max_z", "score", "detail")


def size_bucket(extent):
    for limit, label in SIZE_BUCKETS:
        if limit is None or extent <= limit:
            return label


def finding_path(detail):
    if detail.startswith("Volumen"):
        return "volume"
    if detail.startswith("Kachel"):
        return "surface"
    return "live"


def span_overlap(low_a, high_a, low_b, high_b):
    return max(0, min(high_a, high_b) - max(low_a, low_b) + 1)


def cover(structure, finding, y_tolerance=Y_TOLERANCE):
    s_min, s_max = structure["min"], structure["max"]
    if span_overlap(s_min[1] - y_tolerance, s_max[1] + y_tolerance, finding["min_y"], finding["max_y"]) == 0:
        return 0.0
    covered = (span_overlap(s_min[0], s_max[0], finding["min_x"], finding["max_x"])
               * span_overlap(s_min[2], s_max[2], finding["min_z"], finding["max_z"]))
    return covered / ((s_max[0] - s_min[0] + 1) * (s_max[2] - s_min[2] + 1))


def in_area(finding, area):
    min_x, min_z, max_x, max_z = area
    return (span_overlap(min_x, max_x, finding["min_x"], finding["max_x"]) > 0
            and span_overlap(min_z, max_z, finding["min_z"], finding["max_z"]) > 0)


def match(structures, findings, min_cover=MIN_COVER, y_tolerance=Y_TOLERANCE):
    hits = {structure["id"]: [] for structure in structures}
    classes = {}
    for finding in findings:
        covers = [(structure["id"], cover(structure, finding, y_tolerance)) for structure in structures
                  if structure["world"] == finding["world"]]
        matched = [sid for sid, value in covers if value >= min_cover]
        for sid in matched:
            hits[sid].append(finding)
        if matched:
            classes[finding["id"]] = "matched"
        elif any(value > 0 for _, value in covers):
            classes[finding["id"]] = "partial"
        else:
            classes[finding["id"]] = "background"
    return hits, classes


def recall_row(found, planted):
    return {"planted": planted, "found": found, "recall": round(found / planted, 4) if planted else 0.0}


def breakdown(structures, hits, key):
    groups = {}
    for structure in structures:
        value = size_bucket(structure["extent"]) if key == "size_bucket" else structure[key]
        entry = groups.setdefault(str(value), [0, 0])
        entry[0] += 1
        entry[1] += bool(hits[structure["id"]])
    return {value: recall_row(found, planted) for value, (planted, found) in sorted(groups.items(), key=_order)}


def _order(item):
    value = item[0]
    return (0, int(value), "") if value.lstrip("-").isdigit() else (1, 0, value)


def report(truth, findings, min_cover=MIN_COVER, y_tolerance=Y_TOLERANCE):
    structures = truth["structures"]
    hits, classes = match(structures, findings, min_cover, y_tolerance)
    found = sum(bool(v) for v in hits.values())
    paths = {}
    for structure in structures:
        kinds = sorted({finding_path(f["detail"]) for f in hits[structure["id"]]})
        label = "+".join(kinds) if kinds else "missed"
        paths[label] = paths.get(label, 0) + 1
    background = [f for f in findings if classes[f["id"]] == "background"]
    area = truth.get("area")
    return {
        "world": truth.get("world"),
        "min_cover": min_cover,
        "y_tolerance": y_tolerance,
        "overall": recall_row(found, len(structures)),
        "by": {key: breakdown(structures, hits, key) for key in BREAKDOWNS},
        "found_by_path": dict(sorted(paths.items())),
        "findings": {
            "total": len(findings),
            "matched": sum(c == "matched" for c in classes.values()),
            "partial": sum(c == "partial" for c in classes.values()),
            "background": len(background),
            "background_in_area": sum(in_area(f, area) for f in background) if area else None,
            "background_by_path": _count(finding_path(f["detail"]) for f in background),
        },
        "missed": [s["id"] for s in structures if not hits[s["id"]]],
        "background_ids": [f["id"] for f in background],
    }


def _count(values):
    counts = {}
    for value in values:
        counts[value] = counts.get(value, 0) + 1
    return dict(sorted(counts.items()))


def snapshot(db_path, directory):
    source = Path(db_path)
    target = Path(directory) / source.name
    shutil.copy2(source, target)
    wal = source.with_name(source.name + "-wal")
    if wal.exists():
        shutil.copy2(wal, target.with_name(target.name + "-wal"))
    return target


def load_findings(db_path, world, after_id=0, source=None):
    with tempfile.TemporaryDirectory(prefix="vistructum-recall-") as directory:
        connection = sqlite3.connect(snapshot(db_path, directory))
        try:
            sql = f"SELECT {', '.join(FINDING_COLUMNS)} FROM findings WHERE world = ? AND id > ?"
            arguments = [world, after_id]
            if source:
                sql += " AND upper(source) = upper(?)"
                arguments.append(source)
            rows = connection.execute(sql + " ORDER BY id", arguments).fetchall()
        finally:
            connection.close()
    return [dict(zip(FINDING_COLUMNS, row)) for row in rows]


def markdown(result):
    overall = result["overall"]
    lines = [f"# Recall {result['world']}", "",
             (f"Recall **{overall['recall']:.3f}** ({overall['found']}/{overall['planted']}), "
              f"match = finding covers >= {result['min_cover']:.0%} of the structure footprint, "
              f"y tolerance {result['y_tolerance']}"), ""]
    for key, rows in result["by"].items():
        lines += [f"| {key} | planted | found | recall |", "|---|---:|---:|---:|"]
        lines += [f"| {value} | {row['planted']} | {row['found']} | {row['recall']:.3f} |" for value, row in rows.items()]
        lines.append("")
    lines += ["| found by | structures |", "|---|---:|"]
    lines += [f"| {path} | {count} |" for path, count in result["found_by_path"].items()]
    lines.append("")
    findings = result["findings"]
    lines += ["| findings | count |", "|---|---:|"]
    lines += [f"| {key} | {findings[key]} |" for key in ("total", "matched", "partial", "background",
                                                          "background_in_area")]
    lines += [f"| background {path} | {count} |" for path, count in findings["background_by_path"].items()]
    return "\n".join(lines) + "\n"
