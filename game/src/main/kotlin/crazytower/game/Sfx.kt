package crazytower.game

import crazytower.game.Synth.Wave.SAW
import crazytower.game.Synth.Wave.SINE
import crazytower.game.Synth.Wave.SQUARE
import crazytower.game.Synth.Wave.TRIANGLE
import java.util.concurrent.Executors
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.FloatControl
import kotlin.math.log10

/**
 * The game's sounds. Each one is synthesized on first use and played on a background
 * thread, at the volume from the options. Without a sound card the game stays silent.
 */
object Sfx {
    const val CLICK = "click"
    const val BUILD = "build"
    const val DEMOLISH = "demolish"
    const val ERROR = "error"
    const val CASH = "cash"
    const val STAR = "star"
    const val STAGE = "stage"
    const val DING = "ding"
    const val EMPTY = "empty"

    private val recipes: Map<String, () -> Synth> = mapOf(
        CLICK to { Synth.make(0.05, loudness = 0.05) { tone(0.0, 0.04, 1800.0, 1200.0, SQUARE, 0.12) } },
        BUILD to {
            Synth.make(0.35) {
                noise(0.0, 0.12, 0.5, smooth = 0.9)
                tone(0.0, 0.15, 180.0, 90.0, SINE, 0.6)
                tone(0.08, 0.1, 660.0, wave = TRIANGLE, volume = 0.25)
                tone(0.16, 0.18, 880.0, wave = TRIANGLE, volume = 0.25)
            }
        },
        DEMOLISH to {
            Synth.make(0.7) {
                noise(0.0, 0.65, 0.6, smooth = 0.7)
                tone(0.0, 0.45, 120.0, 40.0, SAW, 0.3)
                noise(0.15, 0.3, 0.3, smooth = 0.4)
            }
        },
        ERROR to {
            Synth.make(0.32) {
                tone(0.0, 0.12, 220.0, wave = SQUARE, volume = 0.2)
                tone(0.13, 0.18, 175.0, wave = SQUARE, volume = 0.2)
            }
        },
        CASH to {
            Synth.make(0.4, loudness = 0.07) {
                noise(0.0, 0.04, 0.2)
                tone(0.0, 0.08, 1318.0, wave = SQUARE, volume = 0.15)
                tone(0.07, 0.3, 1760.0, wave = SQUARE, volume = 0.15)
            }
        },
        STAR to {
            Synth.make(1.3) {
                listOf(523.0, 659.0, 784.0).forEachIndexed { i, f -> tone(i * 0.11, 0.25, f, wave = TRIANGLE, volume = 0.35) }
                tone(0.33, 0.9, 1047.0, wave = TRIANGLE, volume = 0.4)
                tone(0.33, 0.9, 2093.0, wave = SINE, volume = 0.12, vibrato = 6.0)
            }
        },
        STAGE to {
            Synth.make(2.2) {
                listOf(392.0, 523.0, 659.0, 784.0).forEachIndexed { i, f -> tone(i * 0.14, 0.2, f, wave = SQUARE, volume = 0.22) }
                for (f in listOf(523.0, 659.0, 784.0, 1047.0)) tone(0.6, 1.5, f, wave = SQUARE, volume = 0.14, attack = 0.02)
                noise(0.6, 1.2, 0.08)
            }
        },
        DING to { Synth.make(0.8) { tone(0.0, 0.75, 1318.0, volume = 0.35); tone(0.0, 0.75, 2636.0, volume = 0.08) } },
        EMPTY to {
            Synth.make(0.35) {
                noise(0.0, 0.08, 0.6, smooth = 0.92)
                noise(0.16, 0.08, 0.6, smooth = 0.92)
            }
        },
        // ---- one sound per tenant, played when the player clicks their room
        "family" to { Synth.make(1.2) { tone(0.0, 0.55, 659.0, volume = 0.45); tone(0.4, 0.75, 523.0, volume = 0.45) } },
        "students" to {
            Synth.make(0.8) {
                for (i in 0 until 3) tone(i * 0.25, 0.14, 110.0, 45.0, SINE, 0.8)
                for (i in 0 until 3) noise(i * 0.25 + 0.125, 0.04, 0.2)
                tone(0.0, 0.7, 220.0, wave = SAW, volume = 0.08, sustain = true)
            }
        },
        "retirees" to {
            Synth.make(1.0) {
                listOf(2000.0, 1500.0, 2000.0).forEachIndexed { i, f ->
                    noise(i * 0.33, 0.03, 0.3, smooth = 0.5); tone(i * 0.33, 0.04, f, wave = SQUARE, volume = 0.1)
                }
            }
        },
        "influencer" to {
            Synth.make(0.3) {
                tone(0.0, 0.02, 3000.0, wave = SQUARE, volume = 0.1)
                noise(0.0, 0.03, 0.6, smooth = 0.3)
                noise(0.08, 0.06, 0.5, smooth = 0.5)
            }
        },
        "penguins" to {
            Synth.make(0.45) {
                tone(0.0, 0.12, 900.0, 1400.0, SQUARE, 0.18)
                tone(0.17, 0.15, 1000.0, 1700.0, SQUARE, 0.18)
            }
        },
        "ghost" to { Synth.make(1.3) { tone(0.0, 1.25, 300.0, 460.0, SINE, 0.4, attack = 0.3, vibrato = 18.0) } },
        "accountant" to {
            Synth.make(0.7) {
                for (i in 0 until 6) tone(i * 0.06, 0.03, 2500.0, wave = SQUARE, volume = 0.1)
                tone(0.4, 0.3, 1568.0, volume = 0.35)
            }
        },
        "startup" to {
            Synth.make(0.4) {
                tone(0.0, 0.15, 440.0, 880.0, SAW, 0.2)
                tone(0.15, 0.2, 880.0, 1760.0, SAW, 0.2)
            }
        },
        "callcenter" to {
            Synth.make(1.0) {
                for (i in 0 until 2) {
                    tone(i * 0.5, 0.4, 440.0, volume = 0.25, sustain = true)
                    tone(i * 0.5, 0.4, 480.0, volume = 0.25, sustain = true)
                }
            }
        },
        "excuses" to {
            Synth.make(1.3) {
                tone(0.0, 0.3, 392.0, 370.0, SAW, 0.25, attack = 0.03)
                tone(0.3, 0.3, 370.0, 349.0, SAW, 0.25, attack = 0.03)
                tone(0.6, 0.65, 349.0, 330.0, SAW, 0.25, attack = 0.03, vibrato = 6.0)
            }
        },
        "socks" to { Synth.make(0.4) { noise(0.0, 0.15, 0.5, smooth = 0.95); noise(0.18, 0.15, 0.4, smooth = 0.95) } },
        "ministry" to {
            Synth.make(0.6) {
                for (i in 0 until 2) {
                    noise(i * 0.28, 0.1, 0.7, smooth = 0.9)
                    tone(i * 0.28, 0.15, 90.0, 60.0, SINE, 0.5)
                }
            }
        },
        "vampire" to {
            Synth.make(1.4) {
                for (f in listOf(220.0, 261.6, 329.6, 110.0)) tone(0.0, 1.35, f, wave = SQUARE, volume = 0.1, attack = 0.15, vibrato = 2.0)
            }
        },
        "timetravel" to { Synth.make(0.9) { tone(0.0, 0.85, 200.0, 2000.0, SINE, 0.35, attack = 0.6) } },
        "supervillain" to {
            Synth.make(1.2) {
                tone(0.0, 1.1, 110.0, wave = SAW, volume = 0.25, attack = 0.05)
                tone(0.0, 1.1, 116.5, wave = SAW, volume = 0.25, attack = 0.05)
                tone(0.0, 1.1, 55.0, wave = SINE, volume = 0.4, attack = 0.05)
            }
        },
        "alien" to {
            Synth.make(0.6) {
                listOf(1200.0, 800.0, 1500.0, 600.0).forEachIndexed { i, f -> tone(i * 0.12, 0.1, f, volume = 0.3, vibrato = 40.0) }
            }
        },
        "clown" to {
            Synth.make(0.7) {
                for (i in 0 until 2) {
                    tone(i * 0.32, 0.25, 350.0, wave = SAW, volume = 0.25, sustain = true)
                    tone(i * 0.32, 0.25, 356.0, wave = SQUARE, volume = 0.2, sustain = true)
                }
            }
        },
        "imbiss" to { Synth.make(0.8) { noise(0.0, 0.75, 0.25, sustain = true); tone(0.0, 0.2, 200.0, 120.0, SINE, 0.2) } },
        "sushi" to {
            Synth.make(1.6) { for ((f, v) in listOf(150.0 to 0.4, 237.0 to 0.25, 361.0 to 0.15)) tone(0.0, 1.55, f, volume = v, attack = 0.01) }
        },
        "cafe" to {
            Synth.make(1.0) {
                noise(0.0, 0.9, 0.2, smooth = 0.2, attack = 0.3)
                for (i in 0 until 5) tone(0.3 + i * 0.11, 0.06, 500.0 + i * 70, 900.0, SINE, 0.12)
            }
        },
    )

