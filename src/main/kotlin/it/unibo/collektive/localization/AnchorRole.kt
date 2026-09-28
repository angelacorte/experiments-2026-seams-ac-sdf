package it.unibo.collektive.localization

/** The part a device plays in fixing the frame. */
enum class AnchorRole {
    /** The elected leader, at the origin of the frame. */
    ANCHOR_1,

    /** The neighbor of anchor 1 on the positive x-axis. */
    ANCHOR_2,

    /** The neighbor of anchors 1 and 2 on the positive y side. */
    ANCHOR_3,

    /** Not an anchor. */
    NONE,
}
