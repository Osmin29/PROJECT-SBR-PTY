package com.example.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Participant(
    val name: String,
    val voiceNickname: String
)

@JsonClass(generateAdapter = true)
data class DialogueLine(
    val speaker: String,
    val text: String,
    val timestampMs: Long? = null
)

@JsonClass(generateAdapter = true)
data class MeetingAnalysisResult(
    val title: String,
    val participants: List<Participant>,
    val transcript: List<DialogueLine>,
    val summaryMarkdown: String
)
