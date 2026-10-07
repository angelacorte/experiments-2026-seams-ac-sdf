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
        Line2D([], [], color=EVENT_COLOR, ls=SPAWN_STYLE, lw=1,
               label=f"t={events['spawnTime']:g}: +{events['spawnCount']} nodes ({events['nodes']}→{grown})"),
        Line2D([], [], color=EVENT_COLOR, ls=KILL_STYLE, lw=1.3,
               label=f"t={events['killTime']:g}: down to {events['survivors']} nodes ({grown}→{events['survivors']})"),
    ]


def draw_events(ax, events: dict, annotate: bool) -> None:
    grown = events["nodes"] + events["spawnCount"]
    for time, style, width, text in (
        (events["spawnTime"], SPAWN_STYLE, 1.0, f"+{events['spawnCount']}"),
        (events["killTime"], KILL_STYLE, 1.3, f"{grown}→{events['survivors']}"),
    ):
        ax.axvline(time, color=EVENT_COLOR, ls=style, lw=width, zorder=1)
        if annotate:
            ax.annotate(text, xy=(time, 1), xycoords=("data", "axes fraction"), xytext=(3, -3),
                        textcoords="offset points", ha="left", va="top", fontsize=7, color=EVENT_COLOR)


def scenario_handles(data: pd.DataFrame, palette: dict) -> list[Line2D]:
    return [Line2D([], [], color=palette[label], lw=2, label=label) for label in data["series"].cat.categories]


def draw_metric(ax, frame: pd.DataFrame, metric: str, palette: dict, events: dict, annotate: bool) -> None:
    frame = frame.dropna(subset=[metric])
    if not frame.empty:
        sns.lineplot(data=frame, x=config.TIME_COLUMN, y=metric, hue="series", palette=palette, errorbar=config.ERRORBAR,
                     err_kws={"alpha": 0.2, "lw": 0}, lw=1.2, legend=False, ax=ax)
    draw_events(ax, events, annotate)
    if metric_log(metric):
        ax.set_yscale("log")
    ax.set_xlim(0, frame[config.TIME_COLUMN].max() if not frame.empty else None)
    ax.set_xlabel("Time")
    ax.set_ylabel(metric_label(metric))


def save(fig, path_without_suffix, formats) -> None:
    for fmt in formats:  # <folder>/<fmt>/<name>.<fmt>
        path = path_without_suffix.parent / fmt / f"{path_without_suffix.name}.{fmt}"
        path.parent.mkdir(parents=True, exist_ok=True)
        fig.savefig(path, bbox_inches="tight", dpi=200)
    plt.close(fig)


def plot_single(data, metric, shape, palette, events, formats) -> None:
    fig, ax = plt.subplots(figsize=(6.4, 3.6))
    draw_metric(ax, data[data[config.FACET_VARIABLE] == shape], metric, palette, events, annotate=True)
    ax.set_title(f"{metric_label(metric)} — {shape}")
    ax.legend(handles=scenario_handles(data, palette) + event_handles(events), fontsize=7, loc="best", framealpha=0.9)
    save(fig, config.CHARTS_DIR / metric / f"{metric}_{shape}", formats)


def plot_grid(data, metric, shapes, palette, events, formats) -> None:
    columns = min(len(shapes), config.GRID_COLUMNS)
    rows = math.ceil(len(shapes) / columns)
    fig, axes = plt.subplots(rows, columns, figsize=(3.2 * columns, 3.0 * rows), sharex=True, sharey=True,
                             squeeze=False)
    for index, (ax, shape) in enumerate(zip(axes.flat, shapes)):
        draw_metric(ax, data[data[config.FACET_VARIABLE] == shape], metric, palette, events, annotate=False)
        ax.set_title(shape)
        if index % columns:
            ax.set_ylabel("")
    for index, ax in enumerate(axes.flat):
        if index >= len(shapes):
            ax.set_visible(False)
        elif index + columns >= len(shapes):  # Nothing below it: it carries the time axis
            ax.xaxis.set_tick_params(labelbottom=True)
            ax.set_xlabel("Time")
    handles = scenario_handles(data, palette) + event_handles(events)
    fig.legend(handles=handles, loc="lower center", ncol=len(handles), fontsize=8, frameon=False,
               bbox_to_anchor=(0.5, -0.08 / rows))
    fig.suptitle(metric_label(metric))
    fig.tight_layout()
    save(fig, config.CHARTS_DIR / metric / f"{metric}_all-shapes", formats)


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

    sns.set_theme(style="whitegrid", context="paper")
    if "single" in args.kind:
        for metric in metrics:
            for shape in shapes:
                plot_single(data, metric, shape, palette, events, args.format)
    if "grid" in args.kind:
        for metric in metrics:
            plot_grid(data, metric, shapes, palette, events, args.format)
    if "overview" in args.kind:
        for shape in shapes:
            plot_overview(data, shape, metrics, palette, events, args.format)
    write_summary(data, metrics)
    print(f"[plot] {len(metrics)} metrics x {len(shapes)} shapes, scenarios: "
          f"{', '.join(data['series'].cat.categories)} -> {config.CHARTS_DIR}")


if __name__ == "__main__":
    main()
