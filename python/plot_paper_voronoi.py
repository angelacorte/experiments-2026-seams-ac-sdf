"""Voronoi coverage for the paper, sized for one column of a two-column layout: the shape split among the devices
(as in plot_positions_fairness.py) at three instants side by side, each part colored by the share of its device over
the fair share (colorbar in a figure of its own, shared by all the shapes), with Jain's fairness index of the shares above each panel.

    python python/plot_paper_voronoi.py                                   # every shape, centered start, t = 0 40 590
    python python/plot_paper_voronoi.py 40 150 590 --where shape=star start=left
    python python/plot_paper_voronoi.py --width 3.33                      # ACM column (default: IEEE, 252 pt)

Charts: charts/paper/voronoi_<run>.<fmt>, and voronoi_legend.<fmt> with the colorbar shared by all of them
"""

from __future__ import annotations

import argparse

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
from matplotlib.cm import ScalarMappable
from matplotlib.colors import ListedColormap

import config
import positions as pos
from plot_positions_fairness import BORDER_COLOR, CMAP, DEVICE_COLOR, NORM, jain, nearest, shares

OUT_DIR = config.ROOT / "charts" / "paper"
FONT_SIZE = 8  # Points, as the captions of the paper and its other figures
DOT = 5.0  # Area of the dots of the devices, in square points
BORDER_GROWTH = 1  # Points of the grid added on each side of the borders between the cells (0: one point thin)
TITLE_HEIGHT = 0.2  # Inches above the panels, for their titles

plt.rcParams.update({
    "font.family": "serif",
    "font.serif": ["STIXGeneral"],  # As the other figures of the paper (scripts/generate_preliminaries_figures.py)
    "mathtext.fontset": "stix",
    "font.size": FONT_SIZE,
    "pdf.fonttype": 42,  # TrueType fonts in the PDF: the paper checkers reject Type 3
})


def finer(grid: np.ndarray) -> np.ndarray:
    """The grid twice as fine, a point halfway between every two neighbors: smoother borders between the parts."""
    rows = np.empty((2 * grid.shape[0] - 1, grid.shape[1]))
    rows[::2], rows[1::2] = grid, (grid[:-1] + grid[1:]) / 2
    out = np.empty((rows.shape[0], 2 * grid.shape[1] - 1))
    out[:, ::2], out[:, 1::2] = rows, (rows[:, :-1] + rows[:, 1:]) / 2
    return out


def view(run: pos.Run, times: list[float]) -> tuple[float, float, float, float]:
    """The same view for every panel: a square around the shape plus a small margin, so that the figures of all the
    shapes are equally tall (the devices far outside the shape are left out, they cover none of it)."""
    x, y, values = run.shape_outline(times[0])
    inside = values <= 0
    xs, ys = x[inside], y[inside]
    half = 0.54 * max(np.ptp(xs), np.ptp(ys))
    cx, cy = (xs.min() + xs.max()) / 2, (ys.min() + ys.max()) / 2
    return cx - half, cx + half, cy - half, cy + half


