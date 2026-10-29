package crazytower.game

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * A tiny synthesizer for the game's sounds: tones and noise with an envelope, mixed into
 * mono 16-bit samples. Every sound is made in code, so there are no sound files to license.
 */
class Synth(val seconds: Double, val rate: Int = RATE, private val loudness: Double = 0.12) {
    val samples = FloatArray((seconds * rate).toInt())
    private val random = Random(7)

    enum class Wave { SINE, SQUARE, TRIANGLE, SAW }

    /**
     * A tone from [at] seconds on, sliding from [freq] to [to] Hz. It rises in [attack]
     * seconds and then dies away; [vibrato] wobbles the pitch by that many Hz, five times a second.
     */
    fun tone(
        at: Double, duration: Double, freq: Double, to: Double = freq, wave: Wave = Wave.SINE,
        volume: Double = 0.4, attack: Double = 0.005, vibrato: Double = 0.0, sustain: Boolean = false,
    ) {
        var phase = 0.0
        forEach(at, duration) { i, t ->
            val f = freq + (to - freq) * (t / duration) + vibrato * sin(2 * PI * 5 * t)
            phase += f / rate
            val p = phase % 1.0
            val v = when (wave) {
                Wave.SINE -> sin(2 * PI * p)
                Wave.SQUARE -> if (p < 0.5) 0.6 else -0.6
                Wave.TRIANGLE -> 4 * abs(p - 0.5) - 1
                Wave.SAW -> (2 * p - 1) * 0.7
            }
            samples[i] += (v * volume * envelope(t, duration, attack, sustain)).toFloat()
        }
    }

    /** Noise; [smooth] near 1 makes it dull (a thud), 0 makes it hiss. */
    fun noise(at: Double, duration: Double, volume: Double = 0.4, smooth: Double = 0.0, attack: Double = 0.002, sustain: Boolean = false) {
        var last = 0.0
        forEach(at, duration) { i, t ->
            last = last * smooth + (random.nextDouble() * 2 - 1) * (1 - smooth)
            val gain = if (smooth > 0) 1 / (1 - smooth).coerceAtLeast(0.05) * 0.5 else 1.0
            samples[i] += (last * gain * volume * envelope(t, duration, attack, sustain)).toFloat()
        }
    }

    private inline fun forEach(at: Double, duration: Double, body: (Int, Double) -> Unit) {
        val start = (at * rate).toInt()
        val end = minOf(samples.size, ((at + duration) * rate).toInt())
        for (i in start until end) body(i, (i - start).toDouble() / rate)
    }

    private fun envelope(t: Double, duration: Double, attack: Double, sustain: Boolean): Double {
        val rise = if (t < attack) t / attack else 1.0
        val fall = if (sustain) 1.0 else exp(-(t - attack).coerceAtLeast(0.0) * 4.5 / duration)
        val tail = ((duration - t) / 0.01).coerceIn(0.0, 1.0)
        return rise * fall * tail
    }

    /** 16-bit little-endian PCM, brought to the same [loudness] (RMS) as the other sounds, peaks kept below 0.9. */
    fun pcm(): ByteArray {
        val rms = kotlin.math.sqrt(samples.sumOf { it.toDouble() * it } / samples.size.coerceAtLeast(1))
        val peak = samples.maxOfOrNull { abs(it) }?.toDouble() ?: 0.0
        val gain = if (rms > 0) minOf(loudness / rms, 0.9 / peak) else 1.0
        val out = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val s = samples[i] * gain
            val v = (s * 32000).toInt()
            out[i * 2] = (v and 0xFF).toByte()
            out[i * 2 + 1] = (v shr 8).toByte()
        }
        return out
    }

    companion object {
        const val RATE = 22050

        fun make(seconds: Double, loudness: Double = 0.12, build: Synth.() -> Unit): Synth = Synth(seconds, loudness = loudness).apply(build)
    }
}
