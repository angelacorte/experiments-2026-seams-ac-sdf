"""Reads the Alchemist exports of the dynamic-population experiments into a pandas DataFrame, with a cache.

Every file is parsed once: the cache (python/.cache/parsed.pkl) remembers each file with its modification time and
size, so that only new or changed files are parsed again, and the files that disappeared are dropped.
"""

from __future__ import annotations

import pickle
import re
from pathlib import Path

import numpy as np
import pandas as pd
import yaml

import config

CACHE_VERSION = 1  # Bump when parse_file changes
_CACHE_FILE = config.CACHE_DIR / "parsed.pkl"
_VARIABLE = re.compile(r"(?P<name>[A-Za-z._-]+) = (?P<value>[^,]*),?")
_FLOAT = re.compile(r"^[-+]?\d*\.?\d+(?:[eE][-+]?\d+)?$")


def _typed(value: str):
    value = value.strip()
    return float(value) if _FLOAT.match(value) else value


def parse_file(path: Path) -> pd.DataFrame:
    """One Alchemist export: the variables of the header (e.g. seed, shape) become constant columns."""
    variables: dict = {}
    columns: list[str] | None = None
    last_comment = ""
    rows: list[list[str]] = []
    with open(path) as file:
        for line in file:
            if line.startswith("#"):
                if not variables and " = " in line:
                    variables = {m["name"]: _typed(m["value"]) for m in _VARIABLE.finditer(line[1:])}
                last_comment = line
            elif line.strip():
                if columns is None:  # The last comment before the data names the columns
                    columns = last_comment.lstrip("#").split()
                rows.append(line.split())
    if columns is None or not rows:
        return pd.DataFrame()
    frame = pd.DataFrame(np.array(rows, dtype=float), columns=columns)
    # Snap the time on the export grid (it carries a small offset that differs between runs), keep the first sample
    frame[config.TIME_COLUMN] = (frame[config.TIME_COLUMN] / config.EXPORT_INTERVAL).round() * config.EXPORT_INTERVAL
    frame = frame.drop_duplicates(config.TIME_COLUMN, keep="first")
    if not variables:  # Fall back on the file name: <root>_seed-0.0_shape-star.csv
        for key, value in re.findall(r"_([A-Za-z]+)-([^_]+?)(?=_|\.csv$)", path.name):
            variables[key] = _typed(value)
    for key, value in variables.items():
        frame[key] = value
    frame.attrs["variables"] = list(variables)
    return frame


def _load_cache() -> dict:
    try:
        with open(_CACHE_FILE, "rb") as file:
            cache = pickle.load(file)
        if cache.get("version") == CACHE_VERSION:
            return cache
    except Exception:  # Unreadable, or written by another version of pandas: parse again
        pass
    return {"version": CACHE_VERSION, "files": {}}


def load(scenarios: dict[str, str] | None = None, use_cache: bool = True, verbose: bool = True) -> pd.DataFrame:
    """All the runs of the scenarios that have data, in one frame with the columns scenario, label, the variables of
    the header (seed, shape, ...), time and the metrics."""
    scenarios = scenarios or config.SCENARIOS
    cache = _load_cache() if use_cache else {"version": CACHE_VERSION, "files": {}}
    entries = cache["files"]
    seen, parsed, frames = set(), 0, []
    for scenario, label in scenarios.items():
        folder = config.DATA_DIR / scenario
        if not folder.is_dir():
            if verbose:
                print(f"[loader] no data for {scenario}: skipped")
            continue
        for path in sorted(folder.glob(f"{scenario}_*.csv")):
            key = str(path.relative_to(config.DATA_DIR))
            stat = path.stat()
            signature = (stat.st_mtime_ns, stat.st_size)
            seen.add(key)
            entry = entries.get(key)
            if entry is None or entry[0] != signature:
                entry = entries[key] = (signature, parse_file(path))
                parsed += 1
            frame = entry[1]
            if not frame.empty:
                frames.append(frame.assign(scenario=scenario, label=label, file=key, mtime=signature[0]))
    removed = set(entries) - seen
    for key in removed:
        del entries[key]
    if use_cache and (parsed or removed):
        config.CACHE_DIR.mkdir(parents=True, exist_ok=True)
        with open(_CACHE_FILE, "wb") as file:
            pickle.dump(cache, file, protocol=pickle.HIGHEST_PROTOCOL)
    if verbose:
        print(f"[loader] {len(seen)} files: {parsed} parsed, {len(seen) - parsed} from the cache")
    if not frames:
        return pd.DataFrame()
    variables = list(dict.fromkeys(v for f in frames for v in f.attrs.get("variables", [])))
    data = pd.concat(frames, ignore_index=True)
    configuration = [v for v in variables if v not in config.SEED_VARS]
    for variable in configuration:
        for scenario, defaults in config.VARIABLE_DEFAULTS.items():
            if variable in defaults:
                missing = (data["scenario"] == scenario) & data[variable].isna()
                data.loc[missing, variable] = defaults[variable]
        # Strings, so that grouping never trips on a mix of types or on NaN (a variable a scenario lacks reads "-")
        data[variable] = data[variable].map(_text)
    data = _drop_superseded(data, configuration, verbose)
    data.attrs["configuration"] = configuration
    data["label"] = pd.Categorical(data["label"], categories=[l for s, l in scenarios.items()
                                                                if s in set(data["scenario"])], ordered=True)
    data["series"] = _series(data, scenarios, configuration)
    return data


