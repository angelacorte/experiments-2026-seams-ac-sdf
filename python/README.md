# Plotters

The Python scripts that turn the simulation exports in `data/` into the charts in `charts/`.
Everything is launched from the root of the repository by `process.py`; each plotter can also be run alone.

## Setup

Python 3.14 (see `.python-version`), with the packages of `requirements.txt`:

```bash
python3 -m venv env
source env/bin/activate
pip install -r requirements.txt
```

## Quick start

```bash
python process.py                         # all the plotters, in parallel
python process.py metrics snapshots       # some of them: metrics, snapshots, gifs (or M, S, G)
python process.py S G --where shape=star  # only the runs with the star
python process.py G --until 1000          # gifs from 0 to t = 1000
python process.py --help                  # all the options
```

| Plotter | Script | Input | Output |
|---|---|---|---|
| `metrics` (M) | `plot_dynamic.py` | `data/<scenario>/` | `charts/dynamic/` |
| `snapshots` (S) | `plot_positions_snapshots.py` | `data/positions/<folder>/` | `charts/positions/` |
| `gifs` (G) | `plot_positions_gif.py` | `data/positions/<folder>/` | `charts/positions/` |
| `fairness` (F) | `plot_positions_fairness.py` | `data/positions/<folder>/` | `charts/positions/` |

### One simulation

The plotters (letters) and the simulations (filters) are chosen separately: `--scenario` picks the folder of
`data/positions`, `--where` the values of the variables in the header of the run (`seed`, `shape`, and `start` where
the scenario has it). With `seed` in `--where` that seed is used, otherwise the first seed of each configuration.

```bash
# only the GIFs of one simulation
python process.py G --scenario positionBased --where shape=star seed=42

# snapshots and GIFs of the same simulation
python process.py S G --scenario positionBased --where shape=star seed=42

# one simulation of a batch, only the GIF with the shape in the background, up to t = 1000
python process.py G --scenario dynamicPositionBased --where shape=star start=left seed=3 --shape-overlay on --until 1000

# the snapshots of one simulation at chosen instants
python process.py S --scenario dynamicDisplacementBased --where shape=ring seed=0 --times 0 690 710 990 1010 1500

# the same simulation for two shapes, every seed
python process.py S G --scenario dynamicDistanceBased --where shape=star,ring --all-seeds
```

The values of `--where` accept `3` as well as `3.0`. The metrics (`M`) ignore `--where`: they are averages over the
seeds that compare the scenarios, so a single simulation is shown by the snapshots and the GIFs.

### Options of `process.py`

Each option goes only to the plotters that accept it.

| Option | Plotters | Meaning |
|---|---|---|
| `--times T ...` | snapshots, fairness | these instants (the nearest exports) |
| `--every N` | snapshots, fairness | without `--times`, one chart every N time units (default 50, fairness 20) |
| `--from T`, `--until T` | gifs, snapshots, fairness | first and last instant |
| `--fps N` | gifs | frames per second (default 10) |
| `--frame-step N` | gifs | one frame every N exports (default 1) |
| `--scenario F ...` | snapshots, gifs, fairness | only these folders of `data/positions` |
| `--where NAME=V[,V] ...` | snapshots, gifs, fairness | only the runs whose header variables match, e.g. `shape=star,ring start=left` |
| `--all-seeds` | snapshots, gifs, fairness | every seed (default: the first seed of each configuration) |
| `--shape-overlay off\|on\|both` | snapshots, gifs | shape in the background: without, with, or both versions (default) |
| `--format F ...` | metrics, snapshots, fairness | file formats (default `pdf png`) |
| `--no-cache` | all | parse the data again |
| `--sequential` | all | one plotter at a time, instead of in parallel |

## Metrics over time: `plot_dynamic.py`

The metrics exported by the `CSVExporter` (`FormationMetrics` and `CoverageMetrics`) of the dynamic-population
scenarios (`dynamicPopulation*.yml`), over time.

