"""Fairness of the coverage in the first instants of the runs: the shape split among the devices (each part is the
piece of the shape nearer to that device than to any other, as in FormationMetrics), colored by the share of its
device over the fair share (the area of the shape over the number of devices): gray when fair, red when larger (too
few devices around), blue when smaller (too many). The title tells Jain's fairness index of the shares.

    python python/plot_positions_fairness.py                     # one every 20 time units up to t = 200
    python python/plot_positions_fairness.py 0 50 100            # these instants (the nearest export)
    python python/plot_positions_fairness.py --every 10 --until 100 --where shape=star

Charts: charts/positions/<scenario>/<run>/<fmt>/<run>_fairness_t<time>.<fmt>, and <run>_fairness.<fmt> with all the
instants in one figure (6 per row).
"""

from __future__ import annotations

import argparse
import math

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
from matplotlib.cm import ScalarMappable
from matplotlib.colors import ListedColormap, LogNorm
from matplotlib.lines import Line2D

import positions as pos
import render
from plot_positions_snapshots import COMBINED_COLUMNS, default_times, save

CMAP = "RdBu_r"  # Blue below the fair share, gray-white at it, red above
NORM = LogNorm(vmin=0.25, vmax=4.0)  # From a quarter to four times the fair share, symmetric around it
BORDER_COLOR = "#505050"  # Of the parts
DEVICE_COLOR = "#202020"
CHUNK = 2048  # Points of the shape compared with the devices at once


