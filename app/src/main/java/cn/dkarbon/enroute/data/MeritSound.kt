package cn.dkarbon.enroute.data

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * 木鱼敲击声 —— **程序合成，不带任何音频素材**。
 *
 * 为什么不打包 mp3/ogg：音源有版权问题，而且一个 100ms 的音效文件为了几 KB
 * 还要走一次资源加载。这里直接算出 PCM 写成一个临时 WAV，丢给 [SoundPool]。
 *
 * 木头的音色 = 两件事：
 *   1. **极短的敲击瞬态**（4ms 的白噪爆音）—— 「嗒」的那一下
 *   2. **快速衰减的共振体**（~420Hz 与 ~780Hz 两个阻尼正弦）—— 「咚」的那一下
 * 合成参数调过：衰减系数 35/60 让它在 ~120ms 内消失，连敲不会糊成一片。
 */
object MeritSound {

    private const val SR = 44100
    private const val FILE_NAME = "muyu_knock.wav"

    private var pool: SoundPool? = null
    private var sampleId = 0
    private var ready = false

    /** 懒初始化：第一次敲才加载，不占启动时间 */
    fun ensure(ctx: Context) {
        if (pool != null) return
        val f = File(ctx.cacheDir, FILE_NAME)
        if (!f.exists() || f.length() < 1000) {
            runCatching { f.writeBytes(buildWav()) }
        }
        pool = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
            .also { sp ->
                sp.setOnLoadCompleteListener { _, _, status -> ready = status == 0 }
                sampleId = sp.load(f.absolutePath, 1)
            }
    }

    fun play(ctx: Context, volume: Float = 1f) {
        ensure(ctx)
        if (ready) runCatching { pool?.play(sampleId, volume, volume, 1, 0, 1f) }
    }

    fun release() {
        runCatching { pool?.release() }
        pool = null
        ready = false
    }

    // ── PCM 合成 ──────────────────────────────────────────

    private fun buildWav(): ByteArray {
        val durSec = 0.16
        val n = (SR * durSec).toInt()
        val pcm = ShortArray(n)
        var seed = 12345L

        for (i in 0 until n) {
            val t = i.toDouble() / SR
            // 快速衰减包络
            val env = exp(-t * 35.0)
            // 木头体共振：两个阻尼正弦
            var s = 0.60 * sin(2 * PI * 420.0 * t) * env
            s += 0.35 * sin(2 * PI * 780.0 * t) * exp(-t * 60.0)
            // 敲击瞬态：4ms 白噪爆音（线性衰减）
            if (t < 0.004) {
                seed = seed * 6364136223846793005L + 1442695040888963407L
                val rnd = ((seed ushr 33) % 2001 - 1000) / 1000.0
                s += 0.75 * rnd * (1.0 - t / 0.004)
            }
            pcm[i] = (s.coerceIn(-1.0, 1.0) * 26000).toInt().toShort()
        }
        return wavBytes(pcm, SR)
    }

    /** 手写 44 字节 WAV 头 + PCM16 数据 */
    private fun wavBytes(pcm: ShortArray, sampleRate: Int): ByteArray {
        val dataSize = pcm.size * 2
        val out = ByteArray(44 + dataSize)
        val le = { v: Int, at: Int, bytes: Int ->
            for (b in 0 until bytes) out[at + b] = ((v shr (8 * b)) and 0xFF).toByte()
        }
        "RIFF".toByteArray().copyInto(out, 0)
        le(36 + dataSize, 4, 4)
        "WAVE".toByteArray().copyInto(out, 8)
        "fmt ".toByteArray().copyInto(out, 12)
        le(16, 16, 4)          // fmt chunk 大小
        le(1, 20, 2)           // PCM
        le(1, 22, 2)           // 单声道
        le(sampleRate, 24, 4)
        le(sampleRate * 2, 28, 4)  // byte rate
        le(2, 32, 2)           // block align
        le(16, 34, 2)          // bits
        "data".toByteArray().copyInto(out, 36)
        le(dataSize, 40, 4)
        var p = 44
        for (v in pcm) {
            out[p++] = (v.toInt() and 0xFF).toByte()
            out[p++] = ((v.toInt() shr 8) and 0xFF).toByte()
        }
        return out
    }
}
