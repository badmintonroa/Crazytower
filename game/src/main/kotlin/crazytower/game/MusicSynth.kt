package crazytower.game

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The game's cozy background music, composed and played by this code: a day piece (lo-fi
 * electric piano, soft bass, brushes and a flute) and a night piece (pad, music box, slow piano).
 * There are no samples and no recordings, so the music belongs to the game like the rest of the
 * code and needs no licence (see docs/musik.md).
 *
 * Each piece is a seamless loop: notes and reverb that ring past the end come back in at the start.
 */
object MusicSynth {
    const val RATE = 44_100

    /** Interleaved stereo 16-bit samples, one loop. */
    class Track(val samples: ShortArray) {
        val frames: Int get() = samples.size / 2
    }

    // ---------------------------------------------------------------- the two pieces

    /** Day: 84 BPM in F major, 32 bars. Intro, theme, second theme, theme again. */
    fun day(): Track {
        val bpm = 84.0
        // one bar each; two chords in a bar take two beats each
        val progression = listOf(
            listOf(c(53, 0, 4, 7, 11)),                       // Fmaj7
            listOf(c(52, 0, 3, 7, 10), c(45, 0, 4, 7, 10)),   // Em7  A7
            listOf(c(50, 0, 3, 7, 10, 14)),                   // Dm9
            listOf(c(48, 0, 3, 7, 10), c(41, 0, 4, 7, 10)),   // Cm7  F7
            listOf(c(46, 0, 4, 7, 11)),                       // Bbmaj7
            listOf(c(46, 0, 3, 7, 10), c(51, 0, 4, 7, 10, 14)), // Bbm7 Eb9
            listOf(c(45, 0, 3, 7, 10), c(50, 0, 4, 7, 10)),   // Am7  D7
            listOf(c(43, 0, 3, 7, 10), c(48, 0, 4, 7, 10, 14)), // Gm7  C9
        )
        val song = Song(bars = 32, bpm = bpm, progression = progression, seed = 11)
        val themeA = song.melody(seed = 3, low = 65, high = 81)
        val themeB = song.melody(seed = 8, low = 67, high = 84, busy = 0.3)
        for (bar in 0 until 32) {
            val section = bar / 8
            song.epianoComp(bar, busy = section > 0)
            song.bass(bar, walking = section == 2)
            if (section > 0) song.drums(bar, light = section == 3 && bar >= 28)
            else if (bar >= 4) song.shaker(bar)
        }
        song.play(themeA, startBar = 8, instrument = song::flute, gain = 0.55)
        song.play(themeB, startBar = 16, instrument = song::flute, gain = 0.5)
        song.play(themeB, startBar = 16, instrument = song::bell, gain = 0.12, transpose = 12)
        song.play(themeA, startBar = 24, instrument = song::bell, gain = 0.4)
        return song.finish(reverb = 0.22)
    }

    /** Night: 66 BPM in A minor, 24 bars. Pad and piano, then a music box, then a slow tune. */
    fun night(): Track {
        val progression = listOf(
            listOf(c(45, 0, 3, 7, 10, 14)),                   // Am9
            listOf(c(41, 0, 4, 7, 11)),                       // Fmaj7
            listOf(c(48, 0, 4, 7, 11, 14)),                   // Cmaj9
            listOf(c(43, 0, 4, 7, 9)),                        // G6
            listOf(c(50, 0, 3, 7, 10, 14)),                   // Dm9
            listOf(c(45, 0, 3, 7, 10)),                       // Am7
            listOf(c(46, 0, 4, 7, 11)),                       // Bbmaj7
            listOf(c(40, 0, 5, 7, 10), c(40, 0, 4, 7, 10)),   // E7sus4 E7
        )
        val song = Song(bars = 24, bpm = 66.0, progression = progression, seed = 21)
        val tune = song.melody(seed = 5, low = 69, high = 84, busy = -0.35)
        for (bar in 0 until 24) {
            val pass = bar / 8
            song.pad(bar)
            song.softPiano(bar)
            song.bass(bar, walking = false, soft = true)
            if (pass >= 1) song.musicBoxArpeggio(bar, sparse = pass == 2)
        }
        song.play(tune, startBar = 16, instrument = song::bell, gain = 0.45)
        return song.finish(reverb = 0.35)
    }

