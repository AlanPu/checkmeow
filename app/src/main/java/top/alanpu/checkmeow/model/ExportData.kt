package top.alanpu.checkmeow.model

import java.time.LocalDate

/**
 * 用于导出和导入的打卡数据模型
 */
data class ExportData(
    val targetPercentage: Float,
    val checkInData: List<CheckInDayData>
)

/**
 * 单日打卡数据
 */
data class CheckInDayData(
    val date: String, // 格式：yyyy-MM-dd
    val isCheckedIn: Boolean,
    val isRestDay: Boolean
)
