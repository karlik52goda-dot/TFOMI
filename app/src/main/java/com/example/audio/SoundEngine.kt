package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

enum class SoundEffect {
  JUMP,
  WALL_JUMP,
  SHOOT,
  BUILD,
  HIT,
  SPLAT,
  CHEST,
  BOSS_ROAR,
  DASH,
}

class SoundEngine {
  private val sampleRate = 22050
  private val executor = Executors.newSingleThreadExecutor()

  @Volatile
  var enabled: Boolean = true

  private val buffers: Map<SoundEffect, ShortArray> by lazy {
    mapOf(
      SoundEffect.JUMP to synthSweep(0.09f, 240f, 560f, wave = Wave.SQUARE, gain = 0.25f),
      SoundEffect.WALL_JUMP to synthSweep(0.10f, 320f, 740f, wave = Wave.SQUARE, gain = 0.28f),
      SoundEffect.SHOOT to synthSweep(0.065f, 620f, 220f, wave = Wave.SINE, gain = 0.22f),
      SoundEffect.BUILD to synthSweep(0.075f, 180f, 320f, wave = Wave.NOISE_THUD, gain = 0.30f),
      SoundEffect.HIT to synthSweep(0.08f, 300f, 120f, wave = Wave.SAW, gain = 0.28f),
      SoundEffect.SPLAT to synthSweep(0.18f, 220f, 65f, wave = Wave.NOISE_THUD, gain = 0.35f),
      SoundEffect.CHEST to synthArpeggio(floatArrayOf(523.25f, 659.25f, 783.99f, 1046.5f), 0.065f),
      SoundEffect.BOSS_ROAR to synthSweep(0.32f, 140f, 55f, wave = Wave.SAW, gain = 0.38f),
      SoundEffect.DASH to synthSweep(0.09f, 440f, 880f, wave = Wave.NOISE_THUD, gain = 0.24f),
    )
  }

  fun play(effect: SoundEffect) {
    if (!enabled) return
    val pcm = buffers[effect] ?: return
    executor.execute {
      runCatching {
        val byteLen = pcm.size * 2
        val track = AudioTrack.Builder()
          .setAudioAttributes(
            AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_GAME)
              .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
              .build()
          )
          .setAudioFormat(
            AudioFormat.Builder()
              .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
              .setSampleRate(sampleRate)
              .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
              .build()
          )
          .setBufferSizeInBytes(byteLen.coerceAtLeast(2048))
          .setTransferMode(AudioTrack.MODE_STATIC)
          .build()
        track.write(pcm, 0, pcm.size)
        track.play()
        Thread.sleep((pcm.size * 1000L / sampleRate) + 20L)
        track.stop()
        track.release()
      }
    }
  }

  fun release() {
    executor.shutdownNow()
  }

  private enum class Wave { SINE, SQUARE, SAW, NOISE_THUD }

  private fun synthSweep(
    durationSec: Float,
    startFreq: Float,
    endFreq: Float,
    wave: Wave,
    gain: Float,
  ): ShortArray {
    val count = (sampleRate * durationSec).toInt().coerceAtLeast(1)
    val out = ShortArray(count)
    var phase = 0.0
    for (i in 0 until count) {
      val t = i.toFloat() / count
      val freq = startFreq + (endFreq - startFreq) * t
      phase += 2.0 * PI * freq / sampleRate
      val env = exp(-t * 3.5f)
      val raw = when (wave) {
        Wave.SINE -> sin(phase).toFloat()
        Wave.SQUARE -> if (sin(phase) >= 0.0) 1f else -1f
        Wave.SAW -> (((phase / (2.0 * PI)) % 1.0) * 2.0 - 1.0).toFloat()
        Wave.NOISE_THUD -> (sin(phase).toFloat() * 0.6f + (Random.nextFloat() * 2f - 1f) * 0.4f)
      }
      out[i] = (raw * env * gain * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }
    return out
  }

  private fun synthArpeggio(notes: FloatArray, noteDurSec: Float): ShortArray {
    val perNote = (sampleRate * noteDurSec).toInt().coerceAtLeast(1)
    val out = ShortArray(perNote * notes.size)
    notes.forEachIndexed { idx, freq ->
      var phase = 0.0
      for (i in 0 until perNote) {
        val t = i.toFloat() / perNote
        phase += 2.0 * PI * freq / sampleRate
        val env = (1f - t * 0.65f)
        val raw = if (sin(phase) >= 0.0) 0.7f else -0.7f
        out[idx * perNote + i] = (raw * env * 0.24f * Short.MAX_VALUE).toInt().toShort()
      }
    }
    return out
  }
}
