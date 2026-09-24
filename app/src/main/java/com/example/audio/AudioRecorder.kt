package com.example.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class AudioRecorder(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var currentFile: File? = null

    /**
     * Starts microphone audio recording, storing it in the app's cache directory astemp_recording.m4a.
     * Returns the file location if initiated successfully, or null if it fails.
     */
    fun startRecording(): File? {
        val file = File(context.cacheDir, "temp_recording.aac")
        if (file.exists()) {
            file.delete()
        }

        try {
            @Suppress("DEPRECATION")
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            currentFile = file
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            mediaRecorder = null
            return null
        }
    }

    /**
     * Stops the audio recording. Returns the recorded file, or null.
     */
    fun stopRecording(): File? {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaRecorder = null
        }
        return currentFile
    }

    /**
     * Checks if the recorder is currently recording.
     */
    fun isRecording(): Boolean {
        return mediaRecorder != null
    }

    /**
     * Retrieves the current maximum amplitude (ranging from 0 to 32767) if recording.
     */
    fun getMaxAmplitude(): Int {
        return try {
            mediaRecorder?.maxAmplitude ?: 0
        } catch (e: Exception) {
            0
        }
    }
}
