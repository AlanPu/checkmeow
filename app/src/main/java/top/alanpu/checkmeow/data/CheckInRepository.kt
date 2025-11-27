package top.alanpu.checkmeow.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "check_in_preferences")

class CheckInRepository(private val context: Context) {
    
    companion object {
        // Preferences keys
        private val TARGET_PERCENTAGE = floatPreferencesKey("target_percentage")
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        
        // Helper functions to create keys
        private fun checkedInKey(date: LocalDate) = booleanPreferencesKey("checked_in_${date.format(DATE_FORMATTER)}")
        private fun restDayKey(date: LocalDate) = booleanPreferencesKey("rest_day_${date.format(DATE_FORMATTER)}")
        
        // 从键名中提取日期
        private fun extractDateFromKey(key: String): LocalDate? {
            val pattern = Regex("(checked_in|rest_day)_(\\d{4}-\\d{2}-\\d{2})")
            val matchResult = pattern.find(key)
            return matchResult?.groupValues?.get(2)?.let {
                LocalDate.parse(it, DATE_FORMATTER)
            }
        }
    }
    
    /**
     * 获取目标打卡比例
     */
    val targetPercentageFlow: Flow<Float> = context.dataStore.data
        .map { prefs ->
            prefs[TARGET_PERCENTAGE] ?: 40f
        }
    
    /**
     * 保存目标打卡比例
     */
    suspend fun saveTargetPercentage(percentage: Float) {
        context.dataStore.edit { prefs ->
            prefs[TARGET_PERCENTAGE] = percentage
        }
    }
    
    /**
     * 获取指定日期是否已打卡
     */
    suspend fun isCheckedIn(date: LocalDate): Boolean {
        val prefs = context.dataStore.data.first()
        return prefs[checkedInKey(date)] ?: false
    }
    
    /**
     * 保存指定日期的打卡状态
     */
    suspend fun saveCheckedInStatus(date: LocalDate, isCheckedIn: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[checkedInKey(date)] = isCheckedIn
        }
    }
    
    /**
     * 获取指定日期是否为休息日
     */
    suspend fun isRestDay(date: LocalDate): Boolean {
        val prefs = context.dataStore.data.first()
        return prefs[restDayKey(date)] ?: false
    }
    
    /**
     * 保存指定日期的休息日状态
     */
    suspend fun saveRestDayStatus(date: LocalDate, isRestDay: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[restDayKey(date)] = isRestDay
        }
    }
    
    /**
     * 获取所有打卡数据
     */
    suspend fun getAllCheckInData(): Map<LocalDate, Pair<Boolean, Boolean>> {
        val prefs = context.dataStore.data.first()
        val checkInData = mutableMapOf<LocalDate, Pair<Boolean, Boolean>>()
        
        // 提取所有打卡和休息日数据
        val dateStatusMap = mutableMapOf<LocalDate, MutableMap<String, Boolean>>()
        
        prefs.asMap().forEach { (key, value) ->
            if (value is Boolean) {
                val keyName = key.name
                val date = extractDateFromKey(keyName)
                if (date != null) {
                    val statusMap = dateStatusMap.getOrPut(date) { mutableMapOf() }
                    if (keyName.startsWith("checked_in_")) {
                        statusMap["checkedIn"] = value
                    } else if (keyName.startsWith("rest_day_")) {
                        statusMap["restDay"] = value
                    }
                }
            }
        }
        
        // 转换为统一格式
        dateStatusMap.forEach { (date, statusMap) ->
            checkInData[date] = Pair(
                statusMap["checkedIn"] ?: false,
                statusMap["restDay"] ?: false
            )
        }
        
        return checkInData
    }
    
    /**
     * 导入打卡数据
     */
    suspend fun importCheckInData(targetPercentage: Float, checkInData: Map<LocalDate, Pair<Boolean, Boolean>>) {
        context.dataStore.edit { prefs ->
            // 保存目标打卡比例
            prefs[TARGET_PERCENTAGE] = targetPercentage
            
            // 保存所有打卡数据
            checkInData.forEach { (date, status) ->
                prefs[checkedInKey(date)] = status.first
                prefs[restDayKey(date)] = status.second
            }
        }
    }
    
    /**
     * 清除所有数据（用于测试）
     */
    suspend fun clearAllData() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }
}