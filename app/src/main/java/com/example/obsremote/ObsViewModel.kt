package com.example.obsremote

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

data class AudioInput(val name: String, val muted: Boolean)

data class ObsUiState(
    val scenes: List<String> = emptyList(),
    val currentScene: String? = null,
    val streaming: Boolean = false,
    val recording: Boolean = false,
    val inputs: List<AudioInput> = emptyList(),
    val message: String? = null,
)

data class SavedSettings(val host: String, val port: String, val password: String)

class ObsViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("obs_remote", Context.MODE_PRIVATE)
    private val client = ObsClient()

    val connection: StateFlow<ConnectionState> = client.state

    private val _ui = MutableStateFlow(ObsUiState())
    val ui: StateFlow<ObsUiState> = _ui

    val saved = SavedSettings(
        host = prefs.getString("host", "") ?: "",
        port = prefs.getString("port", "4455") ?: "4455",
        password = prefs.getString("password", "") ?: "",
    )

    init {
        viewModelScope.launch {
            client.state.collect { if (it is ConnectionState.Connected) refreshAll() }
        }
        viewModelScope.launch {
            client.events.collect { onEvent(it) }
        }
    }

    fun connect(host: String, port: String, password: String) {
        prefs.edit()
            .putString("host", host.trim())
            .putString("port", port.trim())
            .putString("password", password)
            .apply()
        client.connect(host, port.trim().toIntOrNull() ?: 4455, password)
    }

    fun disconnect() {
        client.disconnect()
        _ui.value = ObsUiState()
    }

    override fun onCleared() {
        client.disconnect()
    }

    fun selectScene(name: String) = launchRequest {
        client.request("SetCurrentProgramScene", JSONObject().put("sceneName", name))
    }

    fun toggleStream() = launchRequest { client.request("ToggleStream") }

    fun toggleRecord() = launchRequest { client.request("ToggleRecord") }

    fun toggleMute(inputName: String) = launchRequest {
        client.request("ToggleInputMute", JSONObject().put("inputName", inputName))
    }

    fun dismissMessage() = _ui.update { it.copy(message = null) }

    private fun refreshAll() = viewModelScope.launch {
        try {
            val sceneData = client.request("GetSceneList")
            val arr = sceneData.getJSONArray("scenes")
            val names = (0 until arr.length())
                .map { arr.getJSONObject(it).getString("sceneName") }
                .reversed()
            val current = sceneData.optString("currentProgramSceneName")
            val streaming = client.request("GetStreamStatus").getBoolean("outputActive")
            val recording = client.request("GetRecordStatus").getBoolean("outputActive")
            _ui.update {
                it.copy(scenes = names, currentScene = current, streaming = streaming, recording = recording)
            }
            loadInputs()
        } catch (e: ObsException) {
            showError(e)
        }
    }

    private suspend fun loadInputs() {
        val list = client.request("GetInputList").getJSONArray("inputs")
        val result = mutableListOf<AudioInput>()
        for (i in 0 until list.length()) {
            val name = list.getJSONObject(i).getString("inputName")
            try {
                val muted = client.request("GetInputMute", JSONObject().put("inputName", name))
                    .getBoolean("inputMuted")
                result += AudioInput(name, muted)
            } catch (e: ObsException) {
            }
        }
        _ui.update { it.copy(inputs = result) }
    }

    private fun onEvent(e: ObsEvent) {
        when (e.type) {
            "CurrentProgramSceneChanged" ->
                _ui.update { it.copy(currentScene = e.data.optString("sceneName")) }
            "StreamStateChanged" ->
                _ui.update { it.copy(streaming = e.data.optBoolean("outputActive")) }
            "RecordStateChanged" ->
                _ui.update { it.copy(recording = e.data.optBoolean("outputActive")) }
            "InputMuteStateChanged" -> {
                val name = e.data.optString("inputName")
                val muted = e.data.optBoolean("inputMuted")
                _ui.update { s ->
                    s.copy(inputs = s.inputs.map { if (it.name == name) it.copy(muted = muted) else it })
                }
            }
            "SceneListChanged", "InputCreated", "InputRemoved", "InputNameChanged" -> refreshAll()
        }
    }

    private fun launchRequest(block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
        } catch (e: ObsException) {
            showError(e)
        }
    }

    private fun showError(e: ObsException) = _ui.update { it.copy(message = e.message) }
}
