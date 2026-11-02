package crazytower.game

import java.io.BufferedInputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.math.pow

/**
 * Background music: a day and a night loop that fade into each other when the tower's light
 * changes, at the music volume from the options. It plays on its own thread; without a sound
 * card the game simply stays quiet.
 *
 * The loops come from [MusicSynth]. A WAV file at `music/day.wav` or `music/night.wav` in the
 * game's resources (game/src/main/resources/music/) replaces the made-up piece of that name.
 */
object Music {
    private const val RATE = MusicSynth.RATE
    private const val CHUNK = 1024

    /** Seconds for day and night to blend, and for volume changes. */
    private const val CROSSFADE = 5.0
    private const val VOLUME_FADE = 1.2

    private class Player(val track: MusicSynth.Track) {
        var position = 0
        var gain = 0f
    }

    @Volatile private var night = false
    @Volatile private var nightPlayer: Player? = null
    private var started = false

    /** Called every frame by the scenes; starts the music the first time. */
    fun update(isNight: Boolean) {
        night = isNight
        if (started || Autopilot.active) return
        started = true
        Thread(::run, "music").apply { isDaemon = true }.start()
    }

    private fun run() {
        runCatching {
            val day = Player(load("day") ?: MusicSynth.day())
            val format = AudioFormat(RATE.toFloat(), 16, 2, true, false)
            val line = AudioSystem.getSourceDataLine(format)
            // a quarter of a second in the sound card: short enough for quick volume changes
            line.open(format, RATE / 4 * 4)
            // the night piece is made while the day piece already plays
            Thread({ runCatching { nightPlayer = Player(load("night") ?: MusicSynth.night()) } }, "music-night").apply { isDaemon = true }.start()
            mix(line, day)
        }
    }

    private fun mix(line: SourceDataLine, day: Player) {
        val bytes = ByteArray(CHUNK * 4)
        var master = 0f
        val volumeStep = (1.0 / (VOLUME_FADE * RATE)).toFloat()
        val fadeStep = (1.0 / (CROSSFADE * RATE)).toFloat()
        while (true) {
            val target = loudness(Options.current.music)
            if (target == 0f && master < 0.0005f) {
                // muted: nothing to play, so the sound card gets nothing
                master = 0f
                if (line.isRunning) { line.stop(); line.flush() }
                Thread.sleep(100)
                continue
            }
            if (!line.isRunning) line.start()
            val nightOne = nightPlayer
            val players = listOfNotNull(day, nightOne)
            val wanted = if (night && nightOne != null) nightOne else day
            // a piece that has faded out completely starts at its beginning next time
            for (p in players) if (p !== wanted && p.gain == 0f) p.position = 0
            for (f in 0 until CHUNK) {
                master += (target - master).coerceIn(-volumeStep, volumeStep)
                var l = 0f
                var r = 0f
                for (p in players) {
                    p.gain = (p.gain + if (p === wanted) fadeStep else -fadeStep).coerceIn(0f, 1f)
                    if (p.gain == 0f) continue
                    // equal-power blend, so the middle of a crossfade does not dip
                    val g = p.gain * (2 - p.gain)
                    val s = p.track.samples
                    l += s[2 * p.position] * g
                    r += s[2 * p.position + 1] * g
                    if (++p.position == p.track.frames) p.position = 0
                }
                val left = (l * master).toInt().coerceIn(-32768, 32767)
                val right = (r * master).toInt().coerceIn(-32768, 32767)
                bytes[4 * f] = left.toByte(); bytes[4 * f + 1] = (left shr 8).toByte()
                bytes[4 * f + 2] = right.toByte(); bytes[4 * f + 3] = (right shr 8).toByte()
            }
            line.write(bytes, 0, bytes.size)
        }
    }

    /** The slider's 0 to 100 as a gain; ears hear loudness on a curve, not in a straight line. */
    private fun loudness(volume: Int): Float = (volume.coerceIn(0, 100) / 100.0).pow(1.8).toFloat()

    /** A player-supplied WAV from the resources, converted to the music's format, or null. */
    private fun load(name: String): MusicSynth.Track? = runCatching {
        val resource = Music::class.java.getResourceAsStream("/music/$name.wav") ?: return null
        AudioSystem.getAudioInputStream(BufferedInputStream(resource)).use { source ->
            val pcm = AudioFormat(source.format.sampleRate, 16, source.format.channels, true, false)
            val data = AudioSystem.getAudioInputStream(pcm, source).readAllBytes()
            val channels = pcm.channels
            val frames = data.size / (2 * channels)
            fun sample(frame: Int, channel: Int): Int {
                val i = (frame * channels + channel.coerceAtMost(channels - 1)) * 2
                return (data[i].toInt() and 0xFF) or (data[i + 1].toInt() shl 8)
            }
            // other sample rates are stretched to ours, linearly
            val ratio = pcm.sampleRate / RATE
            val outFrames = (frames / ratio).toInt()
            val out = ShortArray(outFrames * 2)
            for (f in 0 until outFrames) {
                val x = f * ratio
                val i = x.toInt().coerceAtMost(frames - 1)
                val j = (i + 1) % frames
                val t = x - i
                for (c in 0 until 2) out[2 * f + c] = (sample(i, c) * (1 - t) + sample(j, c) * t).toInt().toShort()
            }
            MusicSynth.Track(out)
        }
    }.getOrNull()

    /** Writes both pieces as WAV files into [dir], to listen to them outside the game. */
    fun exportWav(dir: java.io.File) {
        dir.mkdirs()
        for ((name, track) in listOf("musik_tag" to MusicSynth.day(), "musik_nacht" to MusicSynth.night())) {
            val bytes = ByteArray(track.samples.size * 2)
            for (i in track.samples.indices) {
                bytes[2 * i] = track.samples[i].toInt().toByte()
                bytes[2 * i + 1] = (track.samples[i].toInt() shr 8).toByte()
            }
            val format = AudioFormat(RATE.toFloat(), 16, 2, true, false)
            val stream = javax.sound.sampled.AudioInputStream(bytes.inputStream(), format, track.frames.toLong())
            AudioSystem.write(stream, javax.sound.sampled.AudioFileFormat.Type.WAVE, java.io.File(dir, "$name.wav"))
        }
    }
}
