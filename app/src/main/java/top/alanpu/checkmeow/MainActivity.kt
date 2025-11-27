package top.alanpu.checkmeow

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.alanpu.checkmeow.data.CheckInRepository
import top.alanpu.checkmeow.model.CalendarUtil
import top.alanpu.checkmeow.model.CheckInDayData
import top.alanpu.checkmeow.model.DayData
import top.alanpu.checkmeow.model.ExportData
import top.alanpu.checkmeow.model.MonthStats
import top.alanpu.checkmeow.ui.theme.MyApplicationTheme
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    CheckInApp(context = this)
                }
            }
        }
    }
}

@Composable
fun CheckInApp(context: android.content.Context) {
    // 状态管理
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var targetPercentage by remember { mutableStateOf(40f) }
    var calendar by remember { mutableStateOf(CalendarUtil.generateMonthCalendar(currentMonth)) }
    var stats by remember { mutableStateOf(CalendarUtil.calculateMonthStats(calendar, targetPercentage)) }
    
    // 数据持久化
    val repository = remember { CheckInRepository(context) }
    val coroutineScope = rememberCoroutineScope()
    
    // 从DataStore加载数据
    LaunchedEffect(currentMonth) {
        // 加载目标打卡比例
        val savedPercentage = repository.targetPercentageFlow.first()
        targetPercentage = savedPercentage
        
        // 加载已保存的打卡状态
        val loadedCalendar = CalendarUtil.generateMonthCalendar(currentMonth).map { week ->
            week.map { dayData ->
                dayData?.copy(
                    isCheckedIn = repository.isCheckedIn(dayData.date),
                    isRestDay = repository.isRestDay(dayData.date)
                )
            }
        }
        calendar = loadedCalendar
        stats = CalendarUtil.calculateMonthStats(calendar, targetPercentage)
    }
    
    // 更新统计信息
    fun updateStats() {
        stats = CalendarUtil.calculateMonthStats(calendar, targetPercentage)
    }
    
    // 切换到上一个月
    fun previousMonth() {
        currentMonth = currentMonth.minusMonths(1)
    }
    
    // 切换到下一个月
    fun nextMonth() {
        currentMonth = currentMonth.plusMonths(1)
    }
    
    // 处理日期点击
    fun handleDayClick(dayData: DayData) {
        // 从当前calendar状态中获取最新的dayData对象
        val currentDayData = calendar.flatten().find { it?.date == dayData.date } ?: return
        
        // 如果是休息日，不允许打卡
        if (currentDayData.isRestDay) {
            return
        }
        
        // 创建新的calendar对象，触发UI更新
        val updatedCalendar = calendar.map { week ->
            week.map { d ->
                if (d?.date == dayData.date) {
                    d.copy(isCheckedIn = !d.isCheckedIn)
                } else {
                    d
                }
            }
        }
        calendar = updatedCalendar
        updateStats()
        
        // 保存到DataStore
        coroutineScope.launch {
            repository.saveCheckedInStatus(dayData.date, !currentDayData.isCheckedIn)
        }
    }
    
    // 设置为休息日
    fun setAsRestDay(dayData: DayData) {
        // 从当前calendar状态中获取最新的dayData对象
        val currentDayData = calendar.flatten().find { it?.date == dayData.date } ?: return
        
        // 计算新的状态
        val newRestDayStatus = !currentDayData.isRestDay
        val newCheckedInStatus = if (newRestDayStatus) false else currentDayData.isCheckedIn
        
        // 创建新的calendar对象，触发UI更新
        val updatedCalendar = calendar.map { week ->
            week.map { d ->
                if (d?.date == dayData.date) {
                    d.copy(
                        isRestDay = newRestDayStatus,
                        isCheckedIn = newCheckedInStatus
                    )
                } else {
                    d
                }
            }
        }
        calendar = updatedCalendar
        updateStats()
        
        // 保存到DataStore
        coroutineScope.launch {
            repository.saveRestDayStatus(dayData.date, newRestDayStatus)
            repository.saveCheckedInStatus(dayData.date, newCheckedInStatus)
        }
    }
    
    // 刷新日历
    fun refreshCalendar() {
        coroutineScope.launch {
            val refreshedCalendar = CalendarUtil.generateMonthCalendar(currentMonth).map { week ->
                week.map { dayData ->
                    dayData?.copy(
                        isCheckedIn = repository.isCheckedIn(dayData.date),
                        isRestDay = repository.isRestDay(dayData.date)
                    )
                }
            }
            calendar = refreshedCalendar
            stats = CalendarUtil.calculateMonthStats(calendar, targetPercentage)
        }
    }
    
    // 导出文件选择器
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == ComponentActivity.RESULT_OK) {
            result.data?.data?.let { uri ->
                coroutineScope.launch {
                    try {
                        // 获取所有打卡数据
                        val allData = repository.getAllCheckInData()
                        
                        // 转换为导出格式
                        val checkInDayDataList = allData.map { (date, status) ->
                            CheckInDayData(
                                date = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                                isCheckedIn = status.first,
                                isRestDay = status.second
                            )
                        }
                        
                        val exportData = ExportData(
                            targetPercentage = targetPercentage,
                            checkInData = checkInDayDataList
                        )
                        
                        // 转换为JSON
                        val gson = Gson()
                        val jsonString = gson.toJson(exportData)
                        
                        // 写入文件
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                                outputStream.write(jsonString.toByteArray())
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }
    
    // 导入文件选择器
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == ComponentActivity.RESULT_OK) {
            result.data?.data?.let { uri ->
                coroutineScope.launch {
                    try {
                        // 读取文件内容
                        val jsonString = withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                                reader.readText()
                            }
                        }
                        
                        if (jsonString != null) {
                            // 解析JSON
                            val gson = Gson()
                            val importData = gson.fromJson(jsonString, ExportData::class.java)
                            
                            // 转换为导入格式
                            val checkInDataMap = mutableMapOf<LocalDate, Pair<Boolean, Boolean>>()
                            for (dayData in importData.checkInData) {
                                val date = LocalDate.parse(dayData.date, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                                checkInDataMap[date] = Pair(dayData.isCheckedIn, dayData.isRestDay)
                            }
                            
                            // 导入数据
                            repository.importCheckInData(importData.targetPercentage, checkInDataMap)
                            
                            // 重新加载数据
                            refreshCalendar()
                            
                            // 更新目标比例
                            targetPercentage = importData.targetPercentage
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }
    
    // 导出数据
    fun exportData() {
        coroutineScope.launch {
            try {
                // 保存到文件
                val fileName = "check_in_data_${YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))}.json"
                
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    putExtra(Intent.EXTRA_TITLE, fileName)
                }
                
                exportLauncher.launch(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    // 导入数据
    fun importData() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        importLauncher.launch(intent)
    }
    
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 月份标题和统计信息
        MonthHeader(
            currentMonth = currentMonth,
            stats = stats,
            onPreviousMonth = ::previousMonth,
            onNextMonth = ::nextMonth
        )
        
        // 目标打卡比例滑块
        TargetPercentageSlider(
            targetPercentage = targetPercentage,
            onValueChange = { 
                targetPercentage = it
                updateStats()
                // 保存目标比例到DataStore
                coroutineScope.launch {
                    repository.saveTargetPercentage(it)
                }
            }
        )
        
        // 日历视图
        CalendarView(
            calendar = calendar,
            onDayClick = ::handleDayClick,
            onLongPress = ::setAsRestDay
        )
        
        // 操作按钮
        ActionButtons(
            onExport = ::exportData,
            onImport = ::importData,
            onRefresh = ::refreshCalendar
        )
    }
}

@Composable
fun MonthHeader(currentMonth: YearMonth, stats: MonthStats, onPreviousMonth: () -> Unit, onNextMonth: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 月份标题和切换按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 上一个月按钮
                IconButton(onClick = onPreviousMonth) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.ArrowBack,
                        contentDescription = "上一个月"
                    )
                }
                
                // 月份标题
                Text(
                    text = currentMonth.format(DateTimeFormatter.ofPattern("yyyy年MM月")),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                
                // 下一个月按钮
                IconButton(onClick = onNextMonth) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.ArrowForward,
                        contentDescription = "下一个月"
                    )
                }
            }
            
            // 统计信息
            Text(
                text = "工作日: ${stats.workdays}天 | 已打卡: ${stats.checkedInDays}天 | 休息: ${stats.restDays}天",
                fontSize = 16.sp,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                textAlign = TextAlign.Center
            )
            
            // 达标情况
            Text(
                text = "目标比例: %.1f%% | 已打卡比例: %.1f%% | 距离达标还需: ${stats.daysToTarget}天".format(stats.targetPercentage, stats.currentPercentage),
                fontSize = 16.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = if (stats.currentPercentage >= stats.targetPercentage) {
                    Color.Green
                } else {
                    Color.Red
                }
            )
        }
    }
}

