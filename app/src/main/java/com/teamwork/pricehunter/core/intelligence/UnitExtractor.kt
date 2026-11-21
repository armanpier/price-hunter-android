package com.teamwork.pricehunter.core.intelligence

import kotlin.math.roundToLong

data class UnitExtractionResult(
    val baseQuantity: Double,
    val standardUnit: StandardUnit,
    val packCount: Int = 1,
    val unitPriceToman: Long? = null,
    val displayText: String
)

enum class StandardUnit(val persianPerUnitLabel: String) {
    GRAM("کیلوگرم"),
    MILLILITER("لیتر"),
    PIECE("عدد")
}

object UnitExtractor {
    // Units alternation: longest match first, with lookahead protecting Persian characters
    private const val WEIGHT_UNITS = "کیلوگرمی|کیلوگرم|کیلو[\\s\\u200C]+گرم|کیلویی|کیلو|kg|kilo|گرمی|گرم|gr|g"
    private const val VOLUME_UNITS = "میلی[\\s\\u200C]?لیتر|میلیلیتر|سی[\\s\\u200C]?سی|سیسی|ml|cc|لیتری|لیتر|lit|l"
    private const val COUNT_UNITS = "عددی|تایی|تکه|قلم|عدد|تای|برگ|برگی"

    // Single measurement: captures (1) amount, (2) unit
    private val MEASUREMENT_REGEX = Regex(
        "(\\d+(?:[.,،]\\d+|[\\s\\u200C]*/[\\s\\u200C]*\\d+)?)[\\s\\u200C]*($WEIGHT_UNITS|$VOLUME_UNITS)(?!\\p{InArabic})",
        RegexOption.IGNORE_CASE
    )

    // Pack multiplier: captures (1) count
    private val PACK_MULTIPLIER_REGEX = Regex(
        "(?:بسته|پک|باکس)?[\\s\\u200C]*(\\d+)[\\s\\u200C]*(?:$COUNT_UNITS)(?!\\p{InArabic})",
        RegexOption.IGNORE_CASE
    )

    // Compound inline e.g. "6x200ml" or "6 در 200 گرم"
    private val COMPOUND_PACK_REGEX = Regex(
        "(\\d+)[\\s\\u200C]*(?:[xX×*]|در)[\\s\\u200C]*(\\d+(?:[.,،]\\d+|[\\s\\u200C]*/[\\s\\u200C]*\\d+)?)[\\s\\u200C]*($WEIGHT_UNITS|$VOLUME_UNITS)(?!\\p{InArabic})",
        RegexOption.IGNORE_CASE
    )

    // Reverse compound inline e.g. "200ml x 6"
    private val REVERSE_COMPOUND_PACK_REGEX = Regex(
        "(\\d+(?:[.,،]\\d+|[\\s\\u200C]*/[\\s\\u200C]*\\d+)?)[\\s\\u200C]*($WEIGHT_UNITS|$VOLUME_UNITS)[\\s\\u200C]*(?:[xX×*]|در)[\\s\\u200C]*(\\d+)(?!\\p{InArabic})",
        RegexOption.IGNORE_CASE
    )

    private fun parseQuantity(raw: String): Double {
        val clean = raw.trim().replace('\u200C', ' ').replace(" ", "")
        if (clean.contains('/')) {
            val parts = clean.split('/')
            if (parts.size == 2) {
                val num = parts[0].toDoubleOrNull() ?: 0.0
                val den = parts[1].toDoubleOrNull() ?: 1.0
                if (den != 0.0) {
                    return num / den
                }
            }
        }
        val decimalFixed = clean.replace('،', '.').replace(',', '.')
        return decimalFixed.toDoubleOrNull() ?: 0.0
    }