def _text(value) -> str:
    if value is None or (isinstance(value, float) and np.isnan(value)):
        return "-"
    if isinstance(value, float) and value.is_integer():
        return str(int(value))
    return str(value)


def _drop_superseded(data: pd.DataFrame, configuration: list[str], verbose: bool) -> pd.DataFrame:
    """Keeps, for every scenario, configuration and seed, only the newest file (an old export repeated by a new one)."""
    keys = ["scenario", *configuration, *[s for s in config.SEED_VARS if s in data.columns]]
    files = data.drop_duplicates("file")[["file", "mtime", *keys]].sort_values("mtime")
    kept = files.drop_duplicates(keys, keep="last")["file"]
    if len(kept) < len(files):
        if verbose:
            superseded = sorted(set(files["file"]) - set(kept))
            print(f"[loader] {len(superseded)} files superseded by newer runs, ignored: {', '.join(superseded)}")
        data = data[data["file"].isin(set(kept))]
    return data.reset_index(drop=True)


def _ordered(variable: str, values) -> list[str]:
    known = list(config.VALUE_LABELS.get(variable, {}))
    return [v for v in known if v in values] + sorted(v for v in values if v not in known)


def _series(data: pd.DataFrame, scenarios: dict[str, str], configuration: list[str]) -> pd.Categorical:
    """The line each run belongs to: the scenario, split by the variables (but the facet one) that take more than one
    value within it."""
    splitting = [v for v in configuration if v != config.FACET_VARIABLE]
    names = pd.Series(data["label"].astype(str), index=data.index)
    order = []
    for scenario, label in scenarios.items():
        rows = data["scenario"] == scenario
        if not rows.any():
            continue
        varying = [v for v in splitting if data.loc[rows, v].nunique() > 1]
        if not varying:
            order.append(label)
            continue
        combos = data.loc[rows, varying].drop_duplicates()
        combos = combos.sort_values(varying, key=lambda column: column.map(
            {value: i for i, value in enumerate(_ordered(column.name, set(column)))}))
        for combo in combos.itertuples(index=False):
            parts = [config.VALUE_LABELS.get(v, {}).get(value, f"{v}={value}") for v, value in zip(varying, combo)]
            name = f"{label} ({', '.join(parts)})"
            order.append(name)
            match = rows.copy()
            for v, value in zip(varying, combo):
                match &= data[v] == value
            names[match] = name
    return pd.Categorical(names, categories=order, ordered=True)


def configuration_columns(data: pd.DataFrame) -> list[str]:
    """The variables of the header that identify a configuration (all but the seeds), e.g. shape."""
    return list(data.attrs.get("configuration", []))


def metric_columns(data: pd.DataFrame) -> list[str]:
    """The metrics in the data (every numeric column but the time and the variables of the header)."""
    excluded = {config.TIME_COLUMN, "scenario", "label", "series", "file", "mtime", *config.SEED_VARS,
                *configuration_columns(data)}
    numeric = [c for c in data.columns if c not in excluded and pd.api.types.is_numeric_dtype(data[c])]
    known = [m for m in config.METRICS if m in numeric]
    return known + [m for m in numeric if m not in known]


def _text(value) -> str:
    """The text of a yaml value: a string, or the string of a formula like `{ formula: "'name'" }`."""
    if isinstance(value, dict):
        value = value.get("formula", "")
    return str(value).strip().strip("'\"")


def yaml_variables(name: str) -> dict | None:
    """The variables of the yaml whose exports are named [name] (its `filename` variable, or the fileNameRoot or the
    last folder of the exportPath of one of its exporters), or None."""
    for path in sorted(config.YAML_DIR.glob("*.yml")):
        try:
            document = yaml.safe_load(path.read_text()) or {}
        except yaml.YAMLError:
            continue
        variables = document.get("variables", {}) or {}
        names = {_text(variables.get("filename", ""))}
        for exporter in document.get("export", []) or []:
            parameters = exporter.get("parameters", {}) if isinstance(exporter, dict) else {}
            if isinstance(parameters, dict):
                names.add(_text(parameters.get("fileNameRoot", "")))
                names.add(_text(parameters.get("exportPath", "")).rstrip("/").split("/")[-1])
        if name in names:
            return variables
    return None


def population_events(scenario: str) -> dict:
    """nodes, spawnTime, spawnCount, killTime, survivors from the yaml of [scenario] (the defaults when missing)."""
    values = dict(config.DEFAULT_POPULATION)
    for key, value in (yaml_variables(scenario) or {}).items():
        if key in values and isinstance(value, (int, float)):
            values[key] = value
    return values


def defined_shapes() -> list[str]:
    """The names of the shapes in src/main/resources/shapes.yml, in their order."""
    with open(config.SHAPES_FILE) as file:
        return list((yaml.safe_load(file) or {}).keys())
