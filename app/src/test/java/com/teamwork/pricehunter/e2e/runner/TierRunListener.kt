package com.teamwork.pricehunter.e2e.runner

import org.junit.runner.Description
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunListener
import java.util.concurrent.ConcurrentHashMap

/**
 * Dynamic JUnit RunListener that intercepts test execution events
 * and aggregates executed, passed, failed, and ignored test counts
 * per E2E tier (Tier 1 through Tier 4).
 */
class TierRunListener : RunListener() {

    private val executedTests = ConcurrentHashMap<String, MutableSet<String>>()
    private val failedTests = ConcurrentHashMap<String, MutableSet<String>>()
    private val ignoredTests = ConcurrentHashMap<String, MutableSet<String>>()

    init {
        reset()
    }

    fun reset() {
        listOf(TIER_1, TIER_2, TIER_3, TIER_4).forEach { tier ->
            executedTests[tier] = ConcurrentHashMap.newKeySet()
            failedTests[tier] = ConcurrentHashMap.newKeySet()
            ignoredTests[tier] = ConcurrentHashMap.newKeySet()
        }
    }

    override fun testStarted(description: Description) {
        val tier = resolveTier(description) ?: return
        val testKey = getTestKey(description)
        executedTests[tier]?.add(testKey)
    }

    override fun testFailure(failure: Failure) {
        val desc = failure.description
        val tier = resolveTier(desc) ?: return
        val testKey = getTestKey(desc)
        failedTests[tier]?.add(testKey)
        // Ensure class-level failures (e.g., @BeforeClass) are registered in executedTests
        executedTests[tier]?.add(testKey)
    }

    override fun testIgnored(description: Description) {
        val tier = resolveTier(description) ?: return
        val testKey = getTestKey(description)
        ignoredTests[tier]?.add(testKey)
    }

    /**
     * Returns a map of tier name to Pair(passedCount, totalExecutedCount).
     * If a tier was not executed, returns Pair(0, 0).
     */
    fun getTierCounts(): Map<String, Pair<Int, Int>> {
        return listOf(TIER_1, TIER_2, TIER_3, TIER_4).associateWith { tier ->
            val total = executedTests[tier]?.size ?: 0
            val failed = failedTests[tier]?.size ?: 0
            val passed = maxOf(0, total - failed)
            Pair(passed, total)
        }
    }

    fun getExecutedCount(tier: String): Int = executedTests[tier]?.size ?: 0
    fun getFailedCount(tier: String): Int = failedTests[tier]?.size ?: 0
    fun getPassedCount(tier: String): Int = maxOf(0, getExecutedCount(tier) - getFailedCount(tier))
    fun getIgnoredCount(tier: String): Int = ignoredTests[tier]?.size ?: 0

    companion object {
        const val TIER_1 = "Tier 1"
        const val TIER_2 = "Tier 2"
        const val TIER_3 = "Tier 3"
        const val TIER_4 = "Tier 4"

        fun resolveTier(description: Description?): String? {
            val className = description?.className ?: return null
            return when {
                className.contains(".tier1_coverage.") || className.contains("Tier1") -> TIER_1
                className.contains(".tier2_boundary.") || className.contains("Tier2") -> TIER_2
                className.contains(".tier3_combination.") || className.contains("Tier3") -> TIER_3
                className.contains(".tier4_application.") || className.contains("Tier4") -> TIER_4
                else -> null
            }
        }

        fun getTestKey(description: Description): String {
            val className = description.className ?: "UnknownClass"
            val methodName = description.methodName ?: "classLevel"
            return "$className#$methodName"
        }
    }
}
