package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ConsumptionRecordEntity
import com.example.ui.FormatUtils
import com.example.ui.MonitorUiState
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DeepCyan
import com.example.ui.theme.SuccessEmerald

@Composable
fun StatsScreen(
    uiState: MonitorUiState,
    modifier: Modifier = Modifier
) {
    val records = uiState.records

    // Calculate daily, weekly, monthly stats
    val now = System.currentTimeMillis()
    val oneDayMs = 24 * 60 * 60 * 1000L
    val oneWeekMs = 7 * oneDayMs
    val oneMonthMs = 30 * oneDayMs

    val todayBytes = remember(records) {
        records.filter { it.timestamp >= now - oneDayMs }.sumOf { it.totalBytes }
    }
    val weekBytes = remember(records) {
        records.filter { it.timestamp >= now - oneWeekMs }.sumOf { it.totalBytes }
    }
    val monthBytes = remember(records) {
        records.filter { it.timestamp >= now - oneMonthMs }.sumOf { it.totalBytes }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top 3 Summary Cards
        item {
            PeriodMetricsRow(
                todayBytes = todayBytes,
                weekBytes = weekBytes,
                monthBytes = monthBytes
            )
        }

        // Consumption Bar Chart
        item {
            ConsumptionChartCard(records = records.takeLast(7))
        }

        // Quota Depletion Forecast Card
        item {
            DepletionAnalysisCard(
                usedBytes = uiState.usedBytes,
                totalBytes = uiState.totalQuotaBytes,
                dailyAvg = uiState.dailyAverageBytes,
                daysRemaining = uiState.forecastDaysRemaining,
                depletionDate = uiState.forecastDepletionDate
            )
        }

        // Historical Records Title
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "السجل",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "سجل الاستهلاك المسجل (${records.size} سجل)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Historical Log Items
        if (records.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "لا توجد سجلات استهلاك محفوظة بعد.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(records.reversed()) { record ->
                ConsumptionRecordRow(record = record)
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PeriodMetricsRow(
    todayBytes: Long,
    weekBytes: Long,
    monthBytes: Long
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MetricCard(title = "اليوم", bytes = todayBytes, modifier = Modifier.weight(1f))
        MetricCard(title = "هذا الأسبوع", bytes = weekBytes, modifier = Modifier.weight(1f))
        MetricCard(title = "هذا الشهر", bytes = monthBytes, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun MetricCard(
    title: String,
    bytes: Long,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            val (num, unit) = FormatUtils.formatBytesShort(bytes)
            Text(
                text = num,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ConsumptionChartCard(records: List<ConsumptionRecordEntity>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("consumption_chart_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.BarChart,
                    contentDescription = "الرسم البياني",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "رسم بياني لاستهلاك الأيام الأخيرة",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (records.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "لا توجد بيانات كافية لرسم المخطط",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val maxBytes = records.maxOfOrNull { it.totalBytes }?.coerceAtLeast(1024 * 1024L) ?: (1024 * 1024L)
                val primaryColor = MaterialTheme.colorScheme.primary
                val gridColor = MaterialTheme.colorScheme.surfaceVariant

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height - 24.dp.toPx()
                        val barCount = records.size
                        val barSpacing = 12.dp.toPx()
                        val totalSpacing = barSpacing * (barCount + 1)
                        val barWidth = ((canvasWidth - totalSpacing) / barCount).coerceIn(16.dp.toPx(), 44.dp.toPx())

                        // Subtle horizontal gridlines
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, canvasHeight * 0.25f),
                            end = Offset(canvasWidth, canvasHeight * 0.25f),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, canvasHeight * 0.75f),
                            end = Offset(canvasWidth, canvasHeight * 0.75f),
                            strokeWidth = 1.dp.toPx()
                        )

                        records.forEachIndexed { index, record ->
                            val heightRatio = (record.totalBytes.toFloat() / maxBytes.toFloat()).coerceIn(0.08f, 1f)
                            val barHeight = canvasHeight * heightRatio
                            val x = barSpacing + index * (barWidth + barSpacing)
                            val y = canvasHeight - barHeight

                            // Gradient Bar
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(CyberCyan, DeepCyan),
                                    startY = y,
                                    endY = canvasHeight
                                ),
                                topLeft = Offset(x, y),
                                size = Size(barWidth, barHeight),
                                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                            )
                        }
                    }
                }

                // Bar Labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    records.forEach { record ->
                        val label = record.dateString.takeLast(5) // MM-dd
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DepletionAnalysisCard(
    usedBytes: Long,
    totalBytes: Long,
    dailyAvg: Long,
    daysRemaining: Int,
    depletionDate: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ShowChart,
                    contentDescription = "التحليل",
                    tint = SuccessEmerald,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "تحليل وتوقع استهلاك الباقة",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "بناءً على متوسط الاستهلاك الفعلي المسجل (${FormatUtils.formatBytes(dailyAvg)}/يوم)، يُتوقع أن تكفي باقتك حتى $depletionDate ($daysRemaining يومًا متبقيًا).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            val ratio = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (ratio > 0.85f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
private fun ConsumptionRecordRow(record: ConsumptionRecordEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = record.dateString,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "تنزيل: ${FormatUtils.formatBytes(record.downloadBytes)} • رفع: ${FormatUtils.formatBytes(record.uploadBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = FormatUtils.formatBytes(record.totalBytes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
