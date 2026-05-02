package com.truelock.enigma.media

import android.media.MediaRecorder

data class PreparedSpeechRecorder(
    val recorder: MediaRecorder,
    val audioSource: Int,
)

@Suppress("DEPRECATION")
fun createSpeechMediaRecorder(outputPath: String): PreparedSpeechRecorder {
    val candidateSources = listOf(
        MediaRecorder.AudioSource.MIC,
        MediaRecorder.AudioSource.CAMCORDER,
        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
        MediaRecorder.AudioSource.DEFAULT,
        MediaRecorder.AudioSource.VOICE_RECOGNITION,
    ).distinct()

    var lastError: Throwable? = null
    candidateSources.forEach { source ->
        val recorder = MediaRecorder()
        try {
            recorder.setAudioSource(source)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioChannels(1)
            recorder.setAudioSamplingRate(44_100)
            recorder.setAudioEncodingBitRate(128_000)
            recorder.setOutputFile(outputPath)
            recorder.prepare()
            recorder.start()
            return PreparedSpeechRecorder(recorder = recorder, audioSource = source)
        } catch (error: Throwable) {
            lastError = error
            runCatching { recorder.release() }
        }
    }

    throw IllegalStateException("Unable to prepare audio recorder", lastError)
}

fun describeAudioSource(audioSource: Int): String =
    when (audioSource) {
        MediaRecorder.AudioSource.DEFAULT -> "DEFAULT"
        MediaRecorder.AudioSource.MIC -> "MIC"
        MediaRecorder.AudioSource.CAMCORDER -> "CAMCORDER"
        MediaRecorder.AudioSource.VOICE_COMMUNICATION -> "VOICE_COMMUNICATION"
        MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
        else -> audioSource.toString()
    }
