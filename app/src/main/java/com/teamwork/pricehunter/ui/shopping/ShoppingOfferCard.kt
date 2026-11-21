package com.teamwork.pricehunter.ui.shopping

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.unit.sp
import com.teamwork.pricehunter.core.provider.ShoppingOffer
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ShoppingOfferCard(
    offer: ShoppingOffer,
    isOutlier: Boolean = false,
    modifier: Modifier = Modifier,
    onCardClick: (() -> Unit)? = null
) {
    val context = LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("shopping_card_${offer.id}")
            .clickable {
                if (onCardClick != null) {
                    onCardClick()
                } else {
                    launchShoppingDeepLink(context, offer.originalUrl)
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
            // Top Row: Title & Provider Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = offer.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("shopping_card_title")
                )

                // Provider Badge
                val (badgeBg, badgeText) = getProviderBadgeColors(offer.providerName)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeBg,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .testTag("shopping_card_provider_badge")
                ) {
                    Text(
                        text = offer.providerName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Middle Row: Seller & Rating
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!offer.sellerName.isNullOrBlank()) {
                    Text(
                        text = "فروشنده: ${offer.sellerName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("shopping_card_seller")
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                if (offer.ratingOutOf5 != null && offer.ratingOutOf5 > 0f) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.testTag("shopping_card_rating")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "امتیاز",
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = String.format(Locale.US, "%.1f", offer.ratingOutOf5),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Statistical Outlier Price Warning Badge (< 20% of median market price)
            if (isOutlier) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFFF8E1),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("shopping_card_outlier_badge")
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
                            text = "قیمت بسیار پایین‌تر از میانگین بازار (<۲۰٪ میانه) — احتمال لوازم جانبی یا فیک",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF795548),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // Bottom Row: Availability & Price (ranked strictly by merchandise price)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (offer.isAvailable) "موجود در انبار" else "ناموجود",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (offer.isAvailable) Color(0xFF2E7D32) else Color(0xFFC62828),
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = formatShoppingPrice(offer.priceToman),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("shopping_card_price")
                )
            }
        }
    }
}

private fun getProviderBadgeColors(providerName: String): Pair<Color, Color> {
    return when {
        providerName.contains("دیجی") -> Pair(Color(0xFFFFEBEE), Color(0xFFD32F2F))
        providerName.contains("ترب") -> Pair(Color(0xFFE3F2FD), Color(0xFF1976D2))
        providerName.contains("اسنپ") -> Pair(Color(0xFFE8F5E9), Color(0xFF2E7D32))
        else -> Pair(Color(0xFFF5F5F5), Color(0xFF616161))
    }
}

fun formatShoppingPrice(amount: Long): String {
    val formatter = NumberFormat.getNumberInstance(Locale("fa", "IR"))
    return "${formatter.format(amount)} تومان"
}

private fun launchShoppingDeepLink(context: Context, url: String) {
    if (url.isBlank()) return
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
    } catch (_: Exception) {}
}