    fun extract(title: String, priceToman: Long? = null): UnitExtractionResult? {
        val normalized = PersianNormalizer.normalize(title)
        if (normalized.isBlank()) return null

        // 1. Check compound pack regex: "6x200ml" or "6 در 200 میلی لیتر"
        val compoundMatch = COMPOUND_PACK_REGEX.find(normalized)
        if (compoundMatch != null) {
            val packCount = compoundMatch.groupValues[1].toIntOrNull() ?: 1
            val singleAmount = parseQuantity(compoundMatch.groupValues[2])
            val unitStr = compoundMatch.groupValues[3]
            return buildResult(singleAmount, unitStr, packCount, priceToman)
        }

        // 2. Check reverse compound pack regex: "200ml x 6"
        val reverseMatch = REVERSE_COMPOUND_PACK_REGEX.find(normalized)
        if (reverseMatch != null) {
            val singleAmount = parseQuantity(reverseMatch.groupValues[1])
            val unitStr = reverseMatch.groupValues[2]
            val packCount = reverseMatch.groupValues[3].toIntOrNull() ?: 1
            return buildResult(singleAmount, unitStr, packCount, priceToman)
        }

        // 3. Check individual measurement + optional separate pack multiplier
        val measurementMatch = MEASUREMENT_REGEX.find(normalized)
        if (measurementMatch != null) {
            val singleAmount = parseQuantity(measurementMatch.groupValues[1])
            val unitStr = measurementMatch.groupValues[2]

            // Search for pack multiplier elsewhere in the title
            val packMatch = PACK_MULTIPLIER_REGEX.find(normalized)
            val packCount = packMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1

            return buildResult(singleAmount, unitStr, packCount, priceToman)
        }

        // 4. Check count-only items e.g. "بسته ۱۰ عددی"
        val countMatch = PACK_MULTIPLIER_REGEX.find(normalized)
        if (countMatch != null) {
            val count = countMatch.groupValues[1].toIntOrNull() ?: 1
            if (count > 0) {
                val unitPrice = if (priceToman != null && priceToman > 0L) (priceToman.toDouble() / count).roundToLong() else null
                return UnitExtractionResult(
                    baseQuantity = count.toDouble(),
                    standardUnit = StandardUnit.PIECE,
                    packCount = count,
                    unitPriceToman = unitPrice,
                    displayText = "$count عدد"
                )
            }
        }

        return null
    }

    private fun buildResult(
        singleAmount: Double,
        unitStr: String,
        packCount: Int,
        totalPriceToman: Long?
    ): UnitExtractionResult {
        val (normalizedSingle, standardUnit) = convertToStandard(singleAmount, unitStr)
        val totalBaseQuantity = normalizedSingle * packCount

        val unitPrice = if (totalPriceToman != null && totalPriceToman > 0L && totalBaseQuantity > 0) {
            when (standardUnit) {
                StandardUnit.GRAM -> ((totalPriceToman.toDouble() / totalBaseQuantity) * 1000.0).roundToLong()
                StandardUnit.MILLILITER -> ((totalPriceToman.toDouble() / totalBaseQuantity) * 1000.0).roundToLong()
                StandardUnit.PIECE -> (totalPriceToman.toDouble() / totalBaseQuantity).roundToLong()
            }
        } else null

        val singleStr = formatNumber(singleAmount)

        val displayText = when (standardUnit) {
            StandardUnit.GRAM -> {
                val totalKg = totalBaseQuantity / 1000.0
                val kgStr = formatNumber(totalKg)
                val isSmallUnit = unitStr.replace("\u200C", "").replace(" ", "").lowercase() in listOf("گرم", "گرمی", "gr", "g")
                if (packCount > 1) {
                    "$kgStr کیلوگرم ($packCount × $singleStr $unitStr)"
                } else if (normalizedSingle >= 1000.0 && isSmallUnit) {
                    "$kgStr کیلوگرم"
                } else {
                    "$singleStr $unitStr"
                }
            }
            StandardUnit.MILLILITER -> {
                val totalL = totalBaseQuantity / 1000.0
                val lStr = formatNumber(totalL)
                val isSmallUnit = unitStr.replace("\u200C", "").replace(" ", "").lowercase() in listOf("میلیلیتر", "سیسی", "ml", "cc")
                if (packCount > 1) {
                    "$lStr لیتر ($packCount × $singleStr $unitStr)"
                } else if (normalizedSingle >= 1000.0 && isSmallUnit) {
                    "$lStr لیتر"
                } else {
                    "$singleStr $unitStr"
                }
            }
            StandardUnit.PIECE -> "$packCount عدد"
        }

        return UnitExtractionResult(
            baseQuantity = totalBaseQuantity,
            standardUnit = standardUnit,
            packCount = packCount,
            unitPriceToman = unitPrice,
            displayText = displayText
        )
    }

    private fun formatNumber(value: Double): String {
        return if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            value.toString()
        }
    }

    private fun convertToStandard(amount: Double, unitStr: String): Pair<Double, StandardUnit> {
        val u = unitStr.replace("\u200C", "").replace(" ", "").lowercase()
        return when {
            u in listOf("کیلوگرم", "کیلوگرمی", "کیلو", "کیلویی", "kg", "kilo") ->
                Pair(amount * 1000.0, StandardUnit.GRAM)
            u in listOf("گرم", "گرمی", "gr", "g") ->
                Pair(amount, StandardUnit.GRAM)
            u in listOf("لیتر", "لیتری", "lit", "l") ->
                Pair(amount * 1000.0, StandardUnit.MILLILITER)
            u in listOf("میلیلیتر", "سیسی", "ml", "cc") ->
                Pair(amount, StandardUnit.MILLILITER)
            else ->
                Pair(amount, StandardUnit.PIECE)
        }
    }
}
