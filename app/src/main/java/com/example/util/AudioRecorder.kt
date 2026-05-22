package com.example.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

class AudioRecorder(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var isRecording = false

    fun startRecording(): File? {
        if (isRecording) return currentFile

        try {
            val audioFile = File(context.cacheDir, "meeting_rec_${System.currentTimeMillis()}.m4a")
            currentFile = audioFile

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioSamplingRate(44100)
            recorder.setAudioEncodingBitRate(96000)
            recorder.setOutputFile(audioFile.absolutePath)

            recorder.prepare()
            recorder.start()

            mediaRecorder = recorder
            isRecording = true
            Log.d("AudioRecorder", "Recording started successfully targeting: ${audioFile.absolutePath}")
            return audioFile
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to start recording", e)
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
            currentFile = null
            return null
        }
    }

    fun stopRecording(): File? {
        if (!isRecording) return null

        try {
            mediaRecorder?.stop()
            Log.d("AudioRecorder", "Recording stopped successfully")
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Exception while stopping recording (too short?)", e)
        } finally {
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
        }
        return currentFile
    }

    fun getMaxAmplitude(): Int {
        return try {
            mediaRecorder?.maxAmplitude ?: 0
        } catch (e: Exception) {
            0
        }
    }

    fun isRecording() = isRecording
}
