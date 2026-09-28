package com.bopis.associate.ui.agent

import android.content.Context
import android.media.MediaPlayer
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bopis.associate.data.BopisRepository
import com.bopis.associate.data.remote.AppNotification
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

sealed class AgentPrompt(open val notificationId: String) {
    data class GuideToShelf(
        override val notificationId: String,
        val productName: String,
        val aisle: String,
    ) : AgentPrompt(notificationId)

    data class NavigateToStore(
        override val notificationId: String,
        val storeName: String,
        val storeAddress: String,
    ) : AgentPrompt(notificationId)
}

// The exact question shown on the prompt card - shared so the spoken version matches the
// displayed version word-for-word.
fun promptQuestion(prompt: AgentPrompt): String = when (prompt) {
    is AgentPrompt.GuideToShelf ->
        "That item is out of stock, but ${prompt.productName} works as a substitute in Aisle ${prompt.aisle}. Customer accepted alternative. Shall I guide you to the shelf?"
    is AgentPrompt.NavigateToStore ->
        "That item is out of stock here, but ${prompt.storeName} still has it. Customer accepted surcharge for collecting the product from another supermarket for them. Shall I navigate you there?"
}

// A small proactive assistant: watches for the two rule-driven notifications that imply
// physical movement (walking to a substitute's shelf, or driving to another store) and asks
// the associate whether to guide them there, instead of making them notice it in the Alerts tab.
class ProactiveAgentViewModel(
    private val repository: BopisRepository,
    private val context: Context,
) : ViewModel() {
    var prompt by mutableStateOf<AgentPrompt?>(null)
        private set

    var navigationTarget by mutableStateOf<AgentPrompt?>(null)
        private set

    // In-memory only, on purpose: a fresh app session should be free to re-ask, and this is a
    // demo app, not a system needing durable "already answered" state.
    private val handledIds = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            while (true) {
                checkForPrompt()
                delay(3000)
            }
        }
    }

    private suspend fun checkForPrompt() {
        if (prompt != null || navigationTarget != null) return
        val result = repository.pull(since = 0, storeId = null) ?: return
        val candidate = result.changes.notifications
            .firstOrNull { it.id !in handledIds && it.type in RELEVANT_TYPES }
            ?: return
        val newPrompt = toPrompt(candidate) ?: return
        prompt = newPrompt
        speak(promptQuestion(newPrompt))
    }

    // Rule-fired prompts are spoken, not just shown as text - a plain toast/card is easy to
    // miss while the associate is walking the floor with the phone in a pocket or holster.
    private fun speak(text: String) {
        viewModelScope.launch {
            val audioBase64 = repository.assistantSpeak(text) ?: return@launch
            val file = File(context.cacheDir, "agent-speech.wav")
            file.writeBytes(Base64.decode(audioBase64, Base64.DEFAULT))
            MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnPreparedListener { start() }
                setOnCompletionListener { release() }
                prepareAsync()
            }
        }
    }

    private fun toPrompt(notification: AppNotification): AgentPrompt? {
        val payload = notification.payload
        return when (notification.type) {
            "substitute_suggested" -> {
                val aisle = payload["aisle"]?.jsonPrimitive?.content ?: return null
                val name = payload["substituteName"]?.jsonPrimitive?.content ?: "the substitute item"
                AgentPrompt.GuideToShelf(notification.id, name, aisle)
            }
            "available_at_other_store" -> {
                val storeName = payload["storeName"]?.jsonPrimitive?.content ?: return null
                val storeAddress = payload["storeAddress"]?.jsonPrimitive?.content ?: "Address unavailable"
                AgentPrompt.NavigateToStore(notification.id, storeName, storeAddress)
            }
            else -> null
        }
    }

    fun respondYes() {
        val current = prompt ?: return
        handledIds += current.notificationId
        prompt = null
        navigationTarget = current

        // Record what the customer agreed to, so it shows up for the associate in the
        // Alerts "Sent" tab (same place a manager notification would show).
        val message = when (current) {
            is AgentPrompt.GuideToShelf -> "Customer accepted alternative"
            is AgentPrompt.NavigateToStore ->
                "Customer accepted surcharge for collecting the product from another supermarket for them"
        }
        viewModelScope.launch { repository.notifyAssociate("customer_accepted", message) }
    }

    fun respondNo() {
        val current = prompt ?: return
        handledIds += current.notificationId
        prompt = null
    }

    fun dismissNavigation() {
        navigationTarget = null
    }

    companion object {
        private val RELEVANT_TYPES = setOf("substitute_suggested", "available_at_other_store")
    }
}
