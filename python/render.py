"""Drawing of one frame of a run (see positions.py): devices colored by the SDF, leader, anchors, shape."""

from __future__ import annotations

import numpy as np
import matplotlib.pyplot as plt
from matplotlib.cm import ScalarMappable
from matplotlib.collections import LineCollection
from matplotlib.colors import TwoSlopeNorm
from matplotlib.lines import Line2D
from matplotlib.patches import Patch

import positions as pos

DEFAULT_CMAP = "viridis_r"  # Reversed: deep inside yellow (warm), on the border green, outside purple


def color_norm(run: pos.Run) -> TwoSlopeNorm:
    """Centered on the border (SDF = 0): one half of the colormap inside the shape, the other half outside, both
    spanning its depth (farther outside saturates)."""
    depth = run.depth
    return TwoSlopeNorm(vmin=-depth, vcenter=0.0, vmax=depth)


def node_size(count: int) -> float:
    return float(np.clip(4000.0 / max(count, 1), 9.0, 45.0))


def event_note(time: float, events: dict | None) -> str:
    if not events:
        return ""
    grown = events["nodes"] + events["spawnCount"]
    if events["spawnTime"] <= time < events["spawnTime"] + 60:
        return f" · +{events['spawnCount']} devices at t={events['spawnTime']:g}"
    if events["killTime"] <= time < events["killTime"] + 60:
        return f" · {grown}→{events['survivors']} devices at t={events['killTime']:g}"
    return ""


def draw_frame(
    ax: plt.Axes,
    run: pos.Run,
    time: float,
    *,
    show_shape: bool,
    cmap: str,
    norm: TwoSlopeNorm,
    trail: int = 0,
    links: float | None = None,
    events: dict | None = None,
    legend: bool = True,
) -> None:
    ax.clear()
    t = run.nearest_time(time)
    frame = run.at(t)
    size = node_size(len(frame))
    if show_shape:
        outline = run.shape_outline(t)
        if outline is not None:
            x, y, values = outline
            ax.contourf(x, y, values, levels=[np.nanmin(values) - 1.0, 0.0], colors=[pos.SHAPE_FILL],
                        alpha=pos.SHAPE_FILL_ALPHA, zorder=0)
            ax.contour(x, y, values, levels=[0.0], colors=[pos.SHAPE_EDGE], linewidths=1.0, zorder=1)
    if trail > 0:
        recent = run.devices[(run.devices["time"] <= t) & (run.devices["time"] > t - trail * run.interval)]
        segments = [g[["x", "y"]].to_numpy() for _, g in recent.groupby("id") if len(g) > 1]
        if segments:
            ax.add_collection(LineCollection(segments, colors=pos.TRAIL_COLOR, linewidths=0.6, alpha=0.5, zorder=2))
    if links:
        xy = frame[["x", "y"]].to_numpy()
        delta = xy[:, None, :] - xy[None, :, :]
        close = np.triu((delta ** 2).sum(-1) <= links ** 2, k=1)
        i, j = np.nonzero(close)
        if len(i):
            ax.add_collection(LineCollection(np.stack([xy[i], xy[j]], axis=1), colors="#b0b0b0",
                                             linewidths=0.4, alpha=0.6, zorder=2))
    plain = frame[(frame["leader"] == 0) & (frame["anchor"] == 0)]
    common = dict(edgecolors="#202020", linewidths=0.3, zorder=4)
    inside_frame = plain[plain["sdf"].notna()]
    ax.scatter(inside_frame["x"], inside_frame["y"], c=inside_frame["sdf"], cmap=cmap, norm=norm, s=size, **common)
    unplaced = plain[plain["sdf"].isna()]
    ax.scatter(unplaced["x"], unplaced["y"], color=pos.UNPLACED_COLOR, s=size, **common)
    leaders = frame[(frame["leader"] == 1) & (frame["anchor"] == 0)]
    anchors = frame[frame["anchor"] > 0]
    for group, marker, scale in ((leaders, "*", 9.0), (anchors, "D", 3.0)):
        if group.empty:
            continue
        colors = [pos.UNPLACED_COLOR if np.isnan(v) else plt.get_cmap(cmap)(norm(v)) for v in group["sdf"]]
        ax.scatter(group["x"], group["y"], c=colors, marker=marker, s=max(size * scale, 60 if marker == "*" else 30),
                   edgecolors=pos.LEADER_EDGE, linewidths=1.3, zorder=6)
    for _, anchor in anchors.iterrows():
        ax.annotate(f"A{int(anchor['anchor'])}", (anchor["x"], anchor["y"]), xytext=(4, 4),
                    textcoords="offset points", fontsize=8, color=pos.ANCHOR_EDGE, fontweight="bold", zorder=7)
    x0, x1, y0, y1 = run.limits
    ax.set_xlim(x0, x1)
    ax.set_ylim(y0, y1)
    ax.set_aspect("equal", adjustable="box")
    ax.grid(True, color="#ececec", linewidth=0.6, zorder=-1)
    ax.set_axisbelow(True)
    ax.set_title(f"t = {t:g} · {len(frame)} devices{event_note(t, events)}", fontsize=10)
    if legend:
        ax.legend(handles=legend_handles(run, show_shape), loc="upper center", bbox_to_anchor=(0.5, -0.07),
                  ncol=2, fontsize=8, frameon=False)


