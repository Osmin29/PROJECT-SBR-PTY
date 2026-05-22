package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "meetings")
data class MeetingEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val timestamp: Long = System.currentTimeMillis(),
    val audioPath: String?,
    val durationMs: Long,
    val transcriptJson: String,
    val summaryMarkdown: String,
    val participantsJson: String
)
