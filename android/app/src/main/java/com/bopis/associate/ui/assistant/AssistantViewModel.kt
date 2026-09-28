package com.bopis.associate.ui.assistant

import android.content.Context
import android.media.MediaPlayer
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bopis.associate.Constants
import com.bopis.associate.data.BopisRepository
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

// One exchange in the chat window: what the associate asked (typed, or transcribed from
// voice) and the assistant's answer.
data class ChatTurn(val question: String, val transcript: String?, val answer: String?)

class AssistantViewModel(
    private val repository: BopisRepository,
    private val context: Context,
) : ViewModel() {
    var question by mutableStateOf("")
    val turns = mutableStateListOf<ChatTurn>()
    var isRecording by mutableStateOf(false)
        private set
    var isBusy by mutableStateOf(false)
        private set

    private val recorder = WavRecorder()
    private val recordingFile = File(context.cacheDir, "question.wav")

    fun askText() {
        val q = question
        if (q.isBlank()) return
        question = ""
        viewModelScope.launch {
            isBusy = true
            val answer = repository.assistantQuery(q, Constants.STORE_ID).answer
            turns.add(ChatTurn(q, transcript = null, answer = answer))
            isBusy = false
        }
    }

    fun startRecording() {
        isRecording = true
        recorder.start(recordingFile)
    }

    fun stopRecordingAndAsk() {
        isRecording = false
        recorder.stop()
        viewModelScope.launch {
            isBusy = true
            val audioPart = MultipartBody.Part.createFormData(
                "audio", "question.wav", recordingFile.asRequestBody("audio/wav".toMediaType())
            )
            val storeIdPart = Constants.STORE_ID.toRequestBody("text/plain".toMediaType())
            val response = repository.assistantVoice(audioPart, storeIdPart)
            turns.add(ChatTurn(response.question ?: question, response.question, response.answer))
            response.audioBase64?.let { playAnswerAudio(it) }
            isBusy = false
        }
    }

    private fun playAnswerAudio(base64: String) {
        val file = File(context.cacheDir, "answer.wav")
        file.writeBytes(Base64.decode(base64, Base64.DEFAULT))
        MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnPreparedListener { start() }
            setOnCompletionListener { release() }
            prepareAsync()
        }
    }
}

