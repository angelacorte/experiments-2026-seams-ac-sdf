"""Voronoi coverage for the paper, sized for half a column (or one column) of a two-column layout: the shape split
among the devices (as in plot_positions_fairness.py) at three instants side by side, each part colored by the share of
its device over the fair share (colorbar in a figure of its own, shared by all the shapes), with Jain's fairness index
of the shares above each panel.

    python python/plot_paper_voronoi.py                                   # every shape, centered start, t = 0 40 590
    python python/plot_paper_voronoi.py 40 150 590 --where shape=star start=left
    python python/plot_paper_voronoi.py --width 3.49                      # full IEEE column (default: 1.70, half column)

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
from matplotlib.lines import Line2D

import config
import positions as pos
from plot_positions_fairness import BORDER_COLOR, CMAP, DEVICE_COLOR, NORM, jain, nearest, shares

OUT_DIR = config.ROOT / "charts" / "paper"
FULL_COLUMN_WIDTH = 3.49  # IEEE column width in inches (252 pt)
HALF_COLUMN_WIDTH = 1.70  # Half of an IEEE column (~0.485 \columnwidth), fitting 2 shapes per row

plt.rcParams.update({
    "font.family": "serif",
    "font.serif": ["STIXGeneral"],  # As the other figures of the paper (scripts/generate_preliminaries_figures.py)
    "mathtext.fontset": "stix",
    "pdf.fonttype": 42,  # TrueType fonts in the PDF: the paper checkers reject Type 3
    "ps.fonttype": 42,
})


def style_for_width(width: float) -> dict[str, float | int]:
    """Scale visual parameters so half-column and full-column figures are both crisp and legible at 1:1 print scale."""
    half_col = width <= 2.2
    return {
        "legend_font": 6.4 if half_col else 8.0,
        "dot": 1.5 if half_col else 5.0,
        "hollow_lw": 0.38 if half_col else 0.5,
        "contour_lw": 0.5 if half_col else 0.6,
        "border_growth": 0 if half_col else 1,
    }


def finer(grid: np.ndarray) -> np.ndarray:
    """The grid twice as fine, a point halfway between every two neighbors: smoother borders between the parts."""
    rows = np.empty((2 * grid.shape[0] - 1, grid.shape[1]))
    rows[::2], rows[1::2] = grid, (grid[:-1] + grid[1:]) / 2
    out = np.empty((rows.shape[0], 2 * grid.shape[1] - 1))
    out[:, ::2], out[:, 1::2] = rows, (rows[:, :-1] + rows[:, 1:]) / 2
    return out


def view_at(run: pos.Run, time: float, pad: float = 0.53) -> tuple[float, float, float, float]:
    """Square view centered on the shape at [time]."""
    x, y, values = run.shape_outline(time)
    inside = values <= 0
    xs, ys = x[inside], y[inside]
    half = pad * max(np.ptp(xs), np.ptp(ys))
    cx, cy = (xs.min() + xs.max()) / 2, (ys.min() + ys.max()) / 2
    return cx - half, cx + half, cy - half, cy + half


def view(run: pos.Run, times: list[float]) -> tuple[float, float, float, float]:
    """The same view for every panel: a square around the shape plus a small margin, so that the figures of all the
    shapes are equally tall (the devices far outside the shape are left out, they cover none of it)."""
    return view_at(run, times[0])


def draw(
    ax: plt.Axes,
    run: pos.Run,
    time: float,
    limits: tuple[float, float, float, float],
    style: dict,
    *,
    show_roles: bool = False,
    prev_outline: tuple[np.ndarray, np.ndarray, np.ndarray] | None = None,
) -> None:
    frame = run.at(time)
    xy = frame[["x", "y"]].to_numpy()
    x, y, values = map(finer, run.shape_outline(time))
    inside = values <= 0
    # A device is inside the shape only if its exact SDF is <= 0 AND it falls inside the rasterized contour
    u_vec = x[0, :] if np.ptp(x[0, :]) > np.ptp(x[:, 0]) else x[:, 0]
    grid_pts = np.column_stack([x.ravel(), y.ravel()])
    nearest_grid = nearest(xy, grid_pts)
    in_shape = (frame["sdf"] <= 0.0).to_numpy() & (values.ravel()[nearest_grid] <= 0.0)
    owner = np.full(values.shape, -1)
    share = np.zeros(len(frame))
    if in_shape.any():
        inside_indices = np.flatnonzero(in_shape)
        owner[inside] = inside_indices[nearest(np.column_stack([x[inside], y[inside]]), xy[in_shape])]
        share = shares(owner[inside], len(frame))
    if prev_outline is not None:
        px, py, pvalues = prev_outline
        ax.contour(px, py, pvalues, levels=[0.0], colors=["#888888"], linewidths=0.55, linestyles="--", zorder=0)
    ax.pcolormesh(x, y, np.ma.masked_where(~inside, share[owner]), cmap=CMAP, norm=NORM, shading="nearest",
                  rasterized=True, zorder=1)
    # The borders between the parts: the points whose right or upper neighbor belongs to another device
    border = np.zeros(values.shape, dtype=bool)
    border[:, :-1] |= owner[:, :-1] != owner[:, 1:]
    border[:-1, :] |= owner[:-1, :] != owner[1:, :]
    for _ in range(int(style["border_growth"])):  # Thicker: also the neighbors of the points of the border
        grown = border.copy()
        grown[1:] |= border[:-1]
        grown[:-1] |= border[1:]
        grown[:, 1:] |= border[:, :-1]
        grown[:, :-1] |= border[:, 1:]
        border = grown
    border &= inside
    ax.pcolormesh(x, y, np.ma.masked_where(~border, border), cmap=ListedColormap([BORDER_COLOR]),
                  shading="nearest", alpha=0.75, rasterized=True, zorder=2)
    ax.contour(x, y, values, levels=[0.0], colors=[pos.SHAPE_EDGE], linewidths=style["contour_lw"], zorder=3)
    dot_scale = 0.45 if len(frame) >= 300 else 1.0
    ax.scatter(xy[in_shape, 0], xy[in_shape, 1], color=DEVICE_COLOR, s=style["dot"] * dot_scale, linewidths=0, zorder=4)
    ax.scatter(xy[~in_shape, 0], xy[~in_shape, 1], facecolors="none", edgecolors=DEVICE_COLOR,
               s=style["dot"] * dot_scale, linewidths=style["hollow_lw"], zorder=4)
    if show_roles:
        leaders = frame[(frame["leader"] == 1) & (frame["anchor"] == 0)]
        anchors = frame[frame["anchor"] > 0]
        if not leaders.empty:
            ax.scatter(leaders["x"], leaders["y"], marker="*", color="#f5b700", edgecolors="#151515",
                       s=28, linewidths=0.5, zorder=6)
        if not anchors.empty:
            ax.scatter(anchors["x"], anchors["y"], marker="D", color="#f5b700", edgecolors="#151515",
                       s=14, linewidths=0.5, zorder=6)
    x0, x1, y0, y1 = limits
    ax.set_xlim(x0, x1)
    ax.set_ylim(y0, y1)
    ax.set_aspect("equal")
    ax.axis("off")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("times", nargs="*", type=float, default=[0.0, 40.0, 590.0],
                        help="the instants of the panels (default: 0 40 590, before the population changes)")
    parser.add_argument("--scenario", nargs="+", default=["dynamicPositionBased"], help="data/positions folders")
    parser.add_argument("--where", nargs="+", default=["start=centered"], metavar="NAME=VALUES",
                        help="filters on the header (default: start=centered)")
    parser.add_argument("--width", type=float, default=HALF_COLUMN_WIDTH,
                        help=f"figure width in inches (default {HALF_COLUMN_WIDTH}, half of the 252 pt IEEE column)")
    parser.add_argument("--format", nargs="+", default=["pdf", "png"])
    parser.add_argument("--dpi", type=int, default=600, help="of the rasterized parts (default 600)")
    args = parser.parse_args()

    style = style_for_width(args.width)
    fig_height = args.width / len(args.times)
    selected = pos.select_runs(args.scenario, pos.parse_filters(args.where), all_seeds=False)
    if not selected:
        raise SystemExit(f"No run in {pos.POSITIONS_DIR} matches")
    for scenario, path in selected:
        run = pos.load_run(scenario, path)
        limits = view(run, args.times)
        # Same size for every shape (no tight bounding box, which would crop each one differently)
        fig, axes = plt.subplots(1, len(args.times), figsize=(args.width, fig_height), layout="constrained")
        fig.get_layout_engine().set(w_pad=0.005, h_pad=0.005, wspace=0.01)
        for ax, t in zip(axes, args.times):
            draw(ax, run, t, limits, style)
        save(fig, f"voronoi_{run.name}", args)
    legend(args, style, fig_height)
    regimes_comparison(args)
    adaptation_sequence(args)


def regimes_comparison(args: argparse.Namespace) -> None:
    """Compare Position-based, Displacement-based (leader), and Distance-based (anchors) on circle and ring at t = 590."""
    regimes = [
        ("dynamicPositionBased", {"start": ["centered"]}, "(a) Position-based"),
        ("dynamicDisplacementBased", {}, "(b) Displacement-based"),
        ("dynamicDistanceBased", {}, "(c) Distance-based"),
    ]
    shapes = ["circle", "ring"]
    style = style_for_width(HALF_COLUMN_WIDTH)
    style["dot"] = 2.0
    fig, axes = plt.subplots(len(shapes), len(regimes), figsize=(FULL_COLUMN_WIDTH, 2.05), layout="constrained")
    fig.get_layout_engine().set(w_pad=0.01, h_pad=0.01, wspace=0.02, hspace=0.02)
    for row_idx, shape in enumerate(shapes):
        for col_idx, (scenario, extra_filter, label) in enumerate(regimes):
            ax = axes[row_idx, col_idx]
            selected = pos.select_runs([scenario], {"shape": [shape], **extra_filter}, all_seeds=False)
            if not selected:
                ax.axis("off")
                continue
            run = pos.load_run(*selected[0])
            draw(ax, run, 590.0, view_at(run, 590.0, pad=0.56), style, show_roles=True)
            if row_idx == 0:
                ax.set_title(label, fontsize=7.0, pad=2)
    handles = [
        Line2D([], [], marker="*", ls="none", markerfacecolor="#f5b700", markeredgecolor="#151515",
               markersize=6.5, label="elected leader"),
        Line2D([], [], marker="D", ls="none", markerfacecolor="#f5b700", markeredgecolor="#151515",
               markersize=4.5, label="elected anchors ($A_1$–$A_3$)"),
        Line2D([], [], marker="o", ls="none", markerfacecolor=DEVICE_COLOR, markeredgecolor=DEVICE_COLOR,
               markersize=3.5, label="inside"),
        Line2D([], [], marker="o", ls="none", markerfacecolor="none", markeredgecolor=DEVICE_COLOR,
               markeredgewidth=0.6, markersize=3.5, label="outside"),
    ]
    fig.legend(handles=handles, loc="outside lower center", ncol=4, fontsize=6.5, frameon=False,
               handletextpad=0.25, columnspacing=0.9)
    save(fig, "voronoi_regimes", args)


def adaptation_sequence(args: argparse.Namespace) -> None:
    """Four phases of runtime adaptation on star (Displacement-based): N=100, N=500 (+400 join), N=50 (leader re-elected
    after 90% kill, dashed old frame -> solid new frame), and N=50 re-converged."""
    selected = pos.select_runs(["dynamicDisplacementBased"], {"shape": ["star"]}, all_seeds=False)
    if not selected:
        return
    run = pos.load_run(*selected[0])
    style = style_for_width(HALF_COLUMN_WIDTH)
    style["dot"] = 2.0
    phases = [
        (590.0, r"$t{=}590\text{ s}$ ($N{=}100$)", None),
        (1190.0, r"$t{=}1190\text{ s}$ ($N{=}500$)", None),
        (1276.0, r"$t{=}1276\text{ s}$ ($N{=}50$)", 1190.0),
        (1790.0, r"$t{=}1790\text{ s}$ ($N{=}50$)", None),
    ]
    fig, axes = plt.subplots(1, len(phases), figsize=(FULL_COLUMN_WIDTH, 1.02), layout="constrained")
    fig.get_layout_engine().set(w_pad=0.01, h_pad=0.01, wspace=0.02)
    for ax, (t, title, prev_t) in zip(axes, phases):
        prev_outline = None
        if prev_t is not None:
            prev_outline = tuple(map(finer, run.shape_outline(prev_t)))
            # Union bounding box of old and new shape so the migration between frames is visible
            x0a, x1a, y0a, y1a = view_at(run, prev_t, pad=0.52)
            x0b, x1b, y0b, y1b = view_at(run, t, pad=0.52)
            cx, cy = (min(x0a, x0b) + max(x1a, x1b)) / 2, (min(y0a, y0b) + max(y1a, y1b)) / 2
            half = 0.52 * max(max(x1a, x1b) - min(x0a, x0b), max(y1a, y1b) - min(y0a, y0b))
            limits = (cx - half, cx + half, cy - half, cy + half)
        else:
            limits = view_at(run, t, pad=0.55)
        draw(ax, run, t, limits, style, show_roles=True, prev_outline=prev_outline)
        ax.set_title(title, fontsize=6.5, pad=1.5)
    save(fig, "voronoi_adaptation", args)


def legend(args: argparse.Namespace, style: dict, panel_height: float) -> None:
    """The colorbar (and dot key when sized for a half-column cell), shared by all the figures, as wide as them."""
    half_col = args.width <= 2.2
    height = panel_height if half_col else 0.38
    fig = plt.figure(figsize=(args.width, height))
    cax_rect = (0.10, 0.72, 0.80, 0.15) if half_col else (0.2, 0.68, 0.6, 0.2)
    bar = fig.colorbar(ScalarMappable(norm=NORM, cmap=CMAP), cax=fig.add_axes(cax_rect),
                       orientation="horizontal", extend="both")
    bar.set_label("Voronoi cell area / fair share", fontsize=style["legend_font"], labelpad=1)
    bar.set_ticks([0.25, 0.5, 1.0, 2.0, 4.0], labels=["¼", "½", "1", "2", "4"])
    bar.minorticks_off()
    bar.ax.tick_params(labelsize=style["legend_font"] - 0.5, length=1.8, pad=1)
    bar.outline.set_linewidth(0.4)
    if half_col:
        handles = [
            Line2D([], [], marker="o", ls="none", markerfacecolor=DEVICE_COLOR, markeredgecolor=DEVICE_COLOR,
                   markersize=3.2, label="inside shape"),
            Line2D([], [], marker="o", ls="none", markerfacecolor="none", markeredgecolor=DEVICE_COLOR,
                   markeredgewidth=0.6, markersize=3.2, label="outside (no cell)"),
        ]
        fig.legend(handles=handles, loc="lower center", bbox_to_anchor=(0.5, 0.02), ncol=2,
                   fontsize=style["legend_font"] - 0.5, frameon=False, handletextpad=0.2, columnspacing=0.8)
    save(fig, "voronoi_legend", args)


def save(fig: plt.Figure, stem: str, args: argparse.Namespace) -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for fmt in args.format:
        fig.savefig(OUT_DIR / f"{stem}.{fmt}", dpi=args.dpi)
    plt.close(fig)
    print(f"[paper] {OUT_DIR / stem}")


if __name__ == "__main__":
    main()
