package top.alanpu.checkmeow.model

data class MonthStats(
    val totalDays: Int,
    val workdays: Int,
    val checkedInDays: Int,
    val restDays: Int,
    val targetPercentage: Float,
    val currentPercentage: Float,
    val daysToTarget: Int
)