"""Snapshots of the dynamic-population runs: the devices at some instants, colored by the SDF of the shape at their
position (warm deep inside, cold outside), with the leader (star) and the anchors (diamonds), and optionally the shape.

    python python/plot_positions_snapshots.py                     # one every 50 time units, first seed of each run
    python python/plot_positions_snapshots.py --every 100         # one every 100
    python python/plot_positions_snapshots.py --until 1000        # one every 50, up to t = 1000
    python python/plot_positions_snapshots.py 0 690 710 1500     # these instants (the nearest export)
    python python/plot_positions_snapshots.py --scenario dynamicPositionBased --where shape=star start=left
    python python/plot_positions_snapshots.py --combined         # also all the instants in one figure (6 per row)
    python python/plot_positions_snapshots.py --shape-overlay on # only the version with the shape
    python python/plot_positions_snapshots.py --all-seeds --trail 5 --links 30

Charts: charts/positions/<scenario>/<run>/<fmt>/<run>_t<time>[_shape].<fmt> (and <run>_snapshots[_shape].<fmt>),
where _shape is the version with the shape in the background.
"""

from __future__ import annotations

import argparse
import math

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

import positions as pos
import render


def default_times(run: pos.Run, every: float, start: float | None = None, until: float | None = None) -> list[float]:
    """An instant every [every] time units, from [start] (default: the beginning) to [until] (default: the end of
    the run), the nearest exports."""
    first = 0.0 if start is None else start
    last = float(run.times[-1]) if until is None else min(until, float(run.times[-1]))
    wanted = list(np.arange(first, last + every / 2, every)) + [last]
    return sorted({run.nearest_time(t) for t in wanted if t <= last + 1e-9})


COMBINED_COLUMNS = 6  # Panels per row of the combined figure


def overlays(choice: str) -> list[bool]:
    return {"off": [False], "on": [True], "both": [False, True]}[choice]


def save(fig, run: pos.Run, stem: str, formats: list[str], dpi: int) -> None:
    for fmt in formats:
        path = pos.CHARTS_DIR / run.scenario / run.name / fmt / f"{stem}.{fmt}"
        path.parent.mkdir(parents=True, exist_ok=True)
        fig.savefig(path, bbox_inches="tight", dpi=dpi)
    plt.close(fig)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("times", nargs="*", type=float, help="simulation times (default: one every --every)")
    parser.add_argument("--every", type=float, default=50.0, help="without times, one snapshot every N (default 50)")
    parser.add_argument("--from", dest="start", type=float, default=None, help="without times, the first instant")
    parser.add_argument("--until", type=float, default=None, help="without times, the last instant")
    parser.add_argument("--scenario", nargs="+", help="data/positions folders (default: all)")
    parser.add_argument("--where", nargs="+", metavar="NAME=VALUES", help="filters on the header, e.g. shape=star,ring")
    parser.add_argument("--all-seeds", action="store_true", help="every seed (default: the first of each run)")
    parser.add_argument("--shape-overlay", choices=["off", "on", "both"], default="both",
                        help="draw the shape in the background: without, with, or both versions (default)")
    parser.add_argument("--combined", action="store_true", help="also one figure with all the instants (6 per row)")
    parser.add_argument("--trail", type=int, default=0, help="draw the last N exports of each device")
    parser.add_argument("--links", type=float, default=None, help="draw the links shorter than this distance")
    parser.add_argument("--cmap", default=render.DEFAULT_CMAP, help="colormap of the SDF (default: viridis_r)")
    parser.add_argument("--format", nargs="+", default=["pdf", "png"])
    parser.add_argument("--dpi", type=int, default=200)
    parser.add_argument("--no-cache", action="store_true")
    args = parser.parse_args()

    selected = pos.select_runs(args.scenario, pos.parse_filters(args.where), args.all_seeds)
    if not selected:
        raise SystemExit(f"No run in {pos.POSITIONS_DIR} matches")
    for scenario, path in selected:
        run = pos.load_run(scenario, path, use_cache=not args.no_cache)
        if run.devices.empty:
            continue
        events = pos.population_events(scenario)
        times = sorted({run.nearest_time(t) for t in args.times}) if args.times else default_times(run, args.every, args.start, args.until)
        norm = render.color_norm(run)
        for shape in overlays(args.shape_overlay):
            suffix = "_shape" if shape else ""
            for t in times:
                fig, ax = plt.subplots(figsize=render.figure_size(run), layout="constrained")
                render.draw_frame(ax, run, t, show_shape=shape, cmap=args.cmap, norm=norm, trail=args.trail,
                                  links=args.links, events=events)
                render.add_colorbar(fig, ax, args.cmap, norm)
                fig.suptitle(render.suptitle(run), fontsize=11)
                save(fig, run, f"{run.name}_t{int(round(t)):04d}{suffix}", args.format, args.dpi)
            if args.combined:
                columns = min(len(times), COMBINED_COLUMNS)
                rows = math.ceil(len(times) / columns)
                fig, axes = plt.subplots(rows, columns, figsize=render.figure_size(run, columns, rows), squeeze=False,
                                         layout="constrained")
                for ax in axes.flat[len(times):]:
                    ax.set_visible(False)
                for ax, t in zip(axes.flat, times):
                    render.draw_frame(ax, run, t, show_shape=shape, cmap=args.cmap, norm=norm, trail=args.trail,
                                      links=args.links, events=events, legend=False)
                fig.legend(handles=render.legend_handles(run, shape), loc="outside lower center", ncol=5, fontsize=9,
                           frameon=False)
                render.add_colorbar(fig, axes.ravel().tolist(), args.cmap, norm, rows)
                fig.suptitle(render.suptitle(run), fontsize=12)
                save(fig, run, f"{run.name}_snapshots{suffix}", args.format, args.dpi)
        print(f"[snapshots] {scenario}/{run.name}: t = {', '.join(f'{t:g}' for t in times)}")


if __name__ == "__main__":
    main()
