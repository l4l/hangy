package me.kitsu.hangy.audio

import android.media.AudioManager
import android.media.ToneGenerator

/** Minimal audio cue abstraction, with a no-op implementation for tests and previews. */
interface SoundCue {
    /** A short countdown blip (played in the final seconds of a phase). */
    fun tick()

    /** Marks a hang beginning — deliberately distinct from [end] so start and stop are told apart. */
    fun start()

    /** Marks a hang ending or the routine completing. */
    fun end()

    companion object {
        val NoOp: SoundCue = object : SoundCue {
            override fun tick() = Unit
            override fun start() = Unit
            override fun end() = Unit
        }
    }
}

/**
 * [SoundCue] backed by [ToneGenerator]. The generator is created lazily and failures are
 * swallowed, since some devices throw when the media stream is unavailable — a missing beep must
 * never crash a training session. No `release()`: [lazy] cannot rebuild the generator, so
 * releasing it would silently mute every later session.
 */
class ToneGeneratorSoundCue : SoundCue {

    private val generator: ToneGenerator? by lazy {
        runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME) }.getOrNull()
    }

    override fun tick() {
        runCatching { generator?.startTone(ToneGenerator.TONE_PROP_BEEP, TICK_MS) }
    }

    override fun start() {
        // A rising acknowledgement tone — clearly different from the end's double beep.
        runCatching { generator?.startTone(ToneGenerator.TONE_PROP_ACK, START_MS) }
    }

    override fun end() {
        runCatching { generator?.startTone(ToneGenerator.TONE_PROP_BEEP2, END_MS) }
    }

    private companion object {
        const val VOLUME = 90
        const val TICK_MS = 120
        const val START_MS = 200
        const val END_MS = 350
    }
}
