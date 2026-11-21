package com.teamwork.pricehunter.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamwork.pricehunter.core.state.ProgressiveLoadingSnapshot
import com.teamwork.pricehunter.core.state.ServiceStatus

val ProviderPersianDisplayNames = mapOf(
    "digikala" to "دیجی‌کالا",
    "torob" to "ترب",
    "snappshop" to "اسنپ‌شاپ",
    "snappfood" to "اسنپ‌فود",
    "tapsifood" to "تپسی‌فود",
    "okala" to "اکالا"
)

@Composable
fun ProgressiveLoadingBar(
    snapshot: ProgressiveLoadingSnapshot,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("progressive_loading_bar")
    ) {
        if (!snapshot.isComplete && snapshot.totalCount > 0) {
            LinearProgressIndicator(
                progress = { snapshot.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .testTag("progressive_linear_indicator"),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Horizontal status badges
        if (snapshot.statuses.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("provider_status_chips_row"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(snapshot.statuses.entries.toList(), key = { it.key }) { (providerId, status) ->
                    val displayName = ProviderPersianDisplayNames[providerId] ?: providerId
                    ProviderStatusChip(providerId = providerId, name = displayName, status = status)
                }
            }
        }
    }
}

@Composable
fun ProviderStatusChip(
    providerId: String,
    name: String,
    status: ServiceStatus,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, label) = when (status) {
        ServiceStatus.IDLE -> Triple(Color(0xFFEEEEEE), Color.Gray, name)
        ServiceStatus.LOADING -> Triple(Color(0xFFE3F2FD), Color(0xFF1976D2), "$name ⏳")
        ServiceStatus.SUCCESS -> Triple(Color(0xFFE8F5E9), Color(0xFF388E3C), "$name ✓")
        ServiceStatus.FAILED -> Triple(Color(0xFFFFEBEE), Color(0xFFD32F2F), "$name ✕")
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        modifier = modifier
            .height(28.dp)
            .testTag("status_chip_$providerId")
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = textColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
