package com.example.syncplayer.audio

import com.example.common.util.launchIO
import com.example.media.audio.AACMediaCodecEncoder
import com.example.syncplayer.model.AudioItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow

@OptIn(DelicateCoroutinesApi::class)
class AudioTranscoder(
    item: AudioItem,
    private val scope: CoroutineScope = GlobalScope,
) {
    val progress = MutableStateFlow(0f)
    private val decoder = AudioDecoder(scope, item.filePath)
    private lateinit var encoder: AACMediaCodecEncoder
    private var targetSampleRate = decoder.audioInfo.sampleRate
    private var targetChannels = if (decoder.audioInfo.channelCount > 1) Channels.Stereo else Channels.Mono

    fun getInputFormat(): Format {
        return Format(decoder.audioInfo.sampleRate, if (decoder.audioInfo.channelCount == 1) Channels.Mono else Channels.Stereo)
    }

    fun setOutputFormat(sampleRate: Int, channelNum: Channels) {
        targetSampleRate = sampleRate
        targetChannels = channelNum
    }

    fun release() {
        decoder.release()
    }

    fun start() {
        val inputInfo = decoder.audioInfo
        val path = inputInfo.filePath.substringBeforeLast(".") + "_${System.currentTimeMillis()}.m4a"
        encoder = AACMediaCodecEncoder(path, targetSampleRate, targetChannels.value, inputInfo.bitRate, scope)
        val resample = PcmBufferResampler(decoder.queue)
        resample.addReSampler(inputInfo.channelCount, targetChannels.value, inputInfo.sampleRate, targetSampleRate)
        encoder.setPcmData(resample)
        decoder.start()
        encoder.start()
        startCalculateProgress()
    }

    private fun startCalculateProgress() {
        scope.launchIO {
            encoder.progress.collect {
                progress.emit(it / decoder.audioInfo.duration.toFloat())
            }
        }
    }

    class Format(
        val sampleRate: Int,
        val channelNum: Channels,
    )

    enum class Channels(val value: Int) {
        Mono(1),
        Stereo(2)
    }
}