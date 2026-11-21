package com.teamwork.pricehunter.ui.debug

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamwork.pricehunter.core.network.DebugNetworkCapturePool
import com.teamwork.pricehunter.core.network.NetworkCaptureEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugNetworkCaptureView(
    capturePool: DebugNetworkCapturePool,
    onDismiss: () -> Unit
) {
    var captures by remember { mutableStateOf(capturePool.getCaptures()) }
    var selectedEntryId by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("debug_network_capture_view")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "گزارش شبکه (Debug Capture) — ${captures.size} درخواست",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row {
                    TextButton(
                        onClick = {
                            capturePool.clear()
                            captures = capturePool.getCaptures()
                        },
                        modifier = Modifier.testTag("debug_capture_clear_button")
                    ) {
                        Text("پاکسازی")
                    }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("debug_capture_close_button")
                    ) {
                        Text("بستن")
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Entries List
            if (captures.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "هیچ لاگ شبکه‌ای ثبت نشده است",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("debug_captures_list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(captures.reversed(), key = { it.id }) { entry ->
                        CaptureEntryCard(
                            entry = entry,
                            onClick = {
                                selectedEntryId = if (selectedEntryId == entry.id) null else entry.id
                            },
                            isExpanded = selectedEntryId == entry.id
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptureEntryCard(
    entry: NetworkCaptureEntry,
    onClick: () -> Unit,
    isExpanded: Boolean
) {
    val statusColor = when (entry.statusCode) {
        in 200..299 -> Color(0xFF2E7D32)
        in 300..399 -> Color(0xFFF57C00)
        else -> Color(0xFFD32F2F)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("capture_entry_${entry.id}")
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = entry.method,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = statusColor.copy(alpha = 0.15f),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = if (entry.statusCode == 0) "ERR" else "${entry.statusCode}",
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "${entry.durationMs}ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = entry.url,
                style = MaterialTheme.typography.bodySmall,
                maxLines = if (isExpanded) Int.MAX_VALUE else 1,
                fontSize = 11.sp
            )

            if (isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                Text("هدرهای درخواست (Masked):", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                entry.requestHeaders.forEach { (k, v) ->
                    Text("$k: $v", fontSize = 10.sp, color = Color.DarkGray)
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text("هدرهای پاسخ (Masked):", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                entry.responseHeaders.forEach { (k, v) ->
                    Text("$k: $v", fontSize = 10.sp, color = Color.DarkGray)
                }

                if (!entry.responseSnippet.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("پیام پاسخ:", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                    Text(entry.responseSnippet, fontSize = 10.sp, color = Color.DarkGray)
                }
            }
        }
    }
}
