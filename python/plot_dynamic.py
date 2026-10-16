"""Plots of the dynamic-population experiments: every metric over time, one line per scenario (mean over the seeds,
with the band of the spread), and the population events (spawn and kill) as vertical lines.

    python python/plot_dynamic.py                         # every chart, in charts/dynamic/ (pdf and png)
    python python/plot_dynamic.py --format pdf            # only pdf
    python python/plot_dynamic.py --metrics jain psi6 --shapes star ring
    python python/plot_dynamic.py --kind grid             # only some kinds: single, grid, overview
    python python/plot_dynamic.py --no-cache              # parse the data again from scratch

Charts:
- single:   charts/dynamic/<metric>/<fmt>/<metric>_<shape>.<fmt>      one metric, one shape
- grid:     charts/dynamic/<metric>/<fmt>/<metric>_all-shapes.<fmt>   one metric, a panel per shape
- overview: charts/dynamic/overview/<fmt>/overview_<shape>.<fmt>      one shape, a panel per metric
plus charts/dynamic/summary.csv, with mean, std and number of seeds of every metric, by scenario, shape and time.
"""

from __future__ import annotations

import argparse
import math
import warnings

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import pandas as pd
import seaborn as sns
from matplotlib.lines import Line2D

import config
import loader

EVENT_COLOR = "0.3"
SPAWN_STYLE = "--"
KILL_STYLE = ":"
TEXT_WIDTH = 7.0  # IEEEtran two-column \textwidth in inches (504 pt)
COLUMN_WIDTH = 3.49  # IEEEtran single-column \columnwidth in inches (252 pt)
UNIT_METRICS = {"inside", "jain", "psi6"}  # Metrics bounded in [0, 1]


def apply_paper_style() -> None:
    sns.set_theme(style="whitegrid", context="paper")
    plt.rcParams.update({
        "font.family": "serif",
        "font.serif": ["STIXGeneral"],
        "mathtext.fontset": "stix",
        "font.size": 8,
        "axes.titlesize": 8,
        "axes.labelsize": 8,
        "xtick.labelsize": 7,
        "ytick.labelsize": 7,
        "legend.fontsize": 7,
        "grid.linewidth": 0.4,
        "axes.linewidth": 0.6,
        "pdf.fonttype": 42,
        "ps.fonttype": 42,
    })


def series_palette(data: pd.DataFrame) -> dict[str, tuple]:
    """A viridis color for every line (scenario, or scenario split by a batched variable such as the start).
    The yellow end of viridis is left out, as it barely shows on white."""
    names = list(data["series"].cat.categories)
    colors = sns.color_palette("viridis", as_cmap=True)
    return {name: colors(0.85 * i / max(len(names) - 1, 1)) for i, name in enumerate(names)}


def metric_label(metric: str) -> str:
    return config.METRICS.get(metric, (metric, False))[0]


def metric_log(metric: str) -> bool:
    return config.METRICS.get(metric, (metric, False))[1]


def population_events(data: pd.DataFrame) -> dict:
    events = {scenario: loader.population_events(scenario) for scenario in data["scenario"].unique()}
    distinct = {tuple(sorted(e.items())) for e in events.values()}
    if len(distinct) > 1:
        warnings.warn(f"The scenarios have different population events, the first is drawn: {events}")
    return next(iter(events.values()))


def event_handles(events: dict) -> list[Line2D]:
    grown = events["nodes"] + events["spawnCount"]
    return [
        Line2D([], [], color=EVENT_COLOR, ls=SPAWN_STYLE, lw=0.9,
               label=f"t={events['spawnTime']:g}: +{events['spawnCount']} ({events['nodes']}→{grown})"),
        Line2D([], [], color=EVENT_COLOR, ls=KILL_STYLE, lw=1.1,
               label=f"t={events['killTime']:g}: −{grown - events['survivors']} ({grown}→{events['survivors']})"),
    ]


def draw_events(ax, events: dict, annotate: bool) -> None:
    grown = events["nodes"] + events["spawnCount"]
    for time, style, width, text in (
        (events["spawnTime"], SPAWN_STYLE, 0.85, f"+{events['spawnCount']}"),
        (events["killTime"], KILL_STYLE, 1.05, f"{grown}→{events['survivors']}"),
    ):
        ax.axvline(time, color=EVENT_COLOR, ls=style, lw=width, zorder=1)
        if annotate:
            ax.annotate(text, xy=(time, 0.98), xycoords=("data", "axes fraction"), xytext=(2, -1),
                        textcoords="offset points", ha="left", va="top", fontsize=6.5, color=EVENT_COLOR)


