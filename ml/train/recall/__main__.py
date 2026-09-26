import argparse
import json
import os
import sys
from pathlib import Path

from . import plant, score
from .rcon import Rcon

DB_NOTE = ("The live plugin DB is never opened: the tool copies vistructum.db and vistructum.db-wal into a temp "
           "directory and reads the copy, because opening a WAL database that a server container writes through a "
           "Docker Desktop bind mount corrupts it. Copy while the server is idle or stopped for a consistent snapshot.")


def _csv(value):
    return tuple(item.strip() for item in value.split(",") if item.strip())


def _axes(value):
    axes = tuple(axis.upper() for axis in _csv(value))
    unknown = set(axes) - set(plant.AXES)
    if unknown:
        raise argparse.ArgumentTypeError(f"unknown axes {sorted(unknown)}")
    return axes


def _plant(args):
    if args.dry_run:
        plant.run(None, args)
        return 0
    password = args.password or os.environ.get("RCON_PASSWORD")
    if not password:
        print("missing rcon password: pass --password or set RCON_PASSWORD", file=sys.stderr)
        return 2
    with Rcon(args.host, args.port, password) as client:
        plant.run(client, args)
    return 0


def _score(args):
    truth = json.loads(Path(args.truth).read_text())
    findings = score.load_findings(args.db, truth["world"], args.after_id, args.source)
    result = score.report(truth, findings, args.min_cover, args.y_tolerance)
    Path(args.out).write_text(json.dumps(result, indent=2))
    sys.stdout.write(score.markdown(result))
    return 0


def parser():
    root = argparse.ArgumentParser(prog="python -m recall",
                                   description="measure fullscan recall with planted swastikas on a real world",
                                   epilog=DB_NOTE)
    commands = root.add_subparsers(dest="command", required=True)

    planting = commands.add_parser("plant", help="place symbols over RCON and write the ground truth JSON",
                                   description="place symbols over RCON and write the ground truth JSON; runs "
                                               "save-all flush at the end so the scan reads them from region files")
    planting.add_argument("--host", default="localhost")
    planting.add_argument("--port", type=int, default=25575)
    planting.add_argument("--password", default=None, help="defaults to the RCON_PASSWORD environment variable")
    planting.add_argument("--world", default="world", help="world name as the plugin stores it in findings.world")
    planting.add_argument("--dimension", default="minecraft:overworld", help="dimension key for execute in")
    planting.add_argument("--area", type=int, nargs=4, required=True, metavar=("MIN_X", "MIN_Z", "MAX_X", "MAX_Z"))
    planting.add_argument("--count", type=int, default=500)
    planting.add_argument("--seed", type=int, default=0)
    planting.add_argument("--spacing", type=int, default=64, help="minimum horizontal gap between structures")
    planting.add_argument("--min-size", type=int, default=7)
    planting.add_argument("--max-extent", type=int, default=61, help="largest placed side, rotation included")
    planting.add_argument("--materials", type=_csv, default=plant.MATERIALS)
    planting.add_argument("--axes", type=_axes, default=plant.AXES, help="normal axes: Y flat, X and Z standing")
    planting.add_argument("--placement", choices=("surface", "air"), default="surface",
                          help="surface: on the highest heightmap sample of the footprint; air: random y in --y-range")
    planting.add_argument("--heightmap", default="motion_blocking",
                          choices=("motion_blocking", "motion_blocking_no_leaves", "world_surface", "ocean_floor"))
    planting.add_argument("--lift", type=int, default=0, help="blocks above the highest surface sample")
    planting.add_argument("--y-range", type=int, nargs=2, default=(120, 200), metavar=("MIN_Y", "MAX_Y"))
    planting.add_argument("--out", default="recall-truth.json")
    planting.add_argument("--dry-run", action="store_true", help="print commands instead of connecting")
    planting.set_defaults(handler=_plant)

    scoring = commands.add_parser("score", help="match findings against the ground truth", description=DB_NOTE)
    scoring.add_argument("--truth", required=True)
    scoring.add_argument("--db", required=True, help="path to vistructum.db; only a temp copy is opened")
    scoring.add_argument("--out", default="recall-report.json")
    scoring.add_argument("--min-cover", type=float, default=score.MIN_COVER,
                         help="share of the structure footprint a finding box must cover")
    scoring.add_argument("--y-tolerance", type=int, default=score.Y_TOLERANCE)
    scoring.add_argument("--after-id", type=int, default=0, help="ignore findings with id <= this value")
    scoring.add_argument("--source", default=None, help="only findings of this source, e.g. FULLSCAN")
    scoring.set_defaults(handler=_score)
    return root


def main():
    args = parser().parse_args()
    return args.handler(args)


if __name__ == "__main__":
    sys.exit(main())
