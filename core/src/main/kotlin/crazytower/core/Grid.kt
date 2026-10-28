package crazytower.core

/** Width of one building segment in native pixels. Room art is drawn on this grid. */
const val SEGMENT_PX = 24

/** Height of one floor in native pixels. */
const val FLOOR_PX = 48

/** Floor index of the street level, where the lobby lives. */
const val GROUND_FLOOR = 0

/** A point inside the tower: a floor and a horizontal position measured in segments. */
data class Loc(val floor: Int, val x: Float)
