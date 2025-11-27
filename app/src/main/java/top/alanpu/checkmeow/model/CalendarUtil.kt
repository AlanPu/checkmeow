package top.alanpu.checkmeow.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class CalendarUtil {
    companion object {
        /**
         * 生成指定月份的日历数据
         */
        fun generateMonthCalendar(yearMonth: YearMonth): List<List<DayData?>> {
            val daysInMonth = yearMonth.lengthOfMonth()
            val firstDayOfMonth = yearMonth.atDay(1)
            val firstDayOfWeek = firstDayOfMonth.dayOfWeek.value
            
            val calendar = mutableListOf<List<DayData?>>()
            var week = mutableListOf<DayData?>()
            
            // 添加月初的空白日期
            for (i in 1 until firstDayOfWeek) {
                week.add(null)
            }
            
            // 添加当月日期
            for (day in 1..daysInMonth) {
                val date = yearMonth.atDay(day)
                val isWeekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
                week.add(DayData(
                    date = date,
                    isWorkday = !isWeekend
                ))
                
                if (week.size == 7) {
                    calendar.add(week)
                    week = mutableListOf()
                }
            }
            
            // 添加月末的空白日期
            if (week.isNotEmpty()) {
                while (week.size < 7) {
                    week.add(null)
                }
                calendar.add(week)
            }
            
            return calendar
        }
        
        /**
         * 计算月度统计信息
         */
        fun calculateMonthStats(calendar: List<List<DayData?>>, targetPercentage: Float): MonthStats {
            val allDays = calendar.flatten().filterNotNull()
            val totalDays = allDays.size
            val workdays = allDays.count { it.isWorkday && !it.isRestDay }
            val checkedInDays = allDays.count { it.isCheckedIn }
            val restDays = allDays.count { it.isRestDay }
            
            val currentPercentage = if (workdays > 0) {
                (checkedInDays.toFloat() / workdays) * 100
            } else {
                0f
            }
            
            val targetDays = (workdays * targetPercentage / 100).toInt()
            val daysToTarget = maxOf(0, targetDays - checkedInDays)
            
            return MonthStats(
                totalDays = totalDays,
                workdays = workdays,
                checkedInDays = checkedInDays,
                restDays = restDays,
                targetPercentage = targetPercentage,
                currentPercentage = currentPercentage,
                daysToTarget = daysToTarget
            )
        }
        
        /**
         * 获取当前月份
         */
        fun getCurrentMonth(): YearMonth {
            return YearMonth.now()
        }
    }
}