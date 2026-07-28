package com.t1erno.whisperkeyboard.nativeengine

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioDecoder {

    /**
     * Decodes an audio file (e.g. m4a/aac/wav) into a 16kHz mono FloatArray normalized between -1.0 and 1.0.
     */
    fun decodeToPCM16kHzFloat(audioFile: File): FloatArray {
        if (!audioFile.exists() || audioFile.length() == 0L) {
            return FloatArray(0)
        }

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(audioFile.absolutePath)
        } catch (e: Exception) {
            extractor.release()
            return FloatArray(0)
        }

        var trackIndex = -1
        var format: MediaFormat? = null
        var mime: String? = null

        for (i in 0 until extractor.trackCount) {
            val trackFormat = extractor.getTrackFormat(i)
            val trackMime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
            if (trackMime.startsWith("audio/")) {
                trackIndex = i
                format = trackFormat
                mime = trackMime
                break
            }
        }

        if (trackIndex < 0 || format == null || mime == null) {
            extractor.release()
            return FloatArray(0)
        }

        extractor.selectTrack(trackIndex)

        val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
            format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        } else 16000
        val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
            format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        } else 1

        val codec: MediaCodec
        try {
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()
        } catch (e: Exception) {
            extractor.release()
            return FloatArray(0)
        }

        val rawPCM = ArrayList<Short>()
        val info = MediaCodec.BufferInfo()
        var sawInputEOS = false
        var sawOutputEOS = false

        while (!sawOutputEOS) {
            if (!sawInputEOS) {
                val inputBufferIndex = codec.dequeueInputBuffer(10000L)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                    if (inputBuffer != null) {
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputBufferIndex, 0, 0, 0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            sawInputEOS = true
                        } else {
                            val presentationTimeUs = extractor.sampleTime
                            codec.queueInputBuffer(
                                inputBufferIndex, 0, sampleSize,
                                presentationTimeUs, 0
                            )
                            extractor.advance()
                        }
                    }
                }
            }

            val outputBufferIndex = codec.dequeueOutputBuffer(info, 10000L)
            if (outputBufferIndex >= 0) {
                val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                if (outputBuffer != null && info.size > 0) {
                    outputBuffer.position(info.offset)
                    outputBuffer.limit(info.offset + info.size)

                    val shortBuffer = outputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    while (shortBuffer.hasRemaining()) {
                        rawPCM.add(shortBuffer.get())
                    }
                }

                codec.releaseOutputBuffer(outputBufferIndex, false)
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    sawOutputEOS = true
                }
            }
        }

        try {
            codec.stop()
            codec.release()
            extractor.release()
        } catch (_: Exception) {}

        if (rawPCM.isEmpty()) {
            return FloatArray(0)
        }

        // Downmix multi-channel to mono
        val monoPCM: ShortArray = if (channels > 1) {
            val monoSize = rawPCM.size / channels
            ShortArray(monoSize) { i ->
                var sum = 0
                for (c in 0 until channels) {
                    val idx = i * channels + c
                    if (idx < rawPCM.size) {
                        sum += rawPCM[idx]
                    }
                }
                (sum / channels).toShort()
            }
        } else {
            ShortArray(rawPCM.size) { i -> rawPCM[i] }
        }

        // Resample to 16000 Hz if necessary
        val targetPCM: ShortArray = if (sampleRate != 16000 && sampleRate > 0) {
            val ratio = 16000.0 / sampleRate.toDouble()
            val targetSize = (monoPCM.size * ratio).toInt()
            ShortArray(targetSize) { i ->
                val srcIdx = (i / ratio).toInt().coerceIn(0, monoPCM.size - 1)
                monoPCM[srcIdx]
            }
        } else {
            monoPCM
        }

        // Convert 16-bit PCM ShortArray to FloatArray (-1.0f to 1.0f)
        return FloatArray(targetPCM.size) { i ->
            targetPCM[i] / 32768.0f
        }
    }
}
