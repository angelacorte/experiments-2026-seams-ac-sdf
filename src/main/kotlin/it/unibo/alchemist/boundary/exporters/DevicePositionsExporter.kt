package it.unibo.alchemist.boundary.exporters

import it.unibo.alchemist.boundary.extractors.requireKnownPlacement
import it.unibo.alchemist.boundary.extractors.shapeIn
import it.unibo.alchemist.boundary.extractors.shapeNameIn
import it.unibo.alchemist.model.Actionable
import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.Position
import it.unibo.alchemist.model.Time
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.alchemist.model.times.DoubleTime
import it.unibo.collektive.catalog.TargetShape
import it.unibo.collektive.geometry.Position as Coordinates
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Exports, every [interval], the true position of every device with the SDF of the shape there (negative inside), and
 * whether it is the leader or an anchor, so that the formation can be drawn outside of the Alchemist GUI (see
 * `python`).
 *
 * The shape lies where the devices place it, by [placement], as in
 * [it.unibo.alchemist.boundary.extractors.FormationMetrics]: `global` (where `shapes.yml` puts it), `leader` (on the
 * leader) or `anchors` (on the centroid of the anchors, in their frame).
 *
 * Like the CSVExporter, every run writes in [exportPath] the files named by [fileNameRoot] and the values of the
 * variables (e.g. `dynamicPositionBased_seed-0.0_shape-star.csv`), with the variables in the header:
 * - `<name>.csv`: one row per device and export, `time id x y sdf leader anchor` (anchor: 0 for none, else 1, 2, 3;
 *   sdf: NaN until the leader or the anchors are elected);
 * - `<name>_placement.csv`: one row per export, `time shape ox oy ax ay bx by`: the point (u, v) of the shape of
 *   `shapes.yml` lies in the environment at (ox, oy) + u (ax, ay) + v (bx, by) (absent until the shape is placed);
 * - `shapes/<shape>.csv`, once per shape: its SDF on a grid in the coordinates of `shapes.yml`, to draw it.
 */