def nearest(points: np.ndarray, devices: np.ndarray) -> np.ndarray:
    """The index of the device nearest to each point."""
    return np.concatenate([np.argmin(((chunk[:, None, :] - devices[None, :, :]) ** 2).sum(-1), axis=1)
                           for chunk in np.array_split(points, max(1, len(points) // CHUNK))])


def shares(owners: np.ndarray, devices: int) -> np.ndarray:
    """The share of each device: the points of the shape it owns, over the fair number of them (1 is fair)."""
    return np.bincount(owners, minlength=devices) * devices / len(owners)


def jain(values: np.ndarray) -> float:
    """Jain's fairness index: 1 when all the values are equal, down to one over their number when one has it all."""
    return float(values.sum() ** 2 / (len(values) * (values ** 2).sum()))


def draw_fairness(ax: plt.Axes, run: pos.Run, time: float, events: dict | None, legend: bool = True) -> None:
    ax.clear()
    t = run.nearest_time(time)
    frame = run.at(t)
    xy = frame[["x", "y"]].to_numpy()
    owned = np.zeros(len(frame), dtype=bool)
    note = "shape not placed yet"
    outline = run.shape_outline(t)
    if outline is not None:
        x, y, values = outline
        inside = values <= 0
        owner = np.full(values.shape, -1)
        owner[inside] = nearest(np.column_stack([x[inside], y[inside]]), xy)
        share = shares(owner[inside], len(frame))
        owned = share > 0
        note = f"Jain {jain(share):.2f}"
        ax.pcolormesh(x, y, np.ma.masked_where(~inside, share[owner]), cmap=CMAP, norm=NORM, shading="nearest",
                      rasterized=True, zorder=0)
        # The borders between the parts: the points whose right or upper neighbor belongs to another device
        border = np.zeros(values.shape, dtype=bool)
        border[:, :-1] |= owner[:, :-1] != owner[:, 1:]
        border[:-1, :] |= owner[:-1, :] != owner[1:, :]
        border &= inside
        ax.pcolormesh(x, y, np.ma.masked_where(~border, border), cmap=ListedColormap([BORDER_COLOR]),
                      shading="nearest", alpha=0.6, rasterized=True, zorder=1)
        ax.contour(x, y, values, levels=[0.0], colors=[pos.SHAPE_EDGE], linewidths=1.0, zorder=2)
    size = render.node_size(len(frame))
    ax.scatter(xy[owned, 0], xy[owned, 1], color=DEVICE_COLOR, s=size * 0.5, linewidths=0, zorder=4)
    ax.scatter(xy[~owned, 0], xy[~owned, 1], facecolors="none", edgecolors=DEVICE_COLOR, s=size * 0.5,
               linewidths=0.6, zorder=4)
    leaders = frame[(frame["leader"] == 1) & (frame["anchor"] == 0)]
    anchors = frame[frame["anchor"] > 0]
    for group, marker, scale in ((leaders, "*", 6.0), (anchors, "D", 2.0)):
        ax.scatter(group["x"], group["y"], marker=marker, color=DEVICE_COLOR, edgecolors="white",
                   s=max(size * scale, 60 if marker == "*" else 25), linewidths=0.8, zorder=6)
    x0, x1, y0, y1 = run.limits
    ax.set_xlim(x0, x1)
    ax.set_ylim(y0, y1)
    ax.set_aspect("equal", adjustable="box")
    ax.set_title(f"t = {t:g} · {len(frame)} devices · {note}{render.event_note(t, events)}", fontsize=10)
    if legend:
        ax.legend(handles=legend_handles(run), loc="upper center", bbox_to_anchor=(0.5, -0.07), ncol=2, fontsize=8,
                  frameon=False)


def legend_handles(run: pos.Run) -> list:
    dot = dict(ls="none", markeredgecolor=DEVICE_COLOR, markersize=5)
    handles = [Line2D([], [], marker="o", markerfacecolor=DEVICE_COLOR, label="device", **dot),
               Line2D([], [], marker="o", markerfacecolor="none", label="device owning no part of the shape", **dot)]
    if (run.devices["leader"].astype(bool) & (run.devices["anchor"] == 0)).any():
        handles.append(Line2D([], [], marker="*", ls="none", color=DEVICE_COLOR, markersize=11, label="leader"))
    if (run.devices["anchor"] > 0).any():
        handles.append(Line2D([], [], marker="D", ls="none", color=DEVICE_COLOR, markersize=6, label="anchors"))
    return handles


def add_colorbar(fig: plt.Figure, axes, rows: int = 1) -> None:
    shrink = 0.9 if rows <= 1 else max(0.25, 1.8 / rows)  # Not taller than about two rows of panels
    bar = fig.colorbar(ScalarMappable(norm=NORM, cmap=CMAP), ax=axes, extend="both", shrink=shrink)
    bar.set_label("Share of the shape over the fair share")
    bar.set_ticks([0.25, 0.5, 1.0, 2.0, 4.0], labels=["≤¼", "½", "1 (fair)", "2", "≥4"])
    bar.minorticks_off()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("times", nargs="*", type=float, help="simulation times (default: one every --every)")
    parser.add_argument("--every", type=float, default=20.0, help="without times, one chart every N (default 20)")
    parser.add_argument("--from", dest="start", type=float, default=None, help="without times, the first instant")
    parser.add_argument("--until", type=float, default=200.0, help="without times, the last instant (default 200)")
    parser.add_argument("--scenario", nargs="+", help="data/positions folders (default: all)")
    parser.add_argument("--where", nargs="+", metavar="NAME=VALUES", help="filters on the header, e.g. shape=star,ring")
    parser.add_argument("--all-seeds", action="store_true", help="every seed (default: the first of each run)")
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
        for t in times:
            fig, ax = plt.subplots(figsize=render.figure_size(run), layout="constrained")
            draw_fairness(ax, run, t, events)
            add_colorbar(fig, ax)
            fig.suptitle(render.suptitle(run), fontsize=11)
            save(fig, run, f"{run.name}_fairness_t{int(round(t)):04d}", args.format, args.dpi)
        columns = min(len(times), COMBINED_COLUMNS)
        rows = math.ceil(len(times) / columns)
        fig, axes = plt.subplots(rows, columns, figsize=render.figure_size(run, columns, rows), squeeze=False,
                                 layout="constrained")
        for ax in axes.flat[len(times):]:
            ax.set_visible(False)
        for ax, t in zip(axes.flat, times):
            draw_fairness(ax, run, t, events, legend=False)
        fig.legend(handles=legend_handles(run), loc="outside lower center", ncol=4, fontsize=9, frameon=False)
        add_colorbar(fig, axes.ravel().tolist(), rows)
        fig.suptitle(render.suptitle(run), fontsize=12)
        save(fig, run, f"{run.name}_fairness", args.format, args.dpi)
        print(f"[fairness] {scenario}/{run.name}: t = {', '.join(f'{t:g}' for t in times)}")


if __name__ == "__main__":
    main()
