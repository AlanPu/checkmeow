package top.alanpu.checkmeow.model

import java.time.LocalDate

data class DayData(
    val date: LocalDate,
    var isWorkday: Boolean = true,
    var isCheckedIn: Boolean = false,
    var isRestDay: Boolean = false
)