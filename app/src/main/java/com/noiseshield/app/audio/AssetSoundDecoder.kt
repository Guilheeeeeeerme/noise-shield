package com.noiseshield.app.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.noiseshield.app.data.MaskingSoundId
import java.nio.ByteOrder

data class DecodedPcm16(
    val samples: ShortArray,
    val sampleRate: Int,
)

object AssetSoundDecoder {
    fun decode(context: Context, sound: MaskingSoundId): DecodedPcm16? {
        val assetFile = sound.assetFile ?: return null
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            context.assets.openFd("audio/$assetFile").use { descriptor ->
                extractor.setDataSource(
                    descriptor.fileDescriptor,
                    descriptor.startOffset,
                    descriptor.length,
                )
            }
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val durationUs = format.getLong(MediaFormat.KEY_DURATION)
            var output = ShortArray(
                ((durationUs * sampleRate / 1_000_000L) + 4_096L)
                    .coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            )
            var written = 0
            var inputEnded = false
            var outputEnded = false
            val info = MediaCodec.BufferInfo()
            val decoder = MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }
            codec = decoder
            while (!outputEnded) {
                if (!inputEnded) {
                    val inputIndex = decoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val input = decoder.getInputBuffer(inputIndex) ?: continue
                        val size = extractor.readSampleData(input, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputEnded = true
                        } else {
                            decoder.queueInputBuffer(
                                inputIndex,
                                0,
                                size,
                                extractor.sampleTime,
                                0,
                            )
                            extractor.advance()
                        }
                    }
                }
                when (val outputIndex = decoder.dequeueOutputBuffer(info, CODEC_TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        sampleRate = decoder.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outputIndex >= 0) {
                        decoder.getOutputBuffer(outputIndex)?.let { buffer ->
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            val shorts = buffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            if (written + shorts.remaining() > output.size) {
                                output = output.copyOf(
                                    maxOf(output.size * 2, written + shorts.remaining()),
                                )
                            }
                            val count = shorts.remaining()
                            shorts.get(output, written, count)
                            written += count
                        }
                        outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        decoder.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }
            DecodedPcm16(output.copyOf(written), sampleRate)
        } finally {
            runCatching { codec?.stop() }
            codec?.release()
            extractor.release()
        }
    }

    private const val CODEC_TIMEOUT_US = 10_000L
}
