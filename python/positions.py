"""Reads the exports of DevicePositionsExporter (data/positions/<scenario>/), with a cache, and draws one frame of a
run: the devices colored by the SDF of the shape at their position, the leader and the anchors, and optionally the
shape itself (where the devices place it: fixed for the position-based scenarios, on the leader or the anchors for the
others).

Files of a run (same names as the metrics, see DevicePositionsExporter.kt):
- <root>_<variables>.csv            time id x y sdf leader anchor
- <root>_<variables>_placement.csv  time shape ox oy ax ay bx by
- shapes/<shape>.csv                the SDF of the shape on a grid, in the coordinates of shapes.yml
"""

from __future__ import annotations

import pickle
import re
from dataclasses import dataclass, field
from functools import cached_property
from pathlib import Path

import numpy as np
import pandas as pd

import config

POSITIONS_DIR = config.DATA_DIR / "positions"
CHARTS_DIR = config.ROOT / "charts" / "positions"
CACHE_DIR = config.CACHE_DIR / "positions"
CACHE_VERSION = 3

DEVICE_COLUMNS = ["time", "id", "x", "y", "sdf", "leader", "anchor"]
PLACEMENT_COLUMNS = ["time", "shape", "ox", "oy", "ax", "ay", "bx", "by"]
_VARIABLE = re.compile(r"(?P<name>[A-Za-z._-]+) = (?P<value>[^,]*),?")

# Look of the frames
SHAPE_FILL = "#9a9a9a"
SHAPE_FILL_ALPHA = 0.22
SHAPE_EDGE = "#3d3d3d"
LEADER_EDGE = "#d62728"
ANCHOR_EDGE = "#d62728"
UNPLACED_COLOR = "#bdbdbd"  # Devices before the shape is placed (no leader/anchors yet): no SDF
TRAIL_COLOR = "#7a7a7a"


def _header_variables(path: Path) -> dict[str, str]:
    with open(path) as file:
        for line in file:
            if not line.startswith("#"):
                break
            if " = " in line:
                return {m["name"]: m["value"].strip() for m in _VARIABLE.finditer(line[1:])}
    return {}


def _read(path: Path, columns: list[str]) -> pd.DataFrame:
    if not path.exists():
        return pd.DataFrame(columns=columns)
    return pd.read_csv(path, sep=" ", comment="#", header=None, names=columns, na_values=["NaN"])


def export_interval(frame: pd.DataFrame) -> float:
    """The interval of the exports (the exporter's `interval` in the yaml), from the data: the usual gap between two
    exports (the first one, at 0, is followed by another one right after it, which is ignored)."""
    times = np.sort(frame["time"].unique())
    gaps = np.diff(times)
    gaps = gaps[gaps > 1e-6]
    if len(gaps) == 0:
        return config.EXPORT_INTERVAL
    return float(np.round(np.median(gaps), 6))


def _snap(frame: pd.DataFrame, interval: float) -> pd.DataFrame:
    """Times on the export grid; of two exports on the same grid point (the first, at 0, and the one right after),
    the first is kept."""
    if frame.empty:
        return frame
    raw = frame["time"]
    frame = frame.assign(time=(raw / interval).round() * interval)
    first = raw.groupby(frame["time"]).transform("min")
    return frame[raw == first].reset_index(drop=True)


@dataclass
class Raster:
    """The SDF of a shape on a grid, in the coordinates of shapes.yml."""

    name: str
    u: np.ndarray  # 1D, along x
    v: np.ndarray  # 1D, along y
    values: np.ndarray  # rows along v

    @property
    def depth(self) -> float:
        """How deep the shape goes (the largest distance of a point inside it from its border)."""
        return float(max(-np.nanmin(self.values), 1e-9))

    @staticmethod
    def read(path: Path) -> "Raster":
        meta = {}
        with open(path) as file:
            for line in file:
                if not line.startswith("#"):
                    break
                meta.update({m["name"]: m["value"].strip() for m in _VARIABLE.finditer(line[1:])})
        values = np.loadtxt(path, comments="#", ndmin=2)
        step = float(meta["step"])
        u = float(meta["xmin"]) + step * np.arange(values.shape[1])
        v = float(meta["ymin"]) + step * np.arange(values.shape[0])
        return Raster(meta.get("shape", path.stem), u, v, values)