def scenario_handles(data: pd.DataFrame, palette: dict) -> list[Line2D]:
    return [Line2D([], [], color=palette[label], lw=1.6, label=label) for label in data["series"].cat.categories]


def draw_metric(ax, frame: pd.DataFrame, metric: str, palette: dict, events: dict, annotate: bool) -> None:
    frame = frame.dropna(subset=[metric])
    if not frame.empty:
        sns.lineplot(data=frame, x=config.TIME_COLUMN, y=metric, hue="series", palette=palette, errorbar=config.ERRORBAR,
                     err_kws={"alpha": 0.2, "lw": 0}, lw=1.1, legend=False, ax=ax)
    draw_events(ax, events, annotate)
    if metric_log(metric):
        ax.set_yscale("log")
    elif metric in UNIT_METRICS:
        ax.set_ylim(0.0, 1.03)
        ax.set_yticks([0.0, 0.2, 0.4, 0.6, 0.8, 1.0])
    max_t = frame[config.TIME_COLUMN].max() if not frame.empty else 1800.0
    ax.set_xlim(0, max_t)
    if max_t >= 1800:
        ax.set_xticks([0, 600, 1200, 1800])
    ax.tick_params(axis="both", which="major", pad=1.5, length=2.0, width=0.5)
    ax.set_xlabel("Time (s)", labelpad=1.5)
    ax.set_ylabel(metric_label(metric), labelpad=2.0)


def save(fig, path_without_suffix, formats) -> None:
    for fmt in formats:  # <folder>/<fmt>/<name>.<fmt>
        path = path_without_suffix.parent / fmt / f"{path_without_suffix.name}.{fmt}"
        path.parent.mkdir(parents=True, exist_ok=True)
        fig.savefig(path, bbox_inches="tight", pad_inches=0.02, dpi=300)
    plt.close(fig)


def plot_single(data, metric, shape, palette, events, formats) -> None:
    fig, ax = plt.subplots(figsize=(COLUMN_WIDTH, 1.35))
    draw_metric(ax, data[data[config.FACET_VARIABLE] == shape], metric, palette, events, annotate=True)
    ax.set_title(shape, pad=2.0, fontsize=8)
    handles = scenario_handles(data, palette) + event_handles(events)
    fig.legend(handles=handles, loc="lower center", ncol=3, fontsize=6.3, frameon=False,
               bbox_to_anchor=(0.5, -0.28), handlelength=1.2, handletextpad=0.3, columnspacing=0.7)
    fig.subplots_adjust(left=0.13, right=0.98, top=0.88, bottom=0.22)
    save(fig, config.CHARTS_DIR / metric / f"{metric}_{shape}", formats)


def plot_grid(data, metric, shapes, palette, events, formats) -> None:
    columns = min(len(shapes), config.GRID_COLUMNS)
    rows = math.ceil(len(shapes) / columns)
    fig_width = TEXT_WIDTH if columns >= 3 else COLUMN_WIDTH
    fig_height = 1.25 * rows
    fig, axes = plt.subplots(rows, columns, figsize=(fig_width, fig_height), sharex=True, sharey=True,
                             squeeze=False)
    for index, (ax, shape) in enumerate(zip(axes.flat, shapes)):
        draw_metric(ax, data[data[config.FACET_VARIABLE] == shape], metric, palette, events, annotate=False)
        ax.set_title(shape, pad=2.0, fontsize=8)
        if index % columns:
            ax.set_ylabel("")
    for index, ax in enumerate(axes.flat):
        if index >= len(shapes):
            ax.set_visible(False)
        elif index + columns >= len(shapes):  # Nothing below it: it carries the time axis
            ax.xaxis.set_tick_params(labelbottom=True)
            ax.set_xlabel("Time (s)", labelpad=1.5)
    handles = scenario_handles(data, palette) + event_handles(events)
    fig.subplots_adjust(left=0.055, right=0.99, top=0.88, bottom=0.24, wspace=0.14, hspace=0.22)
    fig.legend(handles=handles, loc="upper center", ncol=len(handles), fontsize=6.8, frameon=False,
               bbox_to_anchor=(0.52, 0.05 / rows), handlelength=1.25, handletextpad=0.3, columnspacing=0.75)
    save(fig, config.CHARTS_DIR / metric / f"{metric}_all-shapes", formats)


