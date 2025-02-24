package com.example.syncplayer.audio

import android.media.MediaCodec
import android.media.MediaCodec.BufferInfo
import android.media.MediaFormat
import com.example.common.util.launchIO
import com.example.media.audio.AudioExtractor
import com.example.media.audio.ShortsInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import java.nio.ByteBuffer

class AudioDecoder(
    private val scope: CoroutineScope,
    filePath: String,
) {
    val audioInfo: AudioInfo
    var queue = BlockQueue<ShortsInfo>(BUFFER_MAX)
    private val decoder: MediaCodec
    private val extractor = AudioExtractor(filePath)
    private var decodeJob: Job? = null
    private var state = State.Init

    init {
        val format = extractor.format
        val type = extractor.format.getString(MediaFormat.KEY_MIME) ?: ""
        audioInfo = AudioInfo.createInfo(filePath, format)
        decoder = MediaCodec.createDecoderByType(type)
        decoder.configure(format, null, null, 0)
    }

    fun start() {
        decoder.start()
        startInner()
        state = State.Running
    }

    suspend fun seekTo(timeUs: Long) {
        decodeJob?.cancelAndJoin()
        queue.clear()
        decoder.flush()
        val progress = timeUs.coerceAtLeast(0).coerceAtMost(audioInfo.duration)
        extractor.seekTo(progress)
        if (progress == audioInfo.duration) {
            val shortsInfo = ShortsInfo(ShortArray(0), 0, 0, audioInfo.duration, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            queue.produce(shortsInfo)
        } else {
            startInner()
        }
    }

    private fun startInner() {
        decodeJob =
            scope.launchIO {
                val info = BufferInfo()
                while (isActive) {
                    extractorData()
                    decoderData(info)
                }
            }
    }

    private fun extractorData() {
        val index = decoder.dequeueInputBuffer(0)
        if (index > 0) {
            val inputBuffer = decoder.getInputBuffer(index) ?: ByteBuffer.allocate(0)
            val sampleSize = extractor.readSampleData(inputBuffer, 0)
            if (sampleSize < 0) {
                decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            } else {
                decoder.queueInputBuffer(index, 0, sampleSize, extractor.sampleTime, 0)
                extractor.advance()
            }
        }
    }

    private suspend fun decoderData(info: BufferInfo) {
        val index = decoder.dequeueOutputBuffer(info, 0)
        if (index > 0) {
            val byteBuffer = decoder.getOutputBuffer(index) ?: ByteBuffer.allocate(0)
            queue.produce(ShortsInfo.createShortsInfo(byteBuffer, info))
            decoder.releaseOutputBuffer(index, false)
        }
    }

    fun release() {
        if (state < State.Running) return
        scope.launchIO {
            decodeJob?.cancelAndJoin()
            queue.clear()
            decoder.stop()
            decoder.release()
            extractor.release()
            state = State.Init
        }
    }

    companion object {
        const val BUFFER_MAX = 4
    }

    enum class State {
        Init,
        Running,
    }
}