@dataclass
class Run:
    """One simulation: its scenario, the variables of the header, the devices and the placements of the shape."""

    scenario: str
    name: str
    variables: dict[str, str]
    devices: pd.DataFrame
    placements: pd.DataFrame
    rasters: dict[str, Raster] = field(default_factory=dict)
    interval: float = config.EXPORT_INTERVAL  # Of the exports

    @property
    def label(self) -> str:
        if self.scenario in config.SCENARIOS:
            return config.SCENARIOS[self.scenario]
        words = re.sub(r"(?<!^)(?=[A-Z])", " ", self.scenario).lower()
        return words[:1].upper() + words[1:].replace(" based", "-based")

    @property
    def description(self) -> str:
        parts = [self.variables.get("shape", "")]
        if "start" in self.variables:
            parts.append(config.VALUE_LABELS.get("start", {}).get(self.variables["start"], self.variables["start"]))
        if "seed" in self.variables:
            parts.append(f"seed {self.variables['seed'].removesuffix('.0')}")
        parts += [f"{k}={v}" for k, v in self.variables.items() if k not in {"shape", "start", "seed"}]
        return ", ".join(p for p in parts if p)

    @cached_property
    def times(self) -> np.ndarray:
        return np.sort(self.devices["time"].unique())

    @cached_property
    def _by_time(self) -> dict[float, pd.DataFrame]:
        return {t: frame for t, frame in self.devices.groupby("time", sort=True)}

    def nearest_time(self, time: float) -> float:
        return float(self.times[np.argmin(np.abs(self.times - time))])

    def at(self, time: float) -> pd.DataFrame:
        return self._by_time[self.nearest_time(time)]

    def placement_at(self, time: float) -> pd.Series | None:
        """The placement of the shape at the export nearest to [time] (None before the shape is placed)."""
        if self.placements.empty:
            return None
        rows = self.placements[self.placements["time"] <= self.nearest_time(time) + 1e-9]
        return None if rows.empty else rows.iloc[-1]

    def shape_outline(self, time: float):
        """The grid of the shape moved where it lies at [time] (x, y, sdf), or None."""
        where = self.placement_at(time)
        raster = self.rasters.get(where["shape"]) if where is not None else None
        if raster is None:
            return None
        uu, vv = np.meshgrid(raster.u, raster.v)
        x = where["ox"] + uu * where["ax"] + vv * where["bx"]
        y = where["oy"] + uu * where["ay"] + vv * where["by"]
        return x, y, raster.values

    @property
    def depth(self) -> float:
        """The scale of the colors: the depth of the shape (or the deepest device, without the shape)."""
        if self.rasters:
            return max(r.depth for r in self.rasters.values())
        return float(max(-self.devices["sdf"].min(skipna=True), 1.0))

    @cached_property
    def limits(self) -> tuple[float, float, float, float]:
        """The same view for every frame: all the positions and all the placements of the shape, plus a margin."""
        xs = [self.devices["x"].min(), self.devices["x"].max()]
        ys = [self.devices["y"].min(), self.devices["y"].max()]
        for _, where in self.placements.drop_duplicates(["shape", "ox", "oy", "ax", "ay", "bx", "by"]).iterrows():
            raster = self.rasters.get(where["shape"])
            if raster is None:
                continue
            inside = raster.values <= 0
            uu, vv = np.meshgrid(raster.u, raster.v)
            u, v = uu[inside], vv[inside]
            xs += list(where["ox"] + u * where["ax"] + v * where["bx"])
            ys += list(where["oy"] + u * where["ay"] + v * where["by"])
        x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
        pad = 0.04 * max(x1 - x0, y1 - y0, 1.0)
        return x0 - pad, x1 + pad, y0 - pad, y1 + pad


def scenario_folders() -> list[str]:
    """Every folder of data/positions: those of config.SCENARIOS first, in their order, then the others (e.g. the
    single runs of positionBased.yml)."""
    if not POSITIONS_DIR.is_dir():
        return []
    present = sorted(p.name for p in POSITIONS_DIR.iterdir() if p.is_dir())
    return [s for s in config.SCENARIOS if s in present] + [s for s in present if s not in config.SCENARIOS]


def run_files(scenarios: list[str] | None = None) -> list[tuple[str, Path]]:
    """Every run in data/positions/<scenario>/ (any file name, as fileNameRoot may differ from the folder)."""
    found = []
    for scenario in scenarios or scenario_folders():
        for path in sorted((POSITIONS_DIR / scenario).glob("*.csv")):
            if not path.stem.endswith("_placement"):
                found.append((scenario, path))
    return found