def plot_paper_stack(data, metrics_list, shapes, palette, events, formats, stem: str) -> None:
    """Compact multi-row x 5-column strip for figure* in the paper, sharing X-axis and a single bottom legend."""
    columns = len(shapes)
    rows = len(metrics_list)
    fig_height = 0.90 * rows + 0.34
    fig, axes = plt.subplots(rows, columns, figsize=(TEXT_WIDTH, fig_height), sharex=True, sharey="row", squeeze=False)
    for row_idx, metric in enumerate(metrics_list):
        for col_idx, shape in enumerate(shapes):
            ax = axes[row_idx, col_idx]
            draw_metric(ax, data[data[config.FACET_VARIABLE] == shape], metric, palette, events, annotate=False)
            if metric == "motion":
                ax.set_ylim(0.0, 0.34)
                ax.set_yticks([0.0, 0.1, 0.2, 0.3])
            if row_idx == 0:
                ax.set_title(shape, pad=2.0, fontsize=8)
            if col_idx > 0:
                ax.set_ylabel("")
                ax.tick_params(labelleft=False)
            if row_idx < rows - 1:
                ax.set_xlabel("")
                ax.tick_params(labelbottom=False)
    handles = scenario_handles(data, palette) + event_handles(events)
    fig.subplots_adjust(left=0.055, right=0.99, top=0.91, bottom=0.18, wspace=0.14, hspace=0.16)
    fig.legend(handles=handles, loc="upper center", ncol=len(handles), fontsize=6.8, frameon=False,
               bbox_to_anchor=(0.52, 0.04), handlelength=1.25, handletextpad=0.3, columnspacing=0.75)
    paper_dir = config.ROOT / "charts" / "paper"
    paper_dir.mkdir(parents=True, exist_ok=True)
    for fmt in formats:
        fig.savefig(paper_dir / f"{stem}.{fmt}", bbox_inches="tight", pad_inches=0.02, dpi=300)
    plt.close(fig)


