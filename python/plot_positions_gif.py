"""GIFs of the dynamic-population runs: the devices over time, colored by the SDF of the shape at their position,
with the leader and the anchors, and optionally the shape in the background (see plot_positions_snapshots.py).

    python python/plot_positions_gif.py                           # first seed of each run, both versions
    python python/plot_positions_gif.py --where shape=star --shape-overlay on --fps 12
    python python/plot_positions_gif.py --from 600 --until 1200 --frame-step 2 --trail 4

GIFs: charts/positions/<scenario>/<run>/gif/<run>[_shape].gif
"""

from __future__ import annotations

import argparse

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.animation import FuncAnimation, PillowWriter

import positions as pos
import render
from plot_positions_snapshots import overlays


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--scenario", nargs="+", help="data/positions folders (default: all)")
    parser.add_argument("--where", nargs="+", metavar="NAME=VALUES", help="filters on the header, e.g. shape=star,ring")
    parser.add_argument("--all-seeds", action="store_true", help="every seed (default: the first of each run)")
    parser.add_argument("--shape-overlay", choices=["off", "on", "both"], default="both")
    parser.add_argument("--from", dest="start", type=float, default=None, help="first simulation time")
    parser.add_argument("--until", type=float, default=None, help="last simulation time")
    parser.add_argument("--frame-step", type=int, default=1, help="one frame every N exports")
    parser.add_argument("--fps", type=int, default=10)
    parser.add_argument("--dpi", type=int, default=100)
    parser.add_argument("--trail", type=int, default=0, help="draw the last N exports of each device")
    parser.add_argument("--links", type=float, default=None, help="draw the links shorter than this distance")
    parser.add_argument("--cmap", default=render.DEFAULT_CMAP)
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
        frames = [t for t in run.times
                  if (args.start is None or t >= args.start) and (args.until is None or t <= args.until)]
        frames = frames[:: max(args.frame_step, 1)]
        norm = render.color_norm(run)
        for shape in overlays(args.shape_overlay):
            fig, ax = plt.subplots(figsize=render.figure_size(run), layout="constrained")
            render.add_colorbar(fig, ax, args.cmap, norm)
            fig.suptitle(render.suptitle(run), fontsize=11)

            def update(t, ax=ax, shape=shape):
                render.draw_frame(ax, run, t, show_shape=shape, cmap=args.cmap, norm=norm, trail=args.trail,
                                  links=args.links, events=events)
                return []

            output = pos.CHARTS_DIR / scenario / run.name / "gif" / f"{run.name}{'_shape' if shape else ''}.gif"
            output.parent.mkdir(parents=True, exist_ok=True)
            FuncAnimation(fig, update, frames=frames, blit=False).save(output, writer=PillowWriter(fps=args.fps),
                                                                       dpi=args.dpi)
            plt.close(fig)
            print(f"[gif] {output} ({len(frames)} frames)")


if __name__ == "__main__":
    main()
