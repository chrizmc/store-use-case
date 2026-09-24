package com.bopis.associate.ui.assistant

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.FileOutputStream

// Minimal 16kHz mono 16-bit PCM WAV recorder -- whisper.cpp requires this exact format
// and doesn't transcode, so MediaRecorder's compressed formats (AAC/3GP) won't work here.
class WavRecorder {
    private val sampleRate = 16000
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    @Volatile private var isRecording = false

    fun start(outputFile: File) {
        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufferSize * 2,
        )
        audioRecord = record
        isRecording = true
        record.startRecording()

        recordingThread = Thread {
            val pcmFile = File(outputFile.parentFile, "${outputFile.name}.pcm")
            FileOutputStream(pcmFile).use { out ->
                val buffer = ByteArray(minBufferSize)
                while (isRecording) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) out.write(buffer, 0, read)
                }
            }
            writeWavFile(pcmFile, outputFile)
            pcmFile.delete()
        }.also { it.start() }
    }

    fun stop() {
        isRecording = false
        recordingThread?.join()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    private fun writeWavFile(pcmFile: File, wavFile: File) {
        val pcmSize = pcmFile.length()
        val byteRate = sampleRate * 2
        FileOutputStream(wavFile).use { out ->
            out.write("RIFF".toByteArray())
            out.write(intToBytes((36 + pcmSize).toInt()))
            out.write("WAVE".toByteArray())
            out.write("fmt ".toByteArray())
            out.write(intToBytes(16))
            out.write(shortToBytes(1)) // PCM
            out.write(shortToBytes(1)) // mono
            out.write(intToBytes(sampleRate))
            out.write(intToBytes(byteRate))
            out.write(shortToBytes(2)) // block align
            out.write(shortToBytes(16)) // bits per sample
            out.write("data".toByteArray())
            out.write(intToBytes(pcmSize.toInt()))
            pcmFile.inputStream().use { it.copyTo(out) }
        }
    }

    private fun intToBytes(v: Int) = byteArrayOf(
        (v and 0xff).toByte(), ((v shr 8) and 0xff).toByte(),
        ((v shr 16) and 0xff).toByte(), ((v shr 24) and 0xff).toByte(),
    )

    private fun shortToBytes(v: Int) = byteArrayOf((v and 0xff).toByte(), ((v shr 8) and 0xff).toByte())
}