def plot_paper_summary_bars(data: pd.DataFrame, shapes: list[str], palette: dict, formats: list[str]) -> None:
    """Compact three-panel single-column bar chart replacing the summary table:
    (a) steady-state Jain's index J across the 3 phases, (b) initial convergence time T_90^init,
    and (c) re-adaptation time Delta T_90^+400 after the +400 join event."""
    import numpy as np

    series_list = list(data["series"].cat.categories)
    n_series = len(series_list)
    x = np.arange(len(shapes))
    width = 0.80 / n_series

    fig = plt.figure(figsize=(COLUMN_WIDTH, 1.38))
    gs = fig.add_gridspec(1, 3, width_ratios=[1.0, 1.0, 1.0], left=0.09, right=0.995, top=0.86, bottom=0.28, wspace=0.25)
    ax_j = fig.add_subplot(gs[0, 0])
    gs_t = gs[0, 1:].subgridspec(1, 2, wspace=0.08)
    ax_t0 = fig.add_subplot(gs_t[0, 0])
    ax_t1 = fig.add_subplot(gs_t[0, 1], sharey=ax_t0)

    # Steady-state J averaged across the three converged checkpoints (t=590, 1190, 1790) per seed
    checkpoints = data[data[config.TIME_COLUMN].isin([590.0, 1190.0, 1790.0])]
    per_seed_j = checkpoints.groupby(["series", config.FACET_VARIABLE, "seed"], observed=True)["jain"].mean().reset_index()

    def compute_t90(lo: float, hi: float) -> pd.DataFrame:
        rows = []
        sub = data[(data[config.TIME_COLUMN] >= lo) & (data[config.TIME_COLUMN] <= hi)]
        for (ser, shp, sd), grp in sub.groupby(["series", config.FACET_VARIABLE, "seed"], observed=True):
            hit = grp.sort_values(config.TIME_COLUMN)
            hit = hit[hit["jain"] >= 0.90]
            if not hit.empty:
                rows.append({"series": ser, "shape": shp, "seed": sd, "t90": float(hit[config.TIME_COLUMN].iloc[0] - lo)})
        return pd.DataFrame(rows)

    t90_init_df = compute_t90(0.0, 590.0)
    t90_join_df = compute_t90(600.0, 1190.0)

    for i, ser in enumerate(series_list):
        offset = (i - (n_series - 1) / 2) * width
        j_means, j_stds = [], []
        t0_means, t0_stds = [], []
        t1_means, t1_stds = [], []
        for shp in shapes:
            vals = per_seed_j[(per_seed_j["series"] == ser) & (per_seed_j[config.FACET_VARIABLE] == shp)]["jain"]
            j_means.append(vals.mean() if not vals.empty else 0.0)
            j_stds.append(vals.std() if len(vals) > 1 else 0.0)

            for df_t, means, stds in ((t90_init_df, t0_means, t0_stds), (t90_join_df, t1_means, t1_stds)):
                tvals = df_t[(df_t["series"] == ser) & (df_t["shape"] == shp)]["t90"] if not df_t.empty else pd.Series(dtype=float)
                if len(tvals) >= 9:
                    means.append(tvals.mean())
                    stds.append(tvals.std())
                else:
                    means.append(0.0)
                    stds.append(0.0)

        err_kw = {"elinewidth": 0.45, "capsize": 1.0, "capthick": 0.45, "ecolor": "#252525"}
        ax_j.bar(x + offset, j_means, width=width * 0.92, color=palette[ser], edgecolor="none",
                 yerr=j_stds, error_kw=err_kw, label=ser, zorder=3)
        ax_t0.bar(x + offset, t0_means, width=width * 0.92, color=palette[ser], edgecolor="none",
                  yerr=t0_stds, error_kw=err_kw, label=ser, zorder=3)
        ax_t1.bar(x + offset, t1_means, width=width * 0.92, color=palette[ser], edgecolor="none",
                  yerr=t1_stds, error_kw=err_kw, label=ser, zorder=3)
        for ax_curr, means in ((ax_t0, t0_means), (ax_t1, t1_means)):
            for xi, tm in zip(x, means):
                if tm == 0.0:
                    ax_curr.plot(xi + offset, 22, marker="x", color=palette[ser], markersize=2.5,
                                 markeredgewidth=0.55, zorder=4)

    short_shapes = ["cir", "sta", "hex", "rin", "cre"]
    ax_j.set_xticks(x, short_shapes, fontsize=6.2)
    ax_j.set_ylim(0.0, 1.05)
    ax_j.set_yticks([0.0, 0.25, 0.5, 0.75, 1.0], ["0", ".25", ".5", ".75", "1"])
    ax_j.set_ylabel(r"Fairness $J$", labelpad=1.5, fontsize=7.2)
    ax_j.set_title(r"(a) Steady-state $J$", fontsize=7.0, pad=2.0)
    ax_j.tick_params(axis="both", which="major", pad=1.0, length=1.5, width=0.5, labelsize=6.2)
    ax_j.grid(axis="x")

    for ax_curr, title, show_yticklabels in (
        (ax_t0, r"(b) $T_{90}^{\text{init}}$ (s)", True),
        (ax_t1, r"(c) $\Delta T_{90}^{+400}$ (s)", False),
    ):
        ax_curr.set_xticks(x, short_shapes, fontsize=6.2)
        ax_curr.set_ylim(0, 600)
        ax_curr.set_yticks([0, 200, 400, 600])
        if not show_yticklabels:
            ax_curr.tick_params(labelleft=False)
        ax_curr.set_title(title, fontsize=7.0, pad=2.0)
        ax_curr.tick_params(axis="both", which="major", pad=1.0, length=1.5, width=0.5, labelsize=6.2)
        ax_curr.grid(axis="x")

    handles = scenario_handles(data, palette)
    fig.legend(handles=handles, loc="upper center", ncol=2, fontsize=6.3, frameon=False,
               bbox_to_anchor=(0.52, 0.14), handlelength=1.1, handletextpad=0.3, columnspacing=0.8)
    paper_dir = config.ROOT / "charts" / "paper"
    paper_dir.mkdir(parents=True, exist_ok=True)
    for fmt in formats:
        fig.savefig(paper_dir / f"summary_bars.{fmt}", bbox_inches="tight", pad_inches=0.02, dpi=300)
    plt.close(fig)


