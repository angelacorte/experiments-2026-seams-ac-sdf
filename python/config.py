"""Configuration shared by the plotters: paths, scenarios, metrics and the population events."""

from pathlib import Path

PYTHON_DIR = Path(__file__).resolve().parent
ROOT = PYTHON_DIR.parent
DATA_DIR = ROOT / "data"
YAML_DIR = ROOT / "src" / "main" / "yaml"
CHARTS_DIR = ROOT / "charts" / "dynamic"
CACHE_DIR = PYTHON_DIR / ".cache"

# Data folder (= fileNameRoot of the export) -> label in the legend. The order is the order of the plot (and of the
# viridis colors). A scenario with no data yet (e.g. dynamicDistanceBased) is simply skipped.
SCENARIOS = {
    "dynamicPositionBased": "Position-based",
    "dynamicDisplacementBased": "Displacement-based",
    "dynamicDistanceBased": "Distance-based",
}

# Variables of the header that are random and get folded (mean +- spread over them). Every other variable of the
# header (e.g. shape) identifies a configuration.
SEED_VARS = ("seed",)
TIME_COLUMN = "time"
# The exporter writes every 10 time units, with a tiny offset that changes between runs: times are snapped to this grid
EXPORT_INTERVAL = 10.0

# The variable of the header with one chart (or panel) per value. Every other variable of the header but the seeds
# that takes more than one value within a scenario splits that scenario into more lines (e.g. the start of
# dynamicPositionBased: "Position-based (left start)" and "Position-based (centered start)").
FACET_VARIABLE = "shape"

# Value of a variable for the files of a scenario whose header lacks it (exported before the variable existed):
# the old dynamicPositionBased runs deployed the nodes to the left of the shape (LeftOfShape).
# A run that a newer file repeats (same scenario, configuration and seed) is dropped in favor of the newer file.
VARIABLE_DEFAULTS = {
    "dynamicPositionBased": {"start": "left"},
}

# How the values of the variables that split a scenario read in the legend, in their order (by default "name=value")
VALUE_LABELS = {
    "start": {"left": "outside start", "centered": "centered start"},
}

# The shapes, in the order of the multi-shape figures: those of this file that have data (the others are skipped)
SHAPES_FILE = ROOT / "src" / "main" / "resources" / "shapes.yml"
# Panels per row in the multi-shape figures
GRID_COLUMNS = 5

# Metric -> (label on the y axis, log scale). Columns not listed here are plotted with their name.
METRICS = {
    "inside": ("Inside fraction", False),
    "jain": (r"Jain's index $J(t)$", False),
    "shareCV": ("CV of the shares", False),
    "cover95": ("Coverage gap, 95th pct", False),
    "coverMax": ("Coverage gap, max", False),
    "nnCV": ("NN distance CV", False),
    "minDist": ("Min NN distance (fill)", False),
    "psi6": (r"Hexagonal order $\psi_6$", False),
    "borderRatio": ("Border ratio", False),
    "fill": ("Lattice spacing fill", False),
    "motion": ("Motion (fill/s)", False),
    "sdfMean": ("Mean neighbor SDF", False),
    "sdfVariance": ("Neighbor SDF variance", False),
    "sdfMeanVariance": ("SDF mean variance", False),
    "neighborDistance": ("Mean neighbor dist.", False),
    "neighborDistanceVariance": ("Neighbor dist. variance", False),
}

# Fallback for the population events, used when the yaml of a scenario cannot be read
DEFAULT_POPULATION = {"nodes": 100, "spawnTime": 600, "spawnCount": 400, "killTime": 1200, "survivors": 50}

# Spread around the mean over the seeds, as seaborn's errorbar: "sd", ("ci", 95), ("pi", 50), "se" or None
ERRORBAR = "sd"