def aspect(run: pos.Run) -> float:
    x0, x1, y0, y1 = run.limits
    return float(np.clip((x1 - x0) / max(y1 - y0, 1e-9), 0.6, 2.2))


def figure_size(run: pos.Run, columns: int = 1, rows: int = 1) -> tuple[float, float]:
    """A figure whose panels have the proportions of the view, plus room for the colorbar (and the legend below)."""
    if columns == 1 and rows == 1:
        return 5.0 * aspect(run) + 2.0, 6.2
    return 3.6 * aspect(run) * columns + 1.6, 3.9 * rows + 0.7


def legend_handles(run: pos.Run, show_shape: bool) -> list:
    handles = [Line2D([], [], marker="o", ls="none", markerfacecolor="#8f8f8f", markeredgecolor="#202020",
                      markersize=6, label="device (color: SDF)")]
    if (run.devices["leader"].astype(bool) & (run.devices["anchor"] == 0)).any():
        handles.append(Line2D([], [], marker="*", ls="none", markerfacecolor="#8f8f8f", markeredgecolor=pos.LEADER_EDGE,
                              markersize=12, label="leader"))
    if (run.devices["anchor"] > 0).any():
        handles.append(Line2D([], [], marker="D", ls="none", markerfacecolor="#8f8f8f", markeredgecolor=pos.ANCHOR_EDGE,
                              markersize=7, label="anchors A1-A3"))
    if show_shape and run.rasters:
        handles.append(Patch(facecolor=pos.SHAPE_FILL, alpha=0.5, edgecolor=pos.SHAPE_EDGE, label="shape (SDF ≤ 0)"))
    if run.devices["sdf"].isna().any():
        handles.append(Line2D([], [], marker="o", ls="none", markerfacecolor=pos.UNPLACED_COLOR,
                              markeredgecolor="#202020", markersize=6, label="shape not placed yet"))
    return handles


def add_colorbar(fig: plt.Figure, axes, cmap: str, norm: TwoSlopeNorm, rows: int = 1) -> None:
    shrink = 0.9 if rows <= 1 else max(0.25, 1.8 / rows)  # Not taller than about two rows of panels
    bar = fig.colorbar(ScalarMappable(norm=norm, cmap=cmap), ax=axes, extend="max", shrink=shrink)
    bar.set_label("SDF at the device (< 0 inside the shape)")
    bar.set_ticks([norm.vmin, norm.vmin / 2, 0.0, norm.vmax / 2, norm.vmax])
    bar.set_ticklabels([f"{norm.vmin:.0f}", f"{norm.vmin / 2:.0f}", "0 (border)", f"{norm.vmax / 2:.0f}",
                        f"≥{norm.vmax:.0f}"])


def suptitle(run: pos.Run) -> str:
    return f"{run.label} — {run.description}"