    // ---------------------------------------------------------------- composing

    /** A chord: MIDI root and intervals above it. */
    class Chord(val root: Int, val intervals: List<Int>) {
        val pitchClasses = intervals.map { (root + it) % 12 }
        fun tones(low: Int, high: Int): List<Int> = (low..high).filter { it % 12 in pitchClasses }
    }

    private fun c(root: Int, vararg intervals: Int) = Chord(root, intervals.toList())

    class Note(val beat: Double, val length: Double, val pitch: Int, val velocity: Double = 1.0)

    private class Song(val bars: Int, val bpm: Double, val progression: List<List<Chord>>, seed: Int) {
        val beat = 60.0 / bpm
        val frames = (bars * 4 * beat * RATE).toInt()
        val left = FloatArray(frames)
        val right = FloatArray(frames)
        val random = Random(seed)

        fun chordAt(bar: Int, beatInBar: Double): Chord {
            val chords = progression[bar % progression.size]
            return if (chords.size == 1 || beatInBar < 2) chords[0] else chords[1]
        }

        /** Seconds of a beat position; off-beat eighths are pushed back a little (swing). */
        fun time(beats: Double): Double {
            val frac = beats - kotlin.math.floor(beats)
            val swing = if (abs(frac - 0.5) < 0.01) 0.07 else 0.0
            return (beats + swing) * beat
        }

        /** Adds [sound] at [seconds], wrapping round the end of the loop. */
        fun add(seconds: Double, sound: FloatArray, gain: Double, pan: Double = 0.0) {
            val start = ((seconds * RATE).toInt() % frames + frames) % frames
            val l = (gain * kotlin.math.cos((pan + 1) * PI / 4)).toFloat()
            val r = (gain * sin((pan + 1) * PI / 4)).toFloat()
            var i = start
            for (s in sound) {
                left[i] += s * l
                right[i] += s * r
                if (++i == frames) i = 0
            }
        }

        fun human(): Double = (random.nextDouble() - 0.5) * 0.016

        // ---- parts

        /** Close voicing between E3 and F4, so the chords sit warm under the tune. */
        fun voicing(chord: Chord, low: Int = 52, high: Int = 65): List<Int> =
            chord.pitchClasses.drop(1).ifEmpty { chord.pitchClasses }.map { pc -> (low..high).first { it % 12 == pc } }.sorted()

        fun epianoComp(bar: Int, busy: Boolean) {
            val chords = progression[bar % progression.size]
            val hits = if (chords.size == 2) listOf(0.0 to 1.6, 2.0 to 1.6) + (if (busy) listOf(1.5 to 0.4) else emptyList())
            else if (busy) listOf(0.0 to 1.4, 1.5 to 1.0, 3.5 to 0.5) else listOf(0.0 to 3.5)
            for ((at, length) in hits) {
                val chord = chordAt(bar, at)
                for ((k, p) in voicing(chord).withIndex()) {
                    val v = (if (at == 0.0 || at == 2.0) 0.8 else 0.55) * (0.9 + random.nextDouble() * 0.2)
                    // a tiny strum, lowest note first
                    add(time(bar * 4 + at) + k * 0.008 + human(), epiano(hz(p), length * beat, v), 0.26, pan = -0.25 + k * 0.15)
                }
            }
        }

        fun softPiano(bar: Int) {
            val chords = progression[bar % progression.size]
            for ((n, chord) in chords.withIndex()) {
                val at = if (chords.size == 2) n * 2.0 else 0.0
                val notes = voicing(chord, 55, 67)
                for ((k, p) in notes.withIndex()) {
                    // rolled slowly upwards, like someone playing quietly at night
                    add(time(bar * 4 + at) + k * 0.09 + human(), epiano(hz(p), (if (chords.size == 2) 1.8 else 3.6) * beat, 0.45), 0.2, pan = 0.3 - k * 0.2)
                }
            }
        }

