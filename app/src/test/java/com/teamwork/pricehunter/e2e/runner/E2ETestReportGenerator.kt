package com.teamwork.pricehunter.e2e.runner

import org.junit.runner.Result
import org.junit.runner.notification.Failure
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class E2ETestReportGenerator(private val outputDir: File) {

    init {
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
    }

    fun generateReports(
        result: Result,
        executionDurationMs: Long,
        tier: String = "all",
        tierCounts: Map<String, Pair<Int, Int>> = emptyMap() // tierName -> (passed, total)
    ) {
        generateMarkdownReport(result, executionDurationMs, tier, tierCounts)
        generateJsonSummary(result, executionDurationMs, tier, tierCounts)
        generateJUnitXml(result, executionDurationMs)
    }

    private fun generateMarkdownReport(
        result: Result,
        executionDurationMs: Long,
        tier: String,
        tierCounts: Map<String, Pair<Int, Int>>
    ) {
        val mdFile = File(outputDir, "e2e_report.md")
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

        val total = result.runCount
        val failed = result.failureCount
        val passed = total - failed
        val passRate = if (total > 0) (passed.toDouble() / total) * 100.0 else 0.0
        val status = if (result.wasSuccessful()) "PASSED" else "FAILED"

        val sb = StringBuilder()
        sb.appendLine("# E2E Test Execution Summary Report")
        sb.appendLine("- **Timestamp**: $timestamp")
        sb.appendLine("- **Target Tier**: $tier")
        sb.appendLine("- **Total Tests**: $total")
        sb.appendLine("- **Passed**: $passed (${String.format(Locale.US, "%.1f", passRate)}%)")
        sb.appendLine("- **Failed**: $failed")
        sb.appendLine("- **Duration**: ${String.format(Locale.US, "%.2f", executionDurationMs / 1000.0)}s")
        sb.appendLine("- **Status**: **$status**")
        sb.appendLine()

        sb.appendLine("## Tier Breakdown")
        sb.appendLine("| Tier | Name | Target | Executed | Passed | Failed | Status |")
        sb.appendLine("|---|---|---|---|---|---|---|")

        val tierDetails = listOf(
            Triple("Tier 1", "Feature Coverage", 56),
            Triple("Tier 2", "Boundary & Corner", 52),
            Triple("Tier 3", "Cross-Feature Combinations", 12),
            Triple("Tier 4", "Real-World Scenarios", 8)
        )

        for ((tKey, tName, target) in tierDetails) {
            val (tPassed, tTotal) = tierCounts[tKey] ?: Pair(0, 0)
            val tFailed = tTotal - tPassed
            val tStatus = if (tFailed == 0 && tTotal > 0) "PASS" else if (tTotal == 0) "SKIPPED" else "FAIL"
            sb.appendLine("| $tKey | $tName | >=$target | $tTotal | $tPassed | $tFailed | $tStatus |")
        }
        sb.appendLine()

        if (result.failureCount > 0) {
            sb.appendLine("## Failures")
            result.failures.forEachIndexed { idx, failure ->
                sb.appendLine("### ${idx + 1}. ${failure.testHeader}")
                sb.appendLine("```")
                sb.appendLine(failure.message ?: "No error message")
                sb.appendLine(failure.trace)
                sb.appendLine("```")
            }
        } else {
            sb.appendLine("## Verification Result")
            sb.appendLine("All $passed executed tests passed with 100% success rate. Exit code 0.")
        }

        mdFile.writeText(sb.toString())
    }

    private fun generateJsonSummary(
        result: Result,
        executionDurationMs: Long,
        tier: String,
        tierCounts: Map<String, Pair<Int, Int>>
    ) {
        val jsonFile = File(outputDir, "e2e_summary.json")
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

        val total = result.runCount
        val failed = result.failureCount
        val passed = total - failed
        val passRate = if (total > 0) (passed.toDouble() / total) * 100.0 else 0.0

        val failureItems = result.failures.joinToString(",") { f ->
            val header = f.testHeader.replace("\"", "\\\"")
            val msg = (f.message ?: "").replace("\"", "\\\"").replace("\n", " ")
            """{"test":"$header","message":"$msg"}"""
        }

        val tierItems = tierCounts.entries.joinToString(",") { (k, v) ->
            """"$k":{"passed":${v.first},"total":${v.second}}"""
        }

        val json = """
{
  "timestamp": "$timestamp",
  "tier": "$tier",
  "totalTests": $total,
  "passed": $passed,
  "failed": $failed,
  "passRatePercent": ${String.format(Locale.US, "%.2f", passRate)},
  "durationMs": $executionDurationMs,
  "status": "${if (result.wasSuccessful()) "PASSED" else "FAILED"}",
  "tiers": {$tierItems},
  "failures": [$failureItems]
}
""".trimIndent()

        jsonFile.writeText(json)
    }

    private fun generateJUnitXml(
        result: Result,
        executionDurationMs: Long
    ) {
        val xmlFile = File(outputDir, "junit_report.xml")
        val total = result.runCount
        val failed = result.failureCount
        val seconds = executionDurationMs / 1000.0

        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<testsuite name="com.teamwork.pricehunter.e2e.E2ETestSuite" tests="$total" failures="$failed" errors="0" skipped="${result.ignoreCount}" time="$seconds">""")

        for (f in result.failures) {
            val header = f.testHeader
            val trace = f.trace.replace("<", "&lt;").replace(">", "&gt;").replace("&", "&amp;")
            val msg = (f.message ?: "").replace("<", "&lt;").replace(">", "&gt;").replace("&", "&amp;")
            sb.appendLine("""  <testcase classname="E2ETest" name="$header">""")
            sb.appendLine("""    <failure message="$msg">$trace</failure>""")
            sb.appendLine("""  </testcase>""")
        }

        sb.appendLine("""</testsuite>""")
        xmlFile.writeText(sb.toString())
    }
}
