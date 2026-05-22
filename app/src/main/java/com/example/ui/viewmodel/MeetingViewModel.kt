package com.example.ui.viewmodel

import android.app.Application
import android.media.MediaPlayer
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.RetrofitClient
import com.example.data.local.AppDatabase
import com.example.data.local.MeetingEntity
import com.example.data.model.DialogueLine
import com.example.data.model.MeetingAnalysisResult
import com.example.data.model.Participant
import com.example.data.repository.MeetingRepository
import com.example.util.AudioRecorder
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MeetingViewModel(application: Application) : AndroidViewModel(application) {

    private val meetingDao = AppDatabase.getDatabase(application).meetingDao()
    private val repository = MeetingRepository(meetingDao)

    private val audioRecorder = AudioRecorder(application)
    private var activeRecordingFile: File? = null
    private var timerJob: Job? = null
    private var amplitudeJob: Job? = null

    // UI State Holders
    val allMeetings: StateFlow<List<MeetingEntity>> = repository.allMeetings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private val _recordingDurationSeconds = MutableStateFlow(0)
    val recordingDurationSeconds = _recordingDurationSeconds.asStateFlow()

    private val _amplitudes = MutableStateFlow<List<Float>>(emptyList())
    val amplitudes = _amplitudes.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    private val _selectedMeeting = MutableStateFlow<MeetingEntity?>(null)
    val selectedMeeting = _selectedMeeting.asStateFlow()

    // Error and Success alerts
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage = _successMessage.asStateFlow()

    // Media Playback State
    private var mediaPlayer: MediaPlayer? = null
    private var playbackProgressJob: Job? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0f) // 0.0 to 1.0
    val playbackProgress = _playbackProgress.asStateFlow()

    private val _playbackPositionMs = MutableStateFlow(0)
    val playbackPositionMs = _playbackPositionMs.asStateFlow()

    private val _playbackDurationMs = MutableStateFlow(0)
    val playbackDurationMs = _playbackDurationMs.asStateFlow()

    // Active screen index (0 = Tab Record, 1 = Tab History)
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab = _selectedTab.asStateFlow()

    // Recording Actions
    fun startRecording() {
        if (_isRecording.value) return
        
        _errorMessage.value = null
        _successMessage.value = null
        
        val file = audioRecorder.startRecording()
        if (file != null) {
            activeRecordingFile = file
            _isRecording.value = true
            _recordingDurationSeconds.value = 0
            _amplitudes.value = emptyList()

            // Chronometer Loop
            timerJob = viewModelScope.launch {
                while (_isRecording.value) {
                    delay(1000)
                    _recordingDurationSeconds.value += 1
                }
            }

            // Amplitude Meter Loop
            amplitudeJob = viewModelScope.launch {
                while (_isRecording.value) {
                    delay(100)
                    val amp = audioRecorder.getMaxAmplitude()
                    // Normalize amplitude to 0.0 - 1.0 (approx 0 - 32767)
                    val normalized = (amp.toFloat() / 32768f).coerceIn(0f, 1f)
                    
                    val currentList = _amplitudes.value.toMutableList()
                    if (currentList.size >= 40) {
                        currentList.removeAt(0)
                    }
                    currentList.add(normalized)
                    _amplitudes.value = currentList
                }
            }
        } else {
            _errorMessage.value = "Error al inicializar el micrófono. Por favor otorga permisos."
        }
    }

    fun stopAndProcessRecording() {
        if (!_isRecording.value) return

        _isRecording.value = false
        timerJob?.cancel()
        amplitudeJob?.cancel()

        val savedFile = audioRecorder.stopRecording()
        if (savedFile != null && savedFile.exists()) {
            val durationMs = (_recordingDurationSeconds.value * 1000).toLong()
            processAudioFile(savedFile, durationMs)
        } else {
            _errorMessage.value = "Error al guardar el archivo de grabación."
        }
    }

    fun discardRecording() {
        if (_isRecording.value) {
            _isRecording.value = false
            timerJob?.cancel()
            amplitudeJob?.cancel()
            audioRecorder.stopRecording()
        }
        activeRecordingFile?.let {
            if (it.exists()) it.delete()
        }
        activeRecordingFile = null
        _recordingDurationSeconds.value = 0
        _amplitudes.value = emptyList()
        _successMessage.value = "Grabación descartada."
    }

    private fun processAudioFile(file: File, durationMs: Long) {
        _isProcessing.value = true
        _errorMessage.value = null
        _selectedTab.value = 0 // Show progress in the record/process screen

        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    file.readBytes()
                }

                // Call Gemini for structured translation, participant listing, and executive summary
                val result = repository.analyzeMeetingAudio(bytes, "audio/aac")
                
                // Convert list objects to JSON string using Moshi JSON adapter
                val moshi = repository2moshi()
                val pType = Types.newParameterizedType(List::class.java, Participant::class.java)
                val tType = Types.newParameterizedType(List::class.java, DialogueLine::class.java)
                val transcriptJson = moshi.adapter<List<DialogueLine>>(tType).toJson(result.transcript)
                val participantsJson = moshi.adapter<List<Participant>>(pType).toJson(result.participants)

                val entity = MeetingEntity(
                    title = result.title,
                    timestamp = System.currentTimeMillis(),
                    audioPath = file.absolutePath,
                    durationMs = durationMs,
                    transcriptJson = transcriptJson,
                    summaryMarkdown = result.summaryMarkdown,
                    participantsJson = participantsJson
                )

                repository.insert(entity)
                
                // Set as currently active item and head over to history view to view details!
                _selectedMeeting.value = entity
                _selectedTab.value = 1 // Auto navigation to history detail
                _successMessage.value = "Minuta generada correctamente para: ${result.title}"
            } catch (e: Exception) {
                Log.e("MeetingViewModel", "Analysis failed", e)
                _errorMessage.value = "Fallo de análisis por voz: ${e.localizedMessage ?: "Consulte su conexión o API Key de Gemini."}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    // Tab Switching
    fun selectTab(index: Int) {
        _selectedTab.value = index
    }

    // Meeting Details Actions
    fun selectMeeting(meeting: MeetingEntity) {
        stopPlayback()
        _selectedMeeting.value = meeting
    }

    fun clearSelectedMeeting() {
        stopPlayback()
        _selectedMeeting.value = null
    }

    fun deleteMeeting(meeting: MeetingEntity) {
        viewModelScope.launch {
            if (_selectedMeeting.value?.id == meeting.id) {
                stopPlayback()
                _selectedMeeting.value = null
            }
            // Delete audio file
            meeting.audioPath?.let { path ->
                val file = File(path)
                if (file.exists()) file.delete()
            }
            repository.deleteById(meeting.id)
            _successMessage.value = "Minuta eliminada correctamente."
        }
    }

    // Dynamic Participant Renaming & Synchronization
    fun renameParticipantInMeeting(meetingId: Int, oldName: String, newName: String) {
        if (newName.isBlank()) return
        
        viewModelScope.launch {
            try {
                val meeting = repository.getMeetingById(meetingId) ?: return@launch
                val moshi = repository2moshi()

                // Parse current lists
                val pType = Types.newParameterizedType(List::class.java, Participant::class.java)
                val tType = Types.newParameterizedType(List::class.java, DialogueLine::class.java)
                val pAdapter: com.squareup.moshi.JsonAdapter<List<Participant>> = moshi.adapter(pType)
                val tAdapter: com.squareup.moshi.JsonAdapter<List<DialogueLine>> = moshi.adapter(tType)

                val participants = pAdapter.fromJson(meeting.participantsJson)?.toMutableList() ?: mutableListOf()
                val transcript = tAdapter.fromJson(meeting.transcriptJson)?.toMutableList() ?: mutableListOf()

                // 1. Update Participant mapping
                val pIndex = participants.indexOfFirst { it.name.trim().lowercase() == oldName.trim().lowercase() }
                if (pIndex != -1) {
                    val p = participants[pIndex]
                    participants[pIndex] = p.copy(name = newName)
                }

                // 2. Update all mentions in the transcript
                for (i in transcript.indices) {
                    val line = transcript[i]
                    if (line.speaker.trim().lowercase() == oldName.trim().lowercase()) {
                        transcript[i] = line.copy(speaker = newName)
                    }
                }

                // 3. Serialize back and update database
                val updatedParticipantsJson = pAdapter.toJson(participants)
                val updatedTranscriptJson = tAdapter.toJson(transcript)

                val updatedMeeting = meeting.copy(
                    participantsJson = updatedParticipantsJson,
                    transcriptJson = updatedTranscriptJson
                )

                repository.update(updatedMeeting)
                
                // Update selected meeting in UI
                if (_selectedMeeting.value?.id == meetingId) {
                    _selectedMeeting.value = updatedMeeting
                }
                
                _successMessage.value = "Hablante renombrado de '$oldName' a '$newName'."
            } catch (e: Exception) {
                Log.e("MeetingViewModel", "Failed to rename participant", e)
                _errorMessage.value = "Error al renombrar participante: ${e.message}"
            }
        }
    }

    // Media Playback Controls
    fun startPlayback(audioPath: String?) {
        if (audioPath == null) {
            _errorMessage.value = "No hay archivo de audio adjunto para reproducir."
            return
        }

        val file = File(audioPath)
        if (!file.exists()) {
            _errorMessage.value = "El archivo de audio de la grabación fue eliminado o no existe."
            return
        }

        stopPlayback()

        try {
            val player = MediaPlayer().apply {
                setDataSource(audioPath)
                prepare()
                start()
            }
            mediaPlayer = player
            _isPlaying.value = true
            _playbackDurationMs.value = player.duration

            // Update Progress Job
            playbackProgressJob = viewModelScope.launch {
                while (_isPlaying.value) {
                    val currentPos = mediaPlayer?.currentPosition ?: 0
                    val duration = _playbackDurationMs.value
                    _playbackPositionMs.value = currentPos
                    if (duration > 0) {
                        _playbackProgress.value = currentPos.toFloat() / duration.toFloat()
                    }
                    delay(100)
                    if (mediaPlayer == null || !(mediaPlayer?.isPlaying ?: false)) {
                        _isPlaying.value = false
                    }
                }
                _playbackProgress.value = 0f
                _playbackPositionMs.value = 0
            }

            player.setOnCompletionListener {
                stopPlayback()
            }
        } catch (e: Exception) {
            Log.e("MeetingViewModel", "Media playback error", e)
            _errorMessage.value = "Error al reproducir el archivo de audio."
            stopPlayback()
        }
    }

    fun pausePlayback() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
                _isPlaying.value = false
                playbackProgressJob?.cancel()
            } else if (mediaPlayer != null) {
                // resume
                mediaPlayer?.start()
                _isPlaying.value = true
                resumePlaybackProgressJob()
            }
        } catch (e: Exception) {
            Log.e("MeetingViewModel", "Pause playback failure", e)
        }
    }

    private fun resumePlaybackProgressJob() {
        playbackProgressJob?.cancel()
        playbackProgressJob = viewModelScope.launch {
            while (_isPlaying.value) {
                val currentPos = mediaPlayer?.currentPosition ?: 0
                val duration = _playbackDurationMs.value
                _playbackPositionMs.value = currentPos
                if (duration > 0) {
                    _playbackProgress.value = currentPos.toFloat() / duration.toFloat()
                }
                delay(100)
            }
        }
    }

    fun stopPlayback() {
        playbackProgressJob?.cancel()
        playbackProgressJob = null
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            // silent
        } finally {
            mediaPlayer = null
            _isPlaying.value = false
            _playbackProgress.value = 0f
            _playbackPositionMs.value = 0
            _playbackDurationMs.value = 0
        }
    }

    // Clear Errors and Successes
    fun clearAlerts() {
        _errorMessage.value = null
        _successMessage.value = null
    }

    private fun repository2moshi() = RetrofitClient.moshi

    override fun onCleared() {
        super.onCleared()
        stopPlayback()
        if (_isRecording.value) {
            audioRecorder.stopRecording()
        }
    }
}