        fun bass(bar: Int, walking: Boolean, soft: Boolean = false) {
            val chords = progression[bar % progression.size]
            fun low(chord: Chord, interval: Int = 0) = (36..48).first { it % 12 == (chord.root + interval) % 12 }
            val notes = ArrayList<Note>()
            if (soft) {
                for ((n, chord) in chords.withIndex()) notes += Note(n * 2.0, if (chords.size == 2) 1.9 else 3.8, low(chord), 0.8)
            } else if (chords.size == 2) {
                notes += Note(0.0, 1.4, low(chords[0])); notes += Note(1.5, 0.4, low(chords[0], 7), 0.6)
                notes += Note(2.0, 1.4, low(chords[1])); if (walking) notes += Note(3.5, 0.4, low(chords[1], 7), 0.6)
            } else {
                notes += Note(0.0, 1.8, low(chords[0]))
                notes += Note(2.0, if (walking) 0.9 else 1.4, low(chords[0], 7), 0.75)
                if (walking) notes += Note(3.0, 0.9, low(chords[0], if (random.nextBoolean()) 12 else 10), 0.7)
                else notes += Note(3.5, 0.45, low(chords[0], 12), 0.55)
            }
            for (n in notes) add(time(bar * 4 + n.beat) + human(), bassTone(hz(n.pitch), n.length * beat), 0.5 * n.velocity)
        }

        fun drums(bar: Int, light: Boolean) {
            val base = bar * 4.0
            add(time(base), kick(), 0.5)
            if (!light) add(time(base + 2.5), kick(), 0.35)
            for (b in listOf(1.0, 3.0)) add(time(base + b) + 0.01, brush(0.22, bright = 0.35), 0.26, pan = 0.15)
            shaker(bar)
        }

        fun shaker(bar: Int) {
            for (i in 0 until 8) {
                val v = if (i % 2 == 1) 0.09 else 0.05
                add(time(bar * 4 + i * 0.5) + human(), brush(0.07, bright = 0.85), v * (0.8 + random.nextDouble() * 0.4), pan = 0.35)
            }
        }

        fun pad(bar: Int) {
            val chord = progression[bar % progression.size][0]
            for ((k, p) in voicing(chord, 55, 67).withIndex()) {
                add(time(bar * 4.0), pad(hz(p), 4 * beat), 0.07, pan = if (k % 2 == 0) -0.5 else 0.5)
            }
        }

        fun musicBoxArpeggio(bar: Int, sparse: Boolean) {
            for (i in 0 until 8) {
                if (sparse && i % 2 == 1) continue
                val chord = chordAt(bar, i * 0.5)
                val tones = chord.tones(72, 88)
                val up = if ((bar / 2) % 2 == 0) i else 7 - i
                val p = tones[(up * 2 / 3 + bar) % tones.size]
                add(time(bar * 4 + i * 0.5) + human(), bell(hz(p), 0.5 * beat), 0.13 * (if (i == 0) 1.2 else 1.0), pan = -0.4 + i * 0.1)
            }
        }

        /** Plays a melody of eight bars from [startBar]. */
        fun play(melody: List<Note>, startBar: Int, instrument: (Double, Double, Double) -> FloatArray, gain: Double, transpose: Int = 0) {
            for (n in melody) add(time(startBar * 4 + n.beat) + human(), instrument(hz(n.pitch + transpose), n.length * beat, n.velocity), gain, pan = 0.1)
        }