class DevicePositionsExporter<T, P : Position<P>>(
    private val fileNameRoot: String,
    val interval: Double,
    val exportPath: String,
    private val placement: String,
) : AbstractExporter<T, P>(interval) {

    init {
        requireKnownPlacement(placement)
    }

    private lateinit var devices: PrintStream
    private lateinit var placements: PrintStream
    private val rastered = mutableSetOf<String>()

    override fun setup(environment: Environment<T, P>) {
        val folder = File(exportPath).apply { mkdirs() }
        val prefix = listOf(fileNameRoot, variablesDescriptor).filter(String::isNotBlank).joinToString("_")
        require(prefix.isNotEmpty()) { "No fileNameRoot and no variables: the file name would be empty" }
        devices = open(File(folder, "$prefix.csv"), "time id x y sdf leader anchor")
        placements = open(File(folder, "${prefix}_placement.csv"), "time shape ox oy ax ay bx by")
        exportData(environment, null, DoubleTime(), 0)
    }

    private fun open(file: File, columns: String): PrintStream =
        PrintStream(file.outputStream().buffered(), false, Charsets.UTF_8.name()).apply {
            println(SEPARATOR)
            println("# Alchemist log file - simulation started at: ${now()} #")
            println(SEPARATOR)
            println("#")
            println("# $verboseVariablesDescriptor")
            println("#")
            println("# The columns have the following meaning: ")
            println("# $columns")
        }

    override fun exportData(environment: Environment<T, P>, reaction: Actionable<T>?, time: Time, step: Long) {
        val now = time.toDouble()
        val placed = shapeIn(environment, placement)
        placed?.let { (shape, where) ->
            val name = shapeNameIn(environment) ?: "shape"
            if (rastered.add(name)) writeRaster(name, shape)
            val o = where.toEnvironment(Coordinates(0.0, 0.0))
            val a = where.toEnvironment(Coordinates(1.0, 0.0))
            val b = where.toEnvironment(Coordinates(0.0, 1.0))
            placements.println(
                listOf(now, o.x, o.y, a.x - o.x, a.y - o.y, b.x - o.x, b.y - o.y)
                    .map { format(it) }
                    .let { listOf(it.first(), name) + it.drop(1) }
                    .joinToString(" "),
            )
        }
        environment.nodes.forEach { node ->
            val coordinates = environment.getPosition(node).coordinates
            val position = Coordinates(coordinates[0], coordinates[1])
            val sdf = placed?.second?.sdf?.invoke(position) ?: Double.NaN
            devices.println(
                listOf(
                    format(now),
                    node.id.toString(),
                    format(position.x),
                    format(position.y),
                    format(sdf),
                    if (node.isLeader()) "1" else "0",
                    node.anchor().toString(),
                ).joinToString(" "),
            )
        }
    }

    private fun Node<T>.isLeader() = LEADER in this && getConcentration(LEADER) == true

    private fun Node<T>.anchor(): Int = ((if (ANCHOR in this) getConcentration(ANCHOR) else null) as? String)
        ?.removePrefix("ANCHOR_")
        ?.toIntOrNull()
        ?: 0

    /**
     * The SDF of [shape] (in the coordinates of `shapes.yml`) on a grid of side [RASTER_STEP] around it, written once
     * in `shapes/<name>.csv` (atomically, as parallel runs may write it at once).
     */
    private fun writeRaster(name: String, shape: TargetShape) {
        val folder = File(exportPath, "shapes").apply { mkdirs() }
        val target = File(folder, "$name.csv")
        if (target.exists()) return
        // The extent of the shape, on a coarse grid, plus a margin
        val coarse = (-SEARCH..SEARCH).flatMap { i ->
            (-SEARCH..SEARCH).map { j -> Coordinates(shape.center.x + i, shape.center.y + j) }
        }.filter { shape.sdf(it) <= 0.0 }
        if (coarse.isEmpty()) return
        val xMin = coarse.minOf { it.x } - MARGIN
        val yMin = coarse.minOf { it.y } - MARGIN
        val columns = ((coarse.maxOf { it.x } + MARGIN - xMin) / RASTER_STEP).toInt() + 1
        val rows = ((coarse.maxOf { it.y } + MARGIN - yMin) / RASTER_STEP).toInt() + 1
        val temporary = File.createTempFile("$name-raster", ".tmp", folder)
        PrintStream(temporary.outputStream().buffered(), false, Charsets.UTF_8.name()).use { out ->
            out.println("# shape = $name")
            out.println("# xmin = $xMin, ymin = $yMin, step = $RASTER_STEP, columns = $columns, rows = $rows")
            out.println("# The SDF of the shape, in the coordinates of shapes.yml: one line per y, from ymin upwards")
            for (row in 0 until rows) {
                out.println(
                    (0 until columns).joinToString(" ") { column ->
                        format(shape.sdf(Coordinates(xMin + column * RASTER_STEP, yMin + row * RASTER_STEP)))
                    },
                )
            }
        }
        runCatching { Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE) }
            .onFailure { temporary.delete() } // Another run wrote it first: same content
    }

    override fun close(environment: Environment<T, P>, time: Time, step: Long) {
        listOf(devices, placements).forEach {
            it.println(SEPARATOR)
            it.println("# End of data export. Simulation finished at: ${now()} #")
            it.println(SEPARATOR)
            it.close()
        }
    }

    private companion object {
        private const val SEPARATOR = "#####################################################################"
        private const val RASTER_STEP = 0.5
        private const val SEARCH = 120 // Half side of the square where the extent of a shape is looked for
        private const val MARGIN = 10.0
        private val LEADER = SimpleMolecule("leader")
        private val ANCHOR = SimpleMolecule("anchor")
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mmZ", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        private fun now(): String = dateFormat.format(Date())

        private fun format(value: Double): String =
            if (value.isFinite()) String.format(Locale.US, "%.3f", value) else "NaN"
    }
}
