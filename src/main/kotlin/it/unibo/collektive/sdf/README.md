# `it.unibo.collektive.sdf` — a small DSL for 2D Signed Distance Fields

This package describes **target shapes** for aggregate shape formation as 2D
[Signed Distance Fields](https://iquilezles.org/articles/distfunctions2d/) (SDFs).
A shape is not a list of points or a mesh: it is a function that, for any point of the plane,
tells how far that point is from the shape's border, and on which side it lies.

Each device only needs to evaluate this function at its own position (plus a few nearby samples for the gradient),
so the shape can be arbitrarily complex while the per-device computation stays local, cheap,
and independent of the number of devices.

```kotlin
val shape: SDF = (Circle(Position(0.0, 0.0), 30.0) - Circle(Position(12.0, 0.0), 30.0))  // a crescent
    .rotate(PI / 4)
    .translate(50.0, 50.0)

shape(Position(10.0, 20.0))          // signed distance: < 0 inside, 0 on the border, > 0 outside
shape.isInside(Position(10.0, 20.0)) // true iff shape(position) <= 0
```

---

## Contents

- [Conventions](#conventions)
- [Package layout](#package-layout)
- [The core: `SDF`](#the-core-sdf)
- [Operators](#operators)
  - [Boolean operators](#boolean-operators)
  - [Offsetting: `expand`, `ring`, `outline`](#offsetting-expand-ring-outline)
  - [Rigid transformations and scaling](#rigid-transformations-and-scaling)
  - [Gradient](#gradient)
- [Primitives](#primitives)
- [Shapes](#shapes)
- [Text](#text)
- [Writing a new shape](#writing-a-new-shape)
- [Exact distances vs. bounds](#exact-distances-vs-bounds)
- [Using an SDF in an aggregate program](#using-an-sdf-in-an-aggregate-program)

---

## Conventions

| What                | Convention                                                                   |
|---------------------|------------------------------------------------------------------------------|
| Coordinates         | `Position(x, y)`, **y grows upwards** (mathematical orientation)             |
| Sign                | **negative inside**, zero on the border, positive outside                    |
| Angles              | **radians**, counterclockwise, measured from the +x axis (unless documented otherwise) |
| "Opening towards +y" | shapes with an aperture (`Wedge`, `CircularSector`, `Horseshoe`) are symmetric around the +y axis, and their aperture is a **half** angle measured from +y |
| Validation          | every constructor checks its parameters with `require` and fails fast with a descriptive message |

Curves with no interior (a `Segment`, an `Arc`, a `Spiral`...) are **unsigned**: they return the plain distance,
which is never negative. They become "fat" shapes with an inside once thickened with [`expand`](#offsetting-expand-ring-outline).

## Package layout

```
sdf/
├── SDF.kt        the SDF interface, the operators, and gradientToSDF
├── primitive/    fields computed from a closed formula (circle, segment, polygon, ...)
├── shape/        higher-level shapes, mostly compositions of primitives
└── text/         letters and multi-line text rendered as SDFs
```

## The core: `SDF`

```kotlin
fun interface SDF {
    operator fun invoke(position: Position): Double
    fun isInside(position: Position): Boolean   // this(position) <= 0.0
    fun isOutside(position: Position): Boolean  // this(position) > 0.0
}
```

`SDF` is a Kotlin `fun interface`, so any lambda from `Position` to `Double` is an SDF:

```kotlin
val horizontalBand = SDF { position -> abs(position.y) - 5.0 }   // the strip |y| <= 5
```

Every operator below takes SDFs and returns a new SDF: fields are **immutable values** that compose lazily
(nothing is computed until the field is evaluated at a point).

## Operators

All operators are extension functions defined in `SDF.kt` (package `it.unibo.collektive.sdf`), and most of them are
`infix` or `operator`, so a shape reads like an expression.

### Boolean operators

| Syntax            | Name         | Formula          | Meaning                        |
|-------------------|--------------|------------------|--------------------------------|
| `a or b`          | union        | `min(A, B)`      | inside `a` **or** inside `b`   |
| `a and b`         | intersection | `max(A, B)`      | inside `a` **and** inside `b`  |
| `!a`              | complement   | `-A`             | outside `a`                    |
| `a - b`           | difference   | `max(A, -B)`     | inside `a` but outside `b`     |
| `shapes.union()`  | n-ary union  | `min(A₁, …, Aₙ)` | union of an `Iterable<SDF>` (must not be empty) |

```kotlin
val keyhole = Circle(Position(0.0, 10.0), 8.0) or Triangle(Position(-6.0, -15.0), Position(6.0, -15.0), Position(0.0, 10.0))
val ring = Circle(center, 20.0) - Circle(center, 15.0)
val halfDisk = Circle(center, 20.0) and HalfPlane(center, Position(center.x + 1.0, center.y))
val dots = List(5) { Circle(Position(it * 10.0, 0.0), 2.0) }.union()
```

### Offsetting: `expand`, `ring`, `outline`

| Syntax              | Formula            | Meaning                                                            |
|---------------------|--------------------|--------------------------------------------------------------------|
| `a expand d`        | `A - d`            | grows `a` outward by `d`, rounding its corners (a negative `d` shrinks it) |
| `a ring t`          | `\|A\| - t`        | a band of **half**-width `t` centred on the border of `a` (`t >= 0`) |
| `a.outline()`       | `\|A\|`            | the border of `a` as a zero-width stroke, i.e. `a ring 0.0`        |

`expand` is how zero-width curves get a body — this is what every `thickness` parameter in the package does:

```kotlin
val stroke = Segment(Position(0.0, 0.0), Position(40.0, 0.0)) expand 3.0   // a 6-wide capsule
val roundedSquare = Square(center, 20.0) expand 4.0                          // square with rounded corners
val hollowStar = Star(center, 30.0, 5) ring 1.5                              // a star-shaped band
```

### Rigid transformations and scaling

| Syntax                            | Meaning                                                             |
|-----------------------------------|---------------------------------------------------------------------|
| `a.translate(dx, dy)`             | moves `a` by `(dx, dy)`                                             |
| `a.rotate(angle, pivot = origin)` | rotates `a` counterclockwise by `angle` radians around `pivot`      |
| `a.scale(factor, pivot = origin)` | scales `a` uniformly by `factor > 0` around `pivot`                 |

They work by transforming the *query point* backwards (e.g. `translate` samples the original shape at
`position - (dx, dy)`), and `scale` also rescales the returned distance, so all three **preserve exact distances**.
Order matters as usual with transformations:

```kotlin
shape.rotate(PI / 2).translate(50.0, 0.0)   // rotate around the origin, then move
shape.translate(50.0, 0.0).rotate(PI / 2)   // move, then rotate around the origin (the shape orbits it)
shape.rotate(PI / 2, pivot = center)        // rotate in place
```

Since fields are cheap to rebuild, time-varying shapes are just SDFs recomputed each round:

```kotlin
val breathing = shape.scale(1.0 + 0.2 * sin(2 * PI * time / 500.0), pivot = center)
```

### Gradient

```kotlin
fun gradientToSDF(sdf: SDF, currentPosition: Position, epsilon: Double): SpeedControl2D
```

Estimates the gradient with central differences of step `epsilon` (4 evaluations). The result is **not** divided by
the step, so only its **direction** is meaningful: it points away from the shape (the outward normal on the border),
and its opposite points towards the closest part of the shape. Normalize it before using it as a direction.

## Primitives

Package `it.unibo.collektive.sdf.primitive`. Each one is a class implementing `SDF` with a closed-form distance.

| Primitive                                                              | Signed? | Notes |
|------------------------------------------------------------------------|---------|-------|
| `Circle(center, radius)`                                               | yes     | a disk |
| `Rectangle(center, width, height)`                                     | yes     | axis-aligned; Quilez's `sdBox` |
| `RoundedRectangle(center, width, height, radius)`                      | yes     | same radius on every corner |
| `RoundedRectangle(center, width, height, topLeft, topRight, bottomRight, bottomLeft)` | yes | one radius per corner, each ≤ half the shorter side; Quilez's `sdRoundedBox` |
| `RoundedRectangle(center, width, height, Corners(topLeft, topRight, bottomRight, bottomLeft))` | yes | the same, with the radii grouped in a `Corners` (every radius defaults to 0; `Corners.all(r)` rounds them all) |
| `Polygon(vertices)`                                                    | yes     | any simple polygon (convex or not, any winding), ≥ 3 vertices; Quilez's `sdPolygon` |
| `HalfPlane(from, to)`                                                  | yes     | everything on the **left** of the directed line `from → to`; intersect with it to cut a shape along a line |
| `Segment(start, end)`                                                  | no      | a zero-width segment |
| `Arc(center, radius, startAngle, aperture)`                            | no      | a zero-width arc from `startAngle`, sweeping `aperture ∈ (0, 2π]` counterclockwise |

## Shapes

Package `it.unibo.collektive.sdf.shape`. Most shapes are **pure compositions** of primitives and operators,
written with Kotlin class delegation (`SDF by ...`), so they double as examples of the DSL.

| Shape | Built as | Parameters |
|-------|----------|------------|
| `Square(center, side)` | `Rectangle` | |
| `RegularPolygon(center, radius, sides, rotation = 0.0)` | `Polygon` | `radius` is centre → vertex; `rotation` is the angle of the first vertex |
| `Hexagon(center, radius)` | `RegularPolygon` with 6 sides | |
| `Triangle(a, b, c)` | three `Segment`s + sign from the edge sides | vertices in any order, not collinear |
| `Star(center, radius, pointCount, spikiness = pointCount / 2.0)` | folding into one slice, as Quilez's `sdStar` | `spikiness ∈ [2, pointCount]`: 2 is a regular polygon, higher is sharper |
| `Wedge(tip, halfAperture)` | `HalfPlane and HalfPlane` (or `or` beyond π/2) | infinite, opening towards +y, `halfAperture ∈ (0, π]` |
| `CircularSector(center, radius, halfAperture)` | `Circle and Wedge` | π/2 is a half disk, π a full disk |
| `CutDisk(center, radius, cutHeight)` | `Circle and HalfPlane` | keeps the part above `center.y + cutHeight` |
| `Crescent(center, radius, offset)` | `Circle - Circle` | the carved disk is shifted right by `offset ∈ (0, 2·radius)` |
| `Vesica(center, radius, offset)` | `Circle and Circle` | the lens between two disks shifted by `±offset`, `offset ∈ [0, radius)` |
| `Horseshoe(center, radius, aperture, armLength, thickness)` | `(Circle ring t) - CircularSector`, `or` two rotated `Rectangle` arms | opens towards +y |
| `RoundedX(center, width, radius)` | `(Segment or Segment) expand radius` | |
| `Stairs(origin, stepWidth, stepHeight, steps)` | `union()` of `Rectangle` columns | going up towards +x from the bottom-left `origin` |
| `QuestionMark(center, radius, thickness = 0.0)` | `(Arc or Segment or Circle) expand thickness` | |
| `Spiral(center, spacing, turns, innerRadius = 0.0, thickness = 0.0)` | chain of tangent semicircles | Archimedean-like, arms `spacing` apart, counterclockwise from +x |
| `FibonacciSpiral(center, scale, quarterTurns, thickness = 0.0)` | chain of tangent quarter circles | radii are the Fibonacci numbers × `scale` |

The two spirals share the internal helper `tangentArcChain(center, radii, sweep, thickness)`, which chains arcs so
that each one starts where the previous ends, tangent to it.

## Text

Package `it.unibo.collektive.sdf.text`: the 26 uppercase Latin letters, rendered as strokes.

### Public API

```kotlin
// A single letter (case-insensitive), with its bottom-left corner at start
'A'.toSdf(start = Position(0.0, 0.0), height = 40.0, thickness = 4.0)

// A multi-line text block: letters, spaces and '\n' (empty lines are kept)
val text: TextBlock = "HELLO\nWORLD".toSdf(
    start = Position(-20.0, 30.0),  // bottom-left corner of the first letter of the first line
    height = 40.0,                  // letter height
    thickness = 4.0,                // half-width of the strokes (applied with expand)
    spacing = 10.0,                 // empty space between letters     (default: height / 4)
    lineSpacing = 10.0,             // empty space between lines       (default: height / 4)
)

// The same, but with the bounding box centred on a point
val centered = TextBlock.centeredAt(Position(0.0, 0.0), "HELLO\nWORLD", glyphHeight = 40.0, thickness = 4.0)

text.width       // width of the longest line, strokes included
text.height      // total height, strokes and line spacing included
text.lineCount   // number of lines, empty ones included
TextBlock.supportedLetters
```

A `TextBlock` is an `SDF` like any other, so it can be transformed and combined:
`text.scale(0.5)`, `text.rotate(PI / 12)`, `text or Circle(...)`, `Rectangle(...) - text` (text carved out of a plate)...

Layout rules:

- lines grow **downwards** from `start`; letters left to right;
- each letter occupies a cell of width `glyphWidth × height + 2 × thickness`, followed by `spacing`;
  the standard glyph width is `0.5` (half the height), and **W** is wider (`0.75`) so its central peak stays readable;
- a space advances by one standard cell without drawing anything;
- anything that is not a letter, a space, or a newline is rejected when the block is built.

### How letters are drawn: the glyph DSL

Letters are defined in the `Alphabet.glyphs` map (`Alphabet.kt`), one `glyph { ... }` entry per letter, with an
internal, `@DslMarker`-annotated DSL (`GlyphScope`, in `Glyph.kt`). That map is the only list of letters:
`TextBlock.supportedLetters` and the cell widths come from it.
Inside a glyph, coordinates are **normalized**: the origin is the bottom-left corner, `y` goes from `0` to `1`
(the letter height), and `x` from `0` to `width` (the glyph width). Each glyph is drawn **once**, in these
coordinates; a `TextBlock` places the glyphs in normalized units too, and only at the end thickens the whole block
and scales it to the requested height (`scale` keeps the distances exact).

| DSL element                              | Result |
|------------------------------------------|--------|
| `x at y`                                 | a glyph-local `Position`, e.g. `left at top`, `center at W.PEAK` |
| `bottom`, `middle`, `top`                | the vertical guides: `0`, `0.5`, `1` |
| `left`, `center`, `right`, `beyondRight` | the horizontal guides: `0`, `width / 2`, `width`, and a coordinate safely past the right side (to carve openings) |
| `between(p, q, fraction)`                | the point at `fraction` of the way from `p` to `q`, e.g. where a crossbar meets a leg |
| `line(p, q)`                             | a zero-width `Segment` |
| `vertical(x, from = bottom, to = top)`   | a vertical zero-width line, e.g. `vertical(left)` (the stem), `vertical(center, to = middle)` |
| `horizontal(y, from = left, to = right)` | a horizontal zero-width line, e.g. `horizontal(top)`, `horizontal(middle, from = G.BAR_START)` |
| `polyline(p₁, p₂, …, pₙ)`                | connected segments through the points (≥ 2) |
| `arc(center, radius, startAngle, aperture)` | a zero-width `Arc` |
| `box(xRange, yRange, corners)`           | a `RoundedRectangle` from normalized bounds, e.g. `box(0.0..width, 0.3..0.7)` |
| `oval()`                                 | the outline of the stadium shared by C, G, O, Q |
| `openOval(from, to)`                     | the `oval()` with an opening on the right between the heights `from` and `to` (C, G) |
| `bowl(bottom, top)`                      | the outline of a D-shaped region, flat on the left and round on the right (D, P, R) |
| `width`, `halfWidth`, `radius`           | the glyph width, its half, and the standard rounding radius (`0.25`) |

Letters are ordinary SDF expressions over these elements:

```kotlin
'H' to glyph { stem or vertical(right) or horizontal(middle) },
'C' to glyph { openOval(middle - C.OPENING, middle + C.OPENING) },  // right side cut away
'W' to glyph(width = WIDE_GLYPH_WIDTH) {
    val inset = W.VALLEY_INSET * width
    polyline(left at top, left + inset at bottom, center at W.PEAK, right - inset at bottom, right at top)
},
```

Parts shared by several letters are private extension properties of `GlyphScope` in `Alphabet`: `stem` (the left
vertical stroke), `cup` (the bottom semicircle of J and U), `fShape` (F, extended by E with a bottom arm), and
`pShape` (P, extended by R with a leg).

Letters are written only in terms of these guides; the proportions specific to a letter (the height of the crossbar
of A, the peak of W, ...) are `const val`s grouped in a private object named after the letter, inside
`internal object Alphabet`, so there are no bare numbers in the glyph definitions.

Glyphs are built with zero-width strokes; the `thickness` is applied **once** to the whole block
(`union() expand thickness / height`, before scaling), so overlapping strokes merge smoothly.
`'A'.toSdf(...)` is just a one-letter `TextBlock`.

To add or tweak a letter, edit its entry in `Alphabet.glyphs` (and its object of proportions); if it needs a wider
cell, pass it to the builder, as in `glyph(width = WIDE_GLYPH_WIDTH) { ... }`, and `TextBlock` will lay it out
accordingly.

## Writing a new shape

There are three idioms in the package, from the simplest:

1. **A lambda**, for one-offs:

   ```kotlin
   val annulus = SDF { p -> abs(p.euclideanDistanceTo(center) - 20.0) - 3.0 }
   ```

2. **Delegation to a composition**, when the shape is made of existing pieces
   (validate parameters inside `run { ... }` so errors are reported before building):

   ```kotlin
   class Plus(center: Position, size: Double, thickness: Double) : SDF by (
       run {
           require(size > 0.0) { "Plus size must be positive, got $size" }
           val half = size / 2
           (Segment(Position(center.x - half, center.y), Position(center.x + half, center.y)) or
               Segment(Position(center.x, center.y - half), Position(center.x, center.y + half))) expand thickness
       }
   )
   ```

3. **A class overriding `invoke`**, when there is a closed formula (faster and exact), as in `Circle`, `Star`,
   or `Polygon`. Precompute everything that does not depend on the query point in properties.

## Exact distances vs. bounds

An SDF is *exact* when `|f(p)|` is the true Euclidean distance to the border; many operators only guarantee a
*bound* — the sign is always correct and the zero level set is always the right border, but `|f(p)|` may
**underestimate** the true distance in some regions.

| Operation                                   | Result |
|---------------------------------------------|--------|
| primitives, `translate`, `rotate`, `scale`, `!`, `ring`, `outline` | exact (given an exact input) |
| `expand` with `d >= 0`                      | exact outside, bound inside |
| `or` / `union()`                            | exact outside, bound inside |
| `and`, `-`                                  | bound (typically underestimates near the removed parts) |

For shape formation this is usually harmless: the sign (inside/outside) and the gradient direction near the border
are what drive the devices, and both are correct. Just avoid relying on the *magnitude* of the distance far inside
a composed shape.

## Using an SDF in an aggregate program

An SDF is a plain, stateless value, so it can be a top-level constant, a parameter, or recomputed every round.
Each device evaluates it only at its own (absolute or estimated) position:

```kotlin
private val shape = "HELLO\nWORLD".toSdf(start = Position(-20.0, 30.0), height = 40.0, thickness = 4.2, spacing = 10.5)

fun Aggregate<Int>.entrypoint(device: CollektiveDevice<*>, locationSensor: LocationSensor) = with(device) {
    val position = locationSensor.coordinates()
    val offsets = neighboring(position).neighbors.values.list.map { it - position }
    applyVelocity(latticeVelocity(shape, position, offsets))
}
```

In `it.unibo.collektive.formation`, `LocalBorder.of(shape, position, gradientStep)` turns the field into what a
device needs locally: the signed distance `shape(position)`, the unit outward normal (the normalized
`gradientToSDF`), whether the device is inside, and the direction towards the shape.