        /**
         * A tune of eight bars made of chord tones only, so it always fits: an arch that rises
         * over the first four bars and settles over the last four, bars 5 and 6 echo the rhythm
         * of 1 and 2, and both halves end on a long note. [busy] above 0 means more notes.
         */
        fun melody(seed: Int, low: Int, high: Int, busy: Double = 0.0): List<Note> {
            val r = Random(seed)
            val rhythms = listOf(
                listOf(0.0 to 1.0, 1.5 to 0.5, 2.0 to 1.5),
                listOf(0.5 to 0.5, 1.0 to 1.0, 2.5 to 1.5),
                listOf(0.0 to 1.5, 2.0 to 0.5, 2.5 to 0.5, 3.0 to 1.0),
                listOf(0.0 to 0.5, 0.5 to 0.5, 1.0 to 2.0, 3.5 to 0.5),
                listOf(1.0 to 0.5, 1.5 to 0.5, 2.0 to 2.0),
                listOf(0.0 to 0.5, 0.5 to 0.5, 1.0 to 0.5, 1.5 to 0.5, 2.0 to 2.0),
            )
            val slow = listOf(listOf(0.0 to 2.0, 2.0 to 2.0), listOf(0.0 to 3.0, 3.0 to 1.0), listOf(1.0 to 3.0))
            val endings = listOf(listOf(0.0 to 3.5), listOf(0.0 to 1.0, 1.0 to 3.0), listOf(0.5 to 0.5, 1.0 to 3.0))
            fun pick(): List<Pair<Double, Double>> = when {
                busy < -0.1 && r.nextDouble() < 0.7 -> slow[r.nextInt(slow.size)]
                busy > 0.1 && r.nextDouble() < busy -> rhythms[3 + r.nextInt(3)]
                else -> rhythms[r.nextInt(rhythms.size)]
            }
            val bar1 = pick()
            val bar2 = pick()
            val pattern = listOf(bar1, bar2, pick(), endings[r.nextInt(endings.size)], bar1, bar2, pick(), listOf(0.0 to 4.0))
            val notes = ArrayList<Note>()
            var last = (low + high) / 2
            for ((bar, rhythm) in pattern.withIndex()) {
                val rising = bar < 3
                for ((k, onset) in rhythm.withIndex()) {
                    val (at, length) = onset
                    val tones = chordAt(bar, at).tones(low, high)
                    val step = when {
                        bar == 7 -> 0
                        rising -> listOf(-2, 1, 2, 3, 4)[r.nextInt(5)]
                        else -> listOf(-4, -3, -2, -1, 2)[r.nextInt(5)]
                    }
                    val target = last + step
                    var pitch = tones.minBy { abs(it - target) + if (it == last && step != 0) 2 else 0 }
                    // the very last note goes home: the root or the third of the chord
                    if (bar == 7 && k == rhythm.lastIndex) {
                        val chord = chordAt(bar, at)
                        pitch = tones.filter { it % 12 == chord.pitchClasses[0] || it % 12 == chord.pitchClasses[1] }.minByOrNull { abs(it - last) } ?: pitch
                    }
                    val accent = if (at % 1.0 == 0.0) 1.0 else 0.8
                    notes += Note(bar * 4 + at, length, pitch, accent * (0.85 + r.nextDouble() * 0.15))
                    last = pitch
                }
            }
            return notes
        }

        // ---- instruments; each returns the sound of one note including its fade-out

        /** Electric piano: two-operator FM whose brightness dies away, like a soft Rhodes. */
        fun epiano(freq: Double, hold: Double, velocity: Double): FloatArray {
            val release = 0.5
            val out = FloatArray(((hold + release) * RATE).toInt())
            var pc = 0.0; var pm = 0.0
            val dc = freq / RATE; val dm = freq / RATE
            for (i in out.indices) {
                val t = i.toDouble() / RATE
                // modulation depth in cycles; high at the strike, then the tone mellows
                val index = (0.25 + 1.6 * velocity * exp(-t * 7)) / (2 * PI)
                pm += dm; pc += dc
                val amp = attack(t, 0.004) * exp(-t * 1.1) * fadeAfter(t, hold, release)
                out[i] = (Wave.sine(pc + Wave.sine(pm) * index) * amp * velocity).toFloat()
            }
            return out
        }

        fun bassTone(freq: Double, hold: Double): FloatArray {
            val release = 0.12
            val out = FloatArray(((hold + release) * RATE).toInt())
            var p = 0.0
            for (i in out.indices) {
                val t = i.toDouble() / RATE
                p += freq / RATE
                val v = Wave.sine(p) + 0.3 * Wave.sine(2 * p) * exp(-t * 5) + 0.08 * Wave.sine(3 * p)
                out[i] = (v * attack(t, 0.01) * (0.55 + 0.45 * exp(-t * 2.5)) * fadeAfter(t, hold, release)).toFloat()
            }
            return out
        }

