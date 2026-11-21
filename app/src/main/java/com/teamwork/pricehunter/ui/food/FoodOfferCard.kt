package com.teamwork.pricehunter.ui.food

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.teamwork.pricehunter.core.provider.FoodVendorDishOffer
import java.text.NumberFormat
import java.util.Locale

@Composable
fun FoodOfferCard(
    offer: FoodVendorDishOffer,
    modifier: Modifier = Modifier,
    onCardClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val cost = offer.costBreakdown

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("food_card_${offer.id}")
            .clickable {
                if (onCardClick != null) {
                    onCardClick()
                } else {
                    launchFoodDeepLink(context, offer.originalUrl)
                }
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header: Dish Name, Vendor Title, Provider Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = offer.dishName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("food_card_title")
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = offer.vendorName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("food_card_vendor")
                    )
                }

                // Provider Badge
                val isSnappFood = offer.providerName.contains("اسنپ")
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isSnappFood) Color(0xFFFFEBF2) else Color(0xFFFFF3E0),
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .testTag("food_card_provider_badge")
                ) {
                    Text(
                        text = offer.providerName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isSnappFood) Color(0xFFE91E63) else Color(0xFFFF6F00),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // Itemized Cost Breakdown
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("food_card_cost_breakdown"),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CostRow(label = "قیمت غذا", value = formatFoodToman(cost.foodPriceToman))

                if (cost.discountToman > 0) {
                    CostRow(
                        label = "تخفیف",
                        value = "- " + formatFoodToman(cost.discountToman),
                        valueColor = Color(0xFF2E7D32)
                    )
                }

                CostRow(
                    label = "هزینه بسته‌بندی",
                    value = if (cost.packagingFeeToman == 0L) "رایگان" else formatFoodToman(cost.packagingFeeToman)
                )

                CostRow(
                    label = "هزینه پیک / ارسال",
                    value = if (cost.deliveryFeeToman == 0L) "رایگان" else formatFoodToman(cost.deliveryFeeToman)
                )

                if (cost.serviceFeeToman > 0) {
                    CostRow(label = "هزینه خدمات", value = formatFoodToman(cost.serviceFeeToman))
                }

                CostRow(label = "مالیات و عوارض (۱۰٪)", value = formatFoodToman(cost.taxToman))
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // Total Delivered Cost
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "مجموع هزینه تحویل:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = formatFoodToman(cost.totalCostToman),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("food_card_total_cost")
                )
            }

            // Minimum Order Threshold Warning Banner
            if (cost.minOrderWarningToman != null && cost.minOrderWarningToman > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFFF8E1),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("food_card_min_order_warning")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFF57F17),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "حداقل مبلغ سفارش: ${formatFoodToman(cost.minOrderWarningToman)} دیگر تا تکمیل حداقل سبد",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF5D4037)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CostRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = valueColor
        )
    }
}

fun formatFoodToman(amount: Long): String {
    val formatter = NumberFormat.getNumberInstance(Locale("fa", "IR"))
    return "${formatter.format(amount)} تومان"
}

private fun launchFoodDeepLink(context: Context, url: String) {
    if (url.isBlank()) return
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
    } catch (_: Exception) {}
}
