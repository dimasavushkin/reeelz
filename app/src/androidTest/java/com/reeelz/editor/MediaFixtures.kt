package com.reeelz.editor

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object MediaFixtures {
    fun video(file: File, seconds: Int = 4, fps: Int = 15) {
        val width = 320
        val height = 240
        val codec = MediaCodec.createEncoderByType("video/avc")
        val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var started = false
        try {
            val format = MediaFormat.createVideoFormat("video/avc", width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_BIT_RATE, 300_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val data = ByteArray(width * height * 3 / 2) { if (it < width * height) 100.toByte() else 128.toByte() }
            val info = MediaCodec.BufferInfo()
            var frame = 0
            var eos = false
            var done = false
            var track = -1
            val deadline = System.currentTimeMillis() + 60000
            while (!done) {
                check(System.currentTimeMillis() < deadline) { "Fixture encoder timed out" }
                if (!eos) {
                    val index = codec.dequeueInputBuffer(10000)
                    if (index >= 0) {
                        if (frame < seconds * fps) {
                            codec.getInputBuffer(index)!!.apply { clear(); put(data) }
                            codec.queueInputBuffer(index, 0, data.size, frame * 1_000_000L / fps, 0)
                            frame++
                        } else {
                            codec.queueInputBuffer(index, 0, 0, seconds * 1_000_000L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            eos = true
                        }
                    }
                }
                val index = codec.dequeueOutputBuffer(info, 10000)
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    track = muxer.addTrack(codec.outputFormat); muxer.start(); started = true
                } else if (index >= 0) {
                    if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        val out = codec.getOutputBuffer(index)!!
                        out.position(info.offset); out.limit(info.offset + info.size)
                        muxer.writeSampleData(track, out, info)
                    }
                    done = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(index, false)
                }
            }
        } finally {
            runCatching { codec.stop() }; codec.release()
            if (started) runCatching { muxer.stop() }
            muxer.release()
        }
    }
    fun music(file: File) {
        val rate = 44100
        val count = rate / 2
        val buffer = ByteBuffer.allocate(44 + count * 2).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(36 + count * 2).put("WAVEfmt ".toByteArray())
        buffer.putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        buffer.put("data".toByteArray()).putInt(count * 2)
        repeat(count) { buffer.putShort((kotlin.math.sin(it * 2 * Math.PI * 440 / rate) * 12000).toInt().toShort()) }
        file.writeBytes(buffer.array())
    }
}