def plot_overview(data, shape, metrics, palette, events, formats) -> None:
    columns = 4
    rows = math.ceil(len(metrics) / columns)
    fig, axes = plt.subplots(rows, columns, figsize=(4.0 * columns, 2.8 * rows), sharex=True, squeeze=False)
    frame = data[data[config.FACET_VARIABLE] == shape]
    for ax, metric in zip(axes.flat, metrics):
        draw_metric(ax, frame, metric, palette, events, annotate=False)
        ax.set_title(metric_label(metric), fontsize=9)
        ax.set_ylabel("")
    for ax in list(axes.flat)[len(metrics):]:
        ax.set_visible(False)
    handles = scenario_handles(data, palette) + event_handles(events)
    fig.legend(handles=handles, loc="lower center", ncol=len(handles), fontsize=9, frameon=False,
               bbox_to_anchor=(0.5, -0.03))
    fig.suptitle(f"Dynamic population — {shape}")
    fig.tight_layout()
    save(fig, config.CHARTS_DIR / "overview" / f"overview_{shape}", formats)


def write_summary(data, metrics) -> None:
    keys = ["scenario", *loader.configuration_columns(data), config.TIME_COLUMN]
    grouped = data.groupby(keys, observed=True)[metrics]
    summary = pd.concat({"mean": grouped.mean(), "std": grouped.std(), "n": grouped.count()}, axis=1)
    summary.columns = [f"{metric}_{stat}" for stat, metric in summary.columns]
    config.CHARTS_DIR.mkdir(parents=True, exist_ok=True)
    summary.sort_index(axis=1).to_csv(config.CHARTS_DIR / "summary.csv")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--metrics", nargs="+", help="metrics to plot (default: all)")
    parser.add_argument("--shapes", nargs="+", help="shapes to plot (default: all)")
    parser.add_argument("--kind", nargs="+", choices=["single", "grid", "overview"],
                        default=["single", "grid", "overview"])
    parser.add_argument("--format", nargs="+", default=["pdf", "png"], help="pdf, png, svg, ... (default: pdf png)")
    parser.add_argument("--no-cache", action="store_true", help="parse every file again")
    args = parser.parse_args()

    data = loader.load(use_cache=not args.no_cache)
    if data.empty:
        raise SystemExit(f"No data in {config.DATA_DIR}")
    metrics = [m for m in loader.metric_columns(data) if not args.metrics or m in args.metrics]
    available_shapes = set(data[config.FACET_VARIABLE].unique())
    defined = loader.defined_shapes()
    undefined = available_shapes - set(defined)
    if undefined:
        print(f"[plot] shapes in the data but not in {config.SHAPES_FILE.name}, skipped: {', '.join(sorted(undefined))}")
    shapes = [s for s in defined if s in available_shapes and (not args.shapes or s in args.shapes)]
    if not shapes:
        raise SystemExit("No shape to plot")
    events = population_events(data)
    palette = series_palette(data)

    apply_paper_style()
    if "single" in args.kind:
        for metric in metrics:
            for shape in shapes:
                plot_single(data, metric, shape, palette, events, args.format)
    if "grid" in args.kind:
        for metric in metrics:
            plot_grid(data, metric, shapes, palette, events, args.format)
        for stack_metrics, stem in (
            (["jain", "inside"], "dynamic_jain_inside"),
            (["jain", "inside", "motion"], "dynamic_paper"),
        ):
            if all(m in data.columns for m in stack_metrics):
                plot_paper_stack(data, stack_metrics, shapes, palette, events, args.format, stem)
        if "jain" in data.columns:
            plot_paper_summary_bars(data, shapes, palette, args.format)
    if "overview" in args.kind:
        for shape in shapes:
            plot_overview(data, shape, metrics, palette, events, args.format)
    write_summary(data, metrics)
    print(f"[plot] {len(metrics)} metrics x {len(shapes)} shapes, scenarios: "
          f"{', '.join(data['series'].cat.categories)} -> {config.CHARTS_DIR}")


if __name__ == "__main__":
    main()