- One line per scenario in the same chart (viridis colors), the mean over the seeds with a band of ±1 standard
  deviation. The runs with the same parameters are grouped; only the seed is averaged.
- A scenario that batches over a further variable (e.g. the `start` of the position-based one) is split into more
  lines, e.g. "Position-based (outside start)" and "Position-based (centered start)".
- Two vertical lines mark the population events, read from the yaml: the spawn (`spawnTime`, `spawnCount`) and the
  kill (`killTime`, `survivors`).
- The shapes are those of `src/main/resources/shapes.yml`, in that order; those without data are skipped.

Charts, in a `pdf/` and a `png/` subfolder of each folder:

- `charts/dynamic/<metric>/<metric>_<shape>`: one metric, one shape;
- `charts/dynamic/<metric>/<metric>_all-shapes`: one metric, a panel per shape;
- `charts/dynamic/overview/overview_<shape>`: one shape, a panel per metric;
- `charts/dynamic/summary.csv`: mean, standard deviation and number of seeds of every metric, by scenario,
  configuration and time.

```bash
python python/plot_dynamic.py --metrics jain psi6 --shapes star ring --kind grid
```

## Device positions: `plot_positions_snapshots.py` and `plot_positions_gif.py`

The devices in the plane, from the exports of `DevicePositionsExporter`:

- every device is colored by the SDF of the shape at its position: reversed viridis, centered on the border, so
  yellow deep inside the shape, green on the border, purple outside (farther than the depth of the shape saturates);
- the leader is a star and the anchors are diamonds labeled A1, A2, A3 (in the distance-based scenario the anchors are
  also leaders, and are drawn only as anchors);
- before the leader or the anchors are elected the shape is not placed yet, and the devices are gray;
- with the shape overlay, the shape lies in the background (gray, with its border), where the devices place it:
  where `shapes.yml` puts it for the position-based scenarios, on the leader or on the anchors for the others;
- the title tells the time, the number of devices and the population event just happened, if any.

Every folder of `data/positions` is read (the dynamic-population scenarios and the single runs, e.g. of
`positionBased.yml`). A run whose export is not finished (no closing lines: the simulation is still running or was
stopped) loses its last instant, which may be written only in part.

### Snapshots

```bash
python python/plot_positions_snapshots.py                     # one every 50 time units, first seed of each run
python python/plot_positions_snapshots.py 0 700 1500          # these instants
python python/plot_positions_snapshots.py --every 100 --until 1000
python python/plot_positions_snapshots.py --combined          # also all the instants in one figure, 6 per row
python python/plot_positions_snapshots.py --trail 5 --links 30
```

`--trail N` draws the last N exports of every device; `--links D` the links shorter than D (only an approximation of
the actual neighborhoods); `--cmap` changes the colormap (e.g. `cividis_r`, `RdYlBu`).

Charts: `charts/positions/<folder>/<run>/<fmt>/<run>_t<time>[_shape].<fmt>`, and `<run>_snapshots[_shape].<fmt>`
for the combined figure, where `_shape` marks the version with the shape in the background.

### GIFs

```bash
python python/plot_positions_gif.py --until 1000
python python/plot_positions_gif.py --from 600 --until 1200 --fps 15 --where shape=star
```

One frame per export (`--frame-step N` to keep one every N). GIFs:
`charts/positions/<folder>/<run>/gif/<run>[_shape].gif`.

### Fairness

```bash
python python/plot_positions_fairness.py                  # one every 20 time units up to t = 200
python python/plot_positions_fairness.py 0 50 100 --where shape=star
```

The shape split among the devices, as `FormationMetrics` measures it: each part is the piece of the shape nearer to
that device than to any other (the Voronoi cell, clipped to the shape), colored by its share over the fair share (the
area of the shape over the number of devices), on a log scale: gray-white when fair, red when larger (too few devices
around), blue when smaller (too many). The devices outside the shape that own no part of it are hollow. The title
tells Jain's fairness index of the shares, the `jain` of the metrics. By default only the first instants (up to
t = 200). `python python/test_fairness.py` checks the shares and the index.