    private val executor = Executors.newSingleThreadExecutor { Thread(it, "sfx").apply { isDaemon = true } }
    private val clips = HashMap<String, Clip>()
    private var broken = false
    private val lastPlayed = HashMap<String, Long>()

    /** True when [name] has a sound, e.g. a tenant id. */
    fun has(name: String): Boolean = name in recipes

    /** Plays [name]; the same sound at most every [gapMillis] milliseconds. */
    fun play(name: String, gapMillis: Long = 60) {
        if (broken || Autopilot.active) return
        val volume = Options.current.volume
        if (volume <= 0 || name !in recipes) return
        val now = System.currentTimeMillis()
        if (now - (lastPlayed[name] ?: 0L) < gapMillis) return
        lastPlayed[name] = now
        executor.execute {
            runCatching {
                val clip = clips.getOrPut(name) { open(recipes.getValue(name)()) }
                (clip.getControl(FloatControl.Type.MASTER_GAIN) as? FloatControl)?.let { gain ->
                    gain.value = (20 * log10(volume / 100.0)).toFloat().coerceIn(gain.minimum, gain.maximum)
                }
                clip.stop()
                clip.framePosition = 0
                clip.start()
            }.onFailure { broken = true }
        }
    }

    /** Writes every sound as a WAV file into [dir], to listen to them outside the game. */
    fun exportWav(dir: java.io.File) {
        dir.mkdirs()
        for ((name, recipe) in recipes) {
            val synth = recipe()
            val format = AudioFormat(synth.rate.toFloat(), 16, 1, true, false)
            val data = synth.pcm()
            val stream = javax.sound.sampled.AudioInputStream(data.inputStream(), format, (data.size / 2).toLong())
            AudioSystem.write(stream, javax.sound.sampled.AudioFileFormat.Type.WAVE, java.io.File(dir, "$name.wav"))
        }
    }

    private fun open(synth: Synth): Clip {
        val format = AudioFormat(synth.rate.toFloat(), 16, 1, true, false)
        val data = synth.pcm()
        return AudioSystem.getClip().apply { open(format, data, 0, data.size) }
    }
}
