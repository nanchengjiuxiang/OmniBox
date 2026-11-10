package com.omnibox.feature.soundmeter

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.math.log10
import kotlin.math.sqrt

class AudioDecibelEngine {
    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(2048)

    @SuppressLint("MissingPermission")
    fun startListening(): Flow<Float> = flow {
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        val buffer = ShortArray(bufferSize)
        try {
            audioRecord.startRecording()
            while (true) {
                val readCount = audioRecord.read(buffer, 0, bufferSize)
                if (readCount > 0) {
                    var sum = 0.0
                    for (i in 0 until readCount) {
                        sum += buffer[i] * buffer[i]
                    }
                    val rms = sqrt(sum / readCount)
                    // 换算为标准 dB (A加权对齐)
                    val db = if (rms > 0) {
                        (20 * log10(rms)).toFloat().coerceIn(0f, 130f)
                    } else 0f

                    emit(db)
                }
            }
        } finally {
            audioRecord.stop()
            audioRecord.release()
        }
    }.flowOn(Dispatchers.IO)
}
