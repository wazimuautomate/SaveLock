package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SaveLockAmber
import com.example.ui.theme.SaveLockPrimary
import com.example.ui.theme.SaveLockRed
import com.example.viewmodel.HistoryStatus
import com.example.viewmodel.HistoryViewModel

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Savings History",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }

            // Section: Trend Chart Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Last 30 Days Savings Trend",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Daily goal success / miss rate",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SaveLockPrimary.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Target Met: 82%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SaveLockPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Custom Canvas-drawn Trend Chart
                        val trendPoints = uiState.trendData
                        Canvas(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .testTag("savings_canvas_chart")
                        ) {
                            val width = size.width
                            val height = size.height
                            val spacing = width / (trendPoints.size - 1)

                            // Step 1: Draw chart gridlines (subtle horizontal lines)
                            val gridLines = 4
                            for (i in 0 until gridLines) {
                                val y = height * i / (gridLines - 1)
                                drawLine(
                                    color = Color.Gray.copy(alpha = 0.15f),
                                    start = Offset(0f, y),
                                    end = Offset(width, y),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }

                            // Step 2: Build coordinate paths
                            val pathPoints = trendPoints.mapIndexed { idx, point ->
                                Offset(
                                    x = idx * spacing,
                                    // Invert Y coordinate so 1f is at top of canvas, 0f is at bottom
                                    y = height - (point * (height - 10.dp.toPx())) - 5.dp.toPx()
                                )
                            }

                            // Build the path connecting coordinates
                            val linePath = Path().apply {
                                if (pathPoints.isNotEmpty()) {
                                    moveTo(pathPoints[0].x, pathPoints[0].y)
                                    for (i in 1 until pathPoints.size) {
                                        lineTo(pathPoints[i].x, pathPoints[i].y)
                                    }
                                }
                            }

                            // Build the fill path under the line
                            val fillPath = Path().apply {
                                if (pathPoints.isNotEmpty()) {
                                    moveTo(pathPoints[0].x, height)
                                    for (i in 0 until pathPoints.size) {
                                        lineTo(pathPoints[i].x, pathPoints[i].y)
                                    }
                                    lineTo(pathPoints.last().x, height)
                                    close()
                                }
                            }

                            // Step 3: Draw gradient fill below trend line
                            drawPath(
                                path = fillPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        SaveLockPrimary.copy(alpha = 0.35f),
                                        SaveLockPrimary.copy(alpha = 0.01f)
                                    )
                                )
                            )

                            // Step 4: Draw main trend line
                            drawPath(
                                path = linePath,
                                color = SaveLockPrimary,
                                style = Stroke(
                                    width = 3.dp.toPx(),
                                    miter = 4f,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )

                            // Step 5: Draw nodes for each historical point
                            pathPoints.forEachIndexed { index, point ->
                                val isSaved = trendPoints[index] > 0.5f
                                drawCircle(
                                    color = if (isSaved) SaveLockPrimary else SaveLockRed,
                                    radius = 4.dp.toPx(),
                                    center = point
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = 2.dp.toPx(),
                                    center = point
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "PAST DAYS HISTORY LOG",
                    style = MaterialTheme.typography.labelMedium,
                    color = SaveLockPrimary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
            }

            // Scrollable list items
            items(uiState.historyItems) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("history_item_${item.date.replace(" ", "_")}"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = item.date,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Target: ${item.targetAmount}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Saved: ${item.savedAmount}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (item.status == HistoryStatus.Saved) SaveLockPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Status Badge
                        val (statusBg, statusBorder, statusText, statusLabel, statusIcon) = when (item.status) {
                            HistoryStatus.Saved -> listOf(
                                SaveLockPrimary.copy(alpha = 0.15f),
                                SaveLockPrimary.copy(alpha = 0.4f),
                                SaveLockPrimary,
                                "Saved",
                                Icons.Default.CheckCircle
                            )
                            HistoryStatus.Missed -> listOf(
                                SaveLockRed.copy(alpha = 0.15f),
                                SaveLockRed.copy(alpha = 0.4f),
                                SaveLockRed,
                                "Missed Lock",
                                Icons.Default.Block
                            )
                            HistoryStatus.RecoveryUsed -> listOf(
                                SaveLockAmber.copy(alpha = 0.15f),
                                SaveLockAmber.copy(alpha = 0.4f),
                                SaveLockAmber,
                                "Code Used",
                                Icons.Default.VpnKey
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(statusBg as Color)
                                .border(1.dp, statusBorder as Color, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = statusIcon as ImageVector,
                                    contentDescription = null,
                                    tint = statusText as Color,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = statusLabel as String,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = statusText
                                )
                            }
                        }
                    }
                }
            }

            // Bottom space offset for Bottom Navigation
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}