def draw(ax: plt.Axes, run: pos.Run, time: float, limits: tuple[float, float, float, float]) -> None:
    frame = run.at(time)
    xy = frame[["x", "y"]].to_numpy()
    x, y, values = map(finer, run.shape_outline(time))
    inside = values <= 0
    owner = np.full(values.shape, -1)
    owner[inside] = nearest(np.column_stack([x[inside], y[inside]]), xy)
    share = shares(owner[inside], len(frame))
    ax.pcolormesh(x, y, np.ma.masked_where(~inside, share[owner]), cmap=CMAP, norm=NORM, shading="nearest",
                  rasterized=True, zorder=0)
    # The borders between the parts: the points whose right or upper neighbor belongs to another device
    border = np.zeros(values.shape, dtype=bool)
    border[:, :-1] |= owner[:, :-1] != owner[:, 1:]
    border[:-1, :] |= owner[:-1, :] != owner[1:, :]
    for _ in range(BORDER_GROWTH):  # Thicker: also the neighbors of the points of the border
        grown = border.copy()
        grown[1:] |= border[:-1]
        grown[:-1] |= border[1:]
        grown[:, 1:] |= border[:, :-1]
        grown[:, :-1] |= border[:, 1:]
        border = grown
    border &= inside
    ax.pcolormesh(x, y, np.ma.masked_where(~border, border), cmap=ListedColormap([BORDER_COLOR]),
                  shading="nearest", alpha=0.75, rasterized=True, zorder=1)
    ax.contour(x, y, values, levels=[0.0], colors=[pos.SHAPE_EDGE], linewidths=0.6, zorder=2)
    owned = share > 0
    ax.scatter(xy[owned, 0], xy[owned, 1], color=DEVICE_COLOR, s=DOT, linewidths=0, zorder=3)
    ax.scatter(xy[~owned, 0], xy[~owned, 1], facecolors="none", edgecolors=DEVICE_COLOR, s=DOT, linewidths=0.5,
               zorder=3)
    x0, x1, y0, y1 = limits
    ax.set_xlim(x0, x1)
    ax.set_ylim(y0, y1)
    ax.set_aspect("equal")
    ax.axis("off")
    ax.set_title(f"$t={run.nearest_time(time):g}$, $J={jain(share):.2f}$", fontsize=FONT_SIZE, pad=2)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("times", nargs="*", type=float, default=[0.0, 40.0, 590.0],
                        help="the instants of the panels (default: 0 40 590, before the population changes)")
    parser.add_argument("--scenario", nargs="+", default=["dynamicPositionBased"], help="data/positions folders")
    parser.add_argument("--where", nargs="+", default=["start=centered"], metavar="NAME=VALUES",
                        help="filters on the header (default: start=centered)")
    parser.add_argument("--width", type=float, default=3.49, help="figure width in inches (default 3.49, the 252 pt IEEE column)")
    parser.add_argument("--format", nargs="+", default=["pdf", "png"])
    parser.add_argument("--dpi", type=int, default=600, help="of the rasterized parts (default 600)")
    args = parser.parse_args()

    selected = pos.select_runs(args.scenario, pos.parse_filters(args.where), all_seeds=False)
    if not selected:
        raise SystemExit(f"No run in {pos.POSITIONS_DIR} matches")
    for scenario, path in selected:
        run = pos.load_run(scenario, path)
        limits = view(run, args.times)
        # Same size for every shape (no tight bounding box, which would crop each one differently)
        fig, axes = plt.subplots(1, len(args.times), figsize=(args.width, args.width / len(args.times) + TITLE_HEIGHT),
                                 layout="constrained")
        fig.get_layout_engine().set(w_pad=0.01, h_pad=0.01, wspace=0.01)
        for ax, t in zip(axes, args.times):
            draw(ax, run, t, limits)
        save(fig, f"voronoi_{run.name}", args)
    legend(args)


def legend(args: argparse.Namespace) -> None:
    """The colorbar alone, shared by all the figures, as wide as them."""
    fig = plt.figure(figsize=(args.width, 0.38))
    bar = fig.colorbar(ScalarMappable(norm=NORM, cmap=CMAP), cax=fig.add_axes((0.2, 0.68, 0.6, 0.2)),
                       orientation="horizontal", extend="both")
    bar.set_label("Voronoi cell area / fair share", fontsize=FONT_SIZE, labelpad=1)
    bar.set_ticks([0.25, 0.5, 1.0, 2.0, 4.0], labels=["¼", "½", "1", "2", "4"])
    bar.minorticks_off()
    bar.ax.tick_params(labelsize=FONT_SIZE - 1, length=2, pad=1)
    bar.outline.set_linewidth(0.4)
    save(fig, "voronoi_legend", args)


def save(fig: plt.Figure, stem: str, args: argparse.Namespace) -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for fmt in args.format:
        fig.savefig(OUT_DIR / f"{stem}.{fmt}", dpi=args.dpi)
    plt.close(fig)
    print(f"[paper] {OUT_DIR / stem}")


if __name__ == "__main__":
    main()
