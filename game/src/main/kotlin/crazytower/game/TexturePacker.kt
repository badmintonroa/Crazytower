package crazytower.game

import korlibs.image.bitmap.Bitmap32
import korlibs.image.bitmap.BmpSlice
import korlibs.image.bitmap.slice
import korlibs.math.geom.RectangleInt
import kotlin.math.max

/**
 * Puts many pictures into as few big textures as possible.
 *
 * KorGE draws everything that uses the same few textures (four at a time) in one draw call. Every
 * further texture in between ends the call and starts a new one, so the tower used to cost a draw
 * call for almost every person and room on screen: each person sheet was its own texture, and the
 * room art was spread over six. Packed onto one page, rooms, people, cabins and badges go to the
 * graphics card in a handful of calls.
 */
object TexturePacker {
    /** Largest page side. Every desktop graphics card of the last fifteen years takes 8192. */
    const val MAX_SIZE = 8192

    /** Empty pixels around every picture, so smoothing never blends in the neighbour. */
    const val PADDING = 2

    /** Packs [pictures] (key to bitmap) and returns a slice of a page for every key. */
    fun pack(pictures: Map<String, Bitmap32>): Map<String, BmpSlice> {
        val result = HashMap<String, BmpSlice>()
        // too big for any page: stays a texture of its own
        val (fitting, huge) = pictures.entries.partition { it.value.width + PADDING <= MAX_SIZE && it.value.height + PADDING <= MAX_SIZE }
        for ((key, bmp) in huge) result[key] = bmp.slice()
        var left = fitting.sortedWith(compareByDescending<Map.Entry<String, Bitmap32>> { it.value.height }.thenByDescending { it.value.width })
        while (left.isNotEmpty()) {
            val layout = bestLayout(left)
            val page = Bitmap32(layout.width, layout.height, premultiplied = true)
            for ((entry, spot) in layout.placed) {
                val bmp = entry.value
                page.put(bmp, spot.first, spot.second)
                result[entry.key] = page.slice(RectangleInt(spot.first, spot.second, bmp.width, bmp.height))
            }
            val done = layout.placed.map { it.first.key }.toHashSet()
            left = left.filter { it.key !in done }
        }
        return result
    }

    private class Layout(val width: Int, val height: Int, val placed: List<Pair<Map.Entry<String, Bitmap32>, Pair<Int, Int>>>)

    /**
     * Tries a range of page widths and keeps the one with the smallest page that holds
     * everything; when nothing holds everything, the widest page and the rest on the next one.
     */
    private fun bestLayout(items: List<Map.Entry<String, Bitmap32>>): Layout {
        val widest = items.maxOf { it.value.width } + PADDING
        var best: Layout? = null
        var width = widest
        while (true) {
            val layout = layout(items, width.coerceAtMost(MAX_SIZE))
            if (layout.placed.size == items.size && (best == null || layout.width.toLong() * layout.height < best.width.toLong() * best.height)) best = layout
            if (width >= MAX_SIZE) return best ?: layout
            width += 128
        }
    }

    /** Bottom-left skyline packing into a page [width] wide and at most [MAX_SIZE] high. */
    private fun layout(items: List<Map.Entry<String, Bitmap32>>, width: Int): Layout {
        // the skyline: segments of x, top and width, left to right
        val xs = ArrayList<Int>().apply { add(0) }
        val ys = ArrayList<Int>().apply { add(0) }
        val ws = ArrayList<Int>().apply { add(width) }
        val placed = ArrayList<Pair<Map.Entry<String, Bitmap32>, Pair<Int, Int>>>()
        var usedW = 0
        var usedH = 0
        for (item in items) {
            val w = item.value.width + PADDING
            val h = item.value.height + PADDING
            var bestY = Int.MAX_VALUE
            var bestI = -1
            for (i in xs.indices) {
                if (xs[i] + w > width) break
                var y = 0
                var j = i
                var covered = 0
                while (covered < w) {
                    y = max(y, ys[j]); covered += ws[j]; j++
                }
                if (y + h <= MAX_SIZE && y < bestY) {
                    bestY = y; bestI = i
                }
            }
            if (bestI < 0) continue
            val x = xs[bestI]
            placed += item to ((x + PADDING / 2) to (bestY + PADDING / 2))
            usedW = max(usedW, x + w)
            usedH = max(usedH, bestY + h)
            // the new segment replaces what it covers; a segment it covers in part is shortened
            var i = bestI
            var rest = w
            while (rest > 0) {
                if (ws[i] <= rest) {
                    rest -= ws[i]
                    xs.removeAt(i); ys.removeAt(i); ws.removeAt(i)
                } else {
                    xs[i] += rest; ws[i] -= rest
                    rest = 0
                }
            }
            xs.add(bestI, x); ys.add(bestI, bestY + h); ws.add(bestI, w)
        }
        return Layout(usedW.coerceAtLeast(1), usedH.coerceAtLeast(1), placed)
    }
}