def _finished(path: Path) -> bool:
    """Whether the export is complete (the exporter closes it with an "End of data export" line)."""
    with open(path, "rb") as file:
        file.seek(0, 2)
        file.seek(max(0, file.tell() - 400))
        return b"End of data export" in file.read()


def _signature(*paths: Path) -> tuple:
    return tuple((p.stat().st_mtime_ns, p.stat().st_size) if p.exists() else None for p in paths)


def load_run(scenario: str, path: Path, use_cache: bool = True) -> Run:
    placement_path = path.with_name(f"{path.stem}_placement.csv")
    cache = CACHE_DIR / scenario / f"{path.stem}.pkl"
    signature = (CACHE_VERSION, _signature(path, placement_path))
    if use_cache and cache.exists():
        try:
            with open(cache, "rb") as file:
                stored_signature, run = pickle.load(file)
            if stored_signature == signature:
                return run
        except (OSError, pickle.UnpicklingError, EOFError, AttributeError, ImportError):
            pass
    raw_devices = _read(path, DEVICE_COLUMNS)
    interval = export_interval(raw_devices) if not raw_devices.empty else config.EXPORT_INTERVAL
    devices = _snap(raw_devices, interval)
    if not devices.empty and not _finished(path):
        # The simulation is still running (or was stopped): the last export may be written only in part
        devices = devices[devices["time"] < devices["time"].max()].reset_index(drop=True)
    devices = devices.astype({"id": int, "leader": int, "anchor": int})
    placements = _snap(_read(placement_path, PLACEMENT_COLUMNS), interval)
    rasters = {}
    for name in placements["shape"].unique() if not placements.empty else []:
        raster_path = path.parent / "shapes" / f"{name}.csv"
        if raster_path.exists():
            rasters[name] = Raster.read(raster_path)
    run = Run(scenario, path.stem, _header_variables(path), devices, placements, rasters, interval)
    if use_cache:
        cache.parent.mkdir(parents=True, exist_ok=True)
        with open(cache, "wb") as file:
            pickle.dump((signature, run), file, protocol=pickle.HIGHEST_PROTOCOL)
    return run


def select_runs(
    scenarios: list[str] | None,
    filters: dict[str, list[str]],
    all_seeds: bool,
) -> list[tuple[str, Path]]:
    """The runs whose header variables match [filters] (name -> accepted values); unless [all_seeds], only the
    smallest seed of every configuration."""
    rows = []
    for scenario, path in run_files(scenarios):
        variables = _header_variables(path)
        if all(_matches(variables.get(k), accepted) for k, accepted in filters.items()):
            rows.append((scenario, path, variables))
    if all_seeds or "seed" in filters:
        return [(s, p) for s, p, _ in rows]
    chosen: dict[tuple, tuple] = {}
    for scenario, path, variables in rows:
        key = (scenario, tuple(sorted((k, v) for k, v in variables.items() if k not in config.SEED_VARS)))
        seed = float(variables.get("seed", "0") or 0)
        if key not in chosen or seed < chosen[key][0]:
            chosen[key] = (seed, scenario, path)
    return [(s, p) for _, s, p in sorted(chosen.values(), key=lambda c: str(c[2]))]


def _matches(value: str | None, accepted: list[str]) -> bool:
    if value is None:
        return False
    for candidate in accepted:
        try:
            if float(candidate) == float(value):
                return True
        except ValueError:
            if candidate == value:
                return True
    return False


def parse_filters(items: list[str] | None) -> dict[str, list[str]]:
    """name=value[,value...] -> {name: [values]}."""
    filters = {}
    for item in items or []:
        name, _, values = item.partition("=")
        if not values:
            raise SystemExit(f"Bad filter {item!r}: use name=value[,value...]")
        filters[name.strip()] = [v.strip() for v in values.split(",")]
    return filters


def population_events(scenario: str) -> dict | None:
    """The population events (spawn and kill) of the yaml that exports in data/positions/<scenario>, or None when it
    has none (e.g. positionBased.yml)."""
    import yaml

    for path in sorted(config.YAML_DIR.glob("*.yml")):
        text = path.read_text()
        if f"data/positions/{scenario}" not in text:
            continue
        try:
            variables = (yaml.safe_load(text) or {}).get("variables", {})
        except yaml.YAMLError:
            return None
        keys = ["nodes", "spawnTime", "spawnCount", "killTime", "survivors"]
        values = {k: variables.get(k) for k in keys}
        return values if all(isinstance(v, (int, float)) for v in values.values()) else None
    return None
