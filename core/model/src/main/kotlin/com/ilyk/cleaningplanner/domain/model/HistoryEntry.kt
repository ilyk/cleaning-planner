package com.ilyk.cleaningplanner.domain.model

import com.ilyk.cleaningplanner.core.model.TaskStatus
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class HistoryEntry(
    val taskId: String,
    val date: LocalDate,
    val status: TaskStatus,
    val durationMin: Int?,
    val note: String?,
    val origin: HistoryOrigin = HistoryOrigin.App,
    val deviceId: String? = null,
    val source: String? = null,
    val createdAt: Long? = null
)

@Serializable
enum class HistoryOrigin {
    App,
    Qr,
    Ocr,
    Voice,
    Other
}