@Composable
fun TargetPercentageSlider(targetPercentage: Float, onValueChange: (Float) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "目标打卡比例:",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${targetPercentage.toInt()}%",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // 进度条
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until 5) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(20.dp)
                            .padding(2.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (i < (targetPercentage / 20)) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.LightGray
                                }
                            )
                    )
                }
            }
            
            // 滑块
            Slider(
                value = targetPercentage,
                onValueChange = { value ->
                    // 确保值是10的倍数
                    val roundedValue = (value / 10 + 0.5f).toInt() * 10f
                    onValueChange(roundedValue)
                },
                valueRange = 10f..100f,
                steps = 9,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun CalendarView(calendar: List<List<DayData?>>, onDayClick: (DayData) -> Unit, onLongPress: (DayData) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 星期标题
            val weekdays = listOf("日", "一", "二", "三", "四", "五", "六")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                weekdays.forEach { weekday ->
                    Text(
                        text = weekday,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            // 日历网格
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(calendar.flatten()) { dayData ->
                    DayItem(
                        dayData = dayData,
                        onDayClick = onDayClick,
                        onLongPress = onLongPress
                    )
                }
            }
        }
    }
}

@Composable
fun DayItem(dayData: DayData?, onDayClick: (DayData) -> Unit, onLongPress: (DayData) -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .padding(2.dp)
            .clip(CircleShape)
            .background(
                when {
                    dayData == null -> Color.Transparent
                    dayData.isRestDay -> Color.Yellow.copy(alpha = 0.3f)
                    dayData.isCheckedIn -> Color.Green.copy(alpha = 0.3f)
                    else -> Color.Transparent
                }
            )
            .border(
                width = if (dayData?.isCheckedIn == true) 2.dp else 0.dp,
                color = if (dayData?.isCheckedIn == true) Color.Green else Color.Transparent,
                shape = CircleShape
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        dayData?.let(onDayClick)
                    },
                    onLongPress = {
                        dayData?.let(onLongPress)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (dayData != null) {
            Text(
                text = dayData.date.dayOfMonth.toString(),
                fontSize = 14.sp,
                fontWeight = if (dayData.date == LocalDate.now()) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    dayData.isRestDay -> Color.Gray
                    dayData.isCheckedIn -> Color.Green
                    else -> Color.Black
                }
            )
        }
    }
}

@Composable
fun ActionButtons(onExport: () -> Unit, onImport: () -> Unit, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Button(
            onClick = onExport,
            modifier = Modifier.weight(1f).padding(8.dp)
        ) {
            Text(text = "导出")
        }
        
        Button(
            onClick = onImport,
            modifier = Modifier.weight(1f).padding(8.dp)
        ) {
            Text(text = "导入")
        }
        
        Button(
            onClick = onRefresh,
            modifier = Modifier.weight(1f).padding(8.dp)
        ) {
            Text(text = "刷新")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CheckInAppPreview() {
    MyApplicationTheme {
        // Preview doesn't have access to context, so we'll use a mock implementation
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "打卡软件预览",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "在实际设备上运行以查看完整功能",
                    fontSize = 16.sp,
                    color = Color.Gray
                )
            }
        }
    }
}