        /** A breathy flute: a sine with a soft overtone, slow vibrato and a little air. */
        fun flute(freq: Double, hold: Double, velocity: Double): FloatArray {
            val release = 0.25
            val out = FloatArray(((hold + release) * RATE).toInt())
            var p = 0.0
            var air = 0.0
            for (i in out.indices) {
                val t = i.toDouble() / RATE
                val vibrato = 1 + 0.004 * sin(2 * PI * 5.2 * t) * (t / 0.4).coerceAtMost(1.0)
                p += freq * vibrato / RATE
                air = air * 0.9 + (random.nextDouble() - 0.5) * 0.1
                val v = Wave.sine(p) + 0.12 * Wave.sine(2 * p) + air * 0.35
                out[i] = (v * attack(t, 0.06) * (0.8 + 0.2 * exp(-t * 2)) * fadeAfter(t, hold, release) * velocity).toFloat()
            }
            return out
        }

        /** Music box / celesta: a bright strike with an inharmonic overtone, ringing on. */
        fun bell(freq: Double, hold: Double, velocity: Double): FloatArray {
            val ring = maxOf(hold, 0.2) + 1.6
            val out = FloatArray((ring * RATE).toInt())
            var p = 0.0
            for (i in out.indices) {
                val t = i.toDouble() / RATE
                p += freq / RATE
                val v = Wave.sine(p) * exp(-t * 2.2) + 0.3 * Wave.sine(p * 4.0) * exp(-t * 9) + 0.12 * Wave.sine(p * 2.76) * exp(-t * 6)
                out[i] = (v * attack(t, 0.002) * fadeAfter(t, ring - 0.1, 0.1) * velocity).toFloat()
            }
            return out
        }

        fun bell(freq: Double, hold: Double): FloatArray = bell(freq, hold, 1.0)

        /** A slow, warm pad: three slightly detuned soft saws. */
        fun pad(freq: Double, hold: Double): FloatArray {
            val release = 1.5
            val out = FloatArray(((hold + release) * RATE).toInt())
            val detune = doubleArrayOf(1.0, 1.003, 0.997)
            val phase = DoubleArray(3) { random.nextDouble() }
            for (i in out.indices) {
                val t = i.toDouble() / RATE
                var v = 0.0
                for (k in 0 until 3) { phase[k] += freq * detune[k] / RATE; v += Wave.soft(phase[k]) }
                out[i] = (v / 3 * attack(t, 1.2) * fadeAfter(t, hold, release)).toFloat()
            }
            return lowpass(out, 1400.0)
        }

        fun kick(): FloatArray {
            val out = FloatArray((0.3 * RATE).toInt())
            var p = 0.0
            for (i in out.indices) {
                val t = i.toDouble() / RATE
                p += (48 + 70 * exp(-t * 35)) / RATE
                out[i] = (Wave.sine(p) * exp(-t * 11) * attack(t, 0.002)).toFloat()
            }
            return out
        }

        /** Noise, dull for a brush on the snare, bright for a shaker. */
        fun brush(length: Double, bright: Double): FloatArray {
            val out = FloatArray((length * RATE).toInt())
            var low = 0.0
            for (i in out.indices) {
                val t = i.toDouble() / RATE
                val n = random.nextDouble() * 2 - 1
                low = low * 0.85 + n * 0.15
                val v = n * bright + low * (1 - bright) * 2.5 - low * bright
                out[i] = (v * attack(t, 0.006) * exp(-t * 6 / length)).toFloat()
            }
            return out
        }

        // ---- mixing

        fun finish(reverb: Double): Track {
            // a gentle low-pass takes the edge off; then a hall that wraps round the loop
            val l = lowpass(left, 9000.0, wrap = true)
            val r = lowpass(right, 9000.0, wrap = true)
            val (wl, wr) = Reverb().process(l, r)
            for (i in l.indices) {
                l[i] += wl[i] * reverb.toFloat()
                r[i] += wr[i] * reverb.toFloat()
            }
            val rms = sqrt(l.indices.sumOf { (l[it] * l[it] + r[it] * r[it]).toDouble() } / (2 * l.size))
            val peak = l.indices.maxOf { maxOf(abs(l[it]), abs(r[it])) }.toDouble()
            val gain = minOf(TARGET_RMS / rms, 0.95 / peak)
            val out = ShortArray(l.size * 2)
            for (i in l.indices) {
                out[2 * i] = (l[i] * gain * 32767).toInt().coerceIn(-32768, 32767).toShort()
                out[2 * i + 1] = (r[i] * gain * 32767).toInt().coerceIn(-32768, 32767).toShort()
            }
            return Track(out)
        }
    }

    // ---------------------------------------------------------------- building blocks

    private const val TABLE = 4096
    private const val TARGET_RMS = 0.13

    private fun hz(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

    private fun attack(t: Double, seconds: Double) = if (t < seconds) t / seconds else 1.0

    /** 1 while the key is held, then a fade to 0 over [release] seconds. */
    private fun fadeAfter(t: Double, hold: Double, release: Double) =
        if (t <= hold) 1.0 else ((1 - (t - hold) / release).coerceAtLeast(0.0)).let { it * it }

    /** Wavetables, read with a phase counted in cycles. */
    private object Wave {
        private val sineTable = FloatArray(TABLE + 1) { sin(2 * PI * it / TABLE).toFloat() }
        // a saw with only its first eight overtones, rounded off: no harsh highs
        private val softTable = FloatArray(TABLE + 1) { i -> (1..8).sumOf { k -> sin(2 * PI * k * i / TABLE) / k.toDouble().pow(1.4) }.toFloat() * 0.6f }

        fun sine(phase: Double) = read(sineTable, phase)
        fun soft(phase: Double) = read(softTable, phase)

        private fun read(table: FloatArray, phase: Double): Double {
            val x = (phase - kotlin.math.floor(phase)) * TABLE
            val i = x.toInt()
            val f = x - i
            return table[i] + (table[i + 1] - table[i]) * f
        }
    }

    private fun lowpass(x: FloatArray, cutoff: Double, wrap: Boolean = false): FloatArray {
        val a = exp(-2 * PI * cutoff / RATE).toFloat()
        val y = FloatArray(x.size)
        var acc = 0f
        // run once to warm up on the end of the loop, so the start joins smoothly
        if (wrap) for (i in maxOf(0, x.size - 2000) until x.size) acc = (1 - a) * x[i] + a * acc
        for (i in x.indices) { acc = (1 - a) * x[i] + a * acc; y[i] = acc }
        return y
    }

    /** A small Freeverb-style hall. It runs over the loop twice, so its tail wraps round to the start. */
    private class Reverb {
        private val combs = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
        private val allpasses = intArrayOf(556, 441, 341, 225)

        fun process(l: FloatArray, r: FloatArray): Pair<FloatArray, FloatArray> = channel(l, 0) to channel(r, 23)

        private fun channel(x: FloatArray, spread: Int): FloatArray {
            val comb = combs.map { FloatArray(it + spread) }
            val combPos = IntArray(comb.size)
            val combLow = FloatArray(comb.size)
            val all = allpasses.map { FloatArray(it + spread) }
            val allPos = IntArray(all.size)
            val out = FloatArray(x.size)
            val feedback = 0.84f
            val damp = 0.25f
            for (pass in 0 until 2) for (i in x.indices) {
                val input = x[i] * 0.015f
                var sum = 0f
                for (k in comb.indices) {
                    val buf = comb[k]
                    val y = buf[combPos[k]]
                    combLow[k] = y * (1 - damp) + combLow[k] * damp
                    buf[combPos[k]] = input + combLow[k] * feedback
                    if (++combPos[k] == buf.size) combPos[k] = 0
                    sum += y
                }
                for (k in all.indices) {
                    val buf = all[k]
                    val b = buf[allPos[k]]
                    buf[allPos[k]] = sum + b * 0.5f
                    sum = b - sum
                    if (++allPos[k] == buf.size) allPos[k] = 0
                }
                if (pass == 1) out[i] = sum
            }
            return out
        }
    }
}