Charts: `charts/positions/<folder>/<run>/<fmt>/<run>_fairness_t<time>.<fmt>`, and `<run>_fairness.<fmt>` with all the
instants in one figure, 6 per row.

## Data

### Metrics: `data/<scenario>/<root>_<variables>.csv`

The Alchemist CSV export: a header with the values of the variables (e.g. `# seed = 0.0, shape = star`), a line
naming the columns, then one row per export (every 10 time units). The times carry a small offset that differs
between runs, so they are snapped to the export grid (`EXPORT_INTERVAL` in `config.py`).

### Positions: `data/positions/<folder>/`

Written by `DevicePositionsExporter` (see its KDoc), configured in the `export` block of the yaml:

```yaml
- type: it.unibo.alchemist.boundary.exporters.DevicePositionsExporter
  parameters: { fileNameRoot: "dynamicPositionBased", interval: 5.0, exportPath: "data/positions/dynamicPositionBased", placement: global }
  data:
    - time
```

`placement` is where the shape lies, as for `FormationMetrics`: `global`, `leader` or `anchors`.
`interval` sets how often the devices are exported, and thus the frames of the GIFs; the plotters read it from the
data, so no change is needed here when it changes. Files, named like the metrics:

- `<root>_<variables>.csv`: one row per device and export: `time id x y sdf leader anchor` (`anchor`: 0 for none,
  else 1, 2, 3; `sdf`: NaN until the shape is placed);
- `<root>_<variables>_placement.csv`: one row per export: `time shape ox oy ax ay bx by`, the point (u, v) of the
  shape of `shapes.yml` lies at (ox, oy) + u (ax, ay) + v (bx, by);
- `shapes/<shape>.csv`: the SDF of the shape on a grid, in the coordinates of `shapes.yml`, written once.

## Cache

The parsed data is cached in `python/.cache/` (`parsed.pkl` for the metrics, `positions/` for the runs). A file is
parsed again only when it changes (modification time or size); `--no-cache` parses everything again. The cache can
be deleted at any time.

## Configuration: `config.py`

| Setting | Meaning |
|---|---|
| `SCENARIOS` | the data folders of the metrics and their labels, in the order (and colors) of the lines |
| `SEED_VARS` | the variables averaged over (the seeds) |
| `FACET_VARIABLE` | the variable with a chart (or panel) per value: the shape |
| `VARIABLE_DEFAULTS` | the value of a variable for older files without it (e.g. `start = left`) |
| `VALUE_LABELS` | how the values of a splitting variable read in the legend |
| `METRICS` | the label (and log scale) of every metric |
| `SHAPES_FILE`, `GRID_COLUMNS` | the shapes and their order; panels per row |
| `DEFAULT_POPULATION` | the population events when the yaml cannot be read |
| `ERRORBAR` | the band around the mean: `"sd"`, `("ci", 95)`, `"se"`, ... |
| `EXPORT_INTERVAL` | the export interval of the metrics |

A new scenario of the metrics needs a line in `SCENARIOS`; a new folder of `data/positions` is found by itself.

## Files

| File | Role |
|---|---|
| `config.py` | paths, scenarios, metrics, labels |
| `loader.py` | parsing (and cache) of the metrics, population events from the yaml |
| `plot_dynamic.py` | metrics over time |
| `positions.py` | parsing (and cache) of the positions, selection of the runs |
| `render.py` | drawing of one frame: devices, leader, anchors, shape, colorbar, legend |
| `plot_positions_snapshots.py` | snapshots |
| `plot_positions_gif.py` | GIFs |
| `plot_positions_fairness.py` | shape split among the devices, by share |
| `test_fairness.py` | check of the shares and of Jain's index |
