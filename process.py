"""Runs all the plotters, or some of them, in parallel.

    python process.py                         # all: metrics, snapshots, gifs, fairness
    python process.py metrics snapshots       # some (also by initial: M S G F)
    python process.py S G --where shape=star  # options are passed to the plotters that accept them
    python process.py S --times 0 700 1500    # instants of the snapshots (default: one every 50)
    python process.py S --every 100           # one snapshot every 100
    python process.py G --until 1000          # gifs up to t = 1000 (also --from, --fps, --frame-step)
    python process.py --sequential            # one plotter at a time (less memory)

Plotters (all in python/, each can also be run alone, see its --help):
- metrics   (M) plot_dynamic.py            metrics over time, a line per scenario   -> charts/dynamic/
- snapshots (S) plot_positions_snapshots.py devices at some instants, by SDF         -> charts/positions/
- gifs      (G) plot_positions_gif.py       devices over time, by SDF                -> charts/positions/
- fairness  (F) plot_positions_fairness.py  shape split among the devices, by share  -> charts/positions/
"""

import argparse
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
PYTHON = ROOT / "python"

PLOTTERS = {  # name -> (initial, script, options it accepts, default extra arguments)
    "metrics": ("M", "plot_dynamic.py", {"format", "no_cache"}, []),
    "snapshots": ("S", "plot_positions_snapshots.py",
                  {"scenario", "where", "all_seeds", "shape_overlay", "format", "no_cache", "every", "range", "times"}, ["--combined"]),
    "gifs": ("G", "plot_positions_gif.py",
             {"scenario", "where", "all_seeds", "shape_overlay", "no_cache", "range", "fps", "frame_step"}, []),
    "fairness": ("F", "plot_positions_fairness.py",
                 {"scenario", "where", "all_seeds", "format", "no_cache", "every", "range", "times"}, []),
}


def selected(requested: list[str]) -> list[str]:
    if not requested:
        return list(PLOTTERS)
    by_key = {name: name for name in PLOTTERS} | {initial: name for name, (initial, *_) in PLOTTERS.items()}
    chosen = []
    for item in requested:
        name = by_key.get(item.lower()) or by_key.get(item.upper())
        if name is None:
            raise SystemExit(f"Unknown plotter {item!r}: use {', '.join(PLOTTERS)} (or M, S, G, F)")
        if name not in chosen:
            chosen.append(name)
    return chosen


def arguments(name: str, args: argparse.Namespace) -> list[str]:
    _, _, accepted, extra = PLOTTERS[name]
    result = list(extra)
    if "times" in accepted and args.times:
        result = [str(t) for t in args.times] + result
    if "every" in accepted and args.every:
        result += ["--every", str(args.every)]
    if "range" in accepted and args.start is not None:
        result += ["--from", str(args.start)]
    if "range" in accepted and args.until is not None:
        result += ["--until", str(args.until)]
    if "fps" in accepted and args.fps:
        result += ["--fps", str(args.fps)]
    if "frame_step" in accepted and args.frame_step:
        result += ["--frame-step", str(args.frame_step)]
    if "scenario" in accepted and args.scenario:
        result += ["--scenario", *args.scenario]
    if "where" in accepted and args.where:
        result += ["--where", *args.where]
    if "all_seeds" in accepted and args.all_seeds:
        result.append("--all-seeds")
    if "shape_overlay" in accepted and args.shape_overlay:
        result += ["--shape-overlay", args.shape_overlay]
    if "format" in accepted and args.format:
        result += ["--format", *args.format]
    if "no_cache" in accepted and args.no_cache:
        result.append("--no-cache")
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("plotters", nargs="*", help="metrics, snapshots, gifs, fairness (or M, S, G, F); default: all")
    parser.add_argument("--times", nargs="+", type=float, help="instants of the snapshots and of the fairness charts")
    parser.add_argument("--every", type=float, help="without --times, one snapshot (or fairness chart) every N time units")
    parser.add_argument("--from", dest="start", type=float, help="first instant of the gifs (and of the snapshots)")
    parser.add_argument("--until", type=float, help="last instant of the gifs (and of the snapshots)")
    parser.add_argument("--fps", type=int, help="frames per second of the gifs (default 10)")
    parser.add_argument("--frame-step", type=int, help="gifs: one frame every N exports (default 1)")
    parser.add_argument("--scenario", nargs="+", help="data/positions folders, for snapshots and gifs")
    parser.add_argument("--where", nargs="+", metavar="NAME=VALUES", help="filters on the runs, e.g. shape=star,ring")
    parser.add_argument("--all-seeds", action="store_true", help="snapshots and gifs of every seed")
    parser.add_argument("--shape-overlay", choices=["off", "on", "both"], help="shape in the background")
    parser.add_argument("--format", nargs="+", help="formats of the charts (default: pdf png)")
    parser.add_argument("--no-cache", action="store_true", help="parse the data again")
    parser.add_argument("--sequential", action="store_true", help="one plotter at a time")
    args = parser.parse_args()

    (ROOT / "charts").mkdir(exist_ok=True)
    running = []
    for name in selected(args.plotters):
        command = [sys.executable, str(PYTHON / PLOTTERS[name][1]), *arguments(name, args)]
        print(f"[process] {name}: {' '.join(command[1:])}")
        process = subprocess.Popen(command, cwd=ROOT)
        if args.sequential:
            process.wait()
        running.append((name, process))
    failed = [(name, code) for name, process in running if (code := process.wait()) != 0]
    if failed:
        raise SystemExit("[process] failed: " + ", ".join(f"{name} (exit {code})" for name, code in failed))
    print("[process] all the plotters finished")


if __name__ == "__main__":
    main()
