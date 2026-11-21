package com.teamwork.pricehunter.e2e.runner

import com.teamwork.pricehunter.e2e.mock.MockServerManager
import org.junit.runner.JUnitCore
import org.junit.runner.Result
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val tierArg = args.firstOrNull { it.startsWith("--tier=") }?.substringAfter("--tier=") ?: "all"
    val reportDirArg = args.firstOrNull { it.startsWith("--report-dir=") }?.substringAfter("--report-dir=") ?: "test-reports"

    println("================================================================================")
    println("        NATIVE ANDROID PRICE HUNTER — 4-TIER E2E TEST RUNNER                    ")
    println("================================================================================")
    println("Target Tier: $tierArg")
    println("Report Directory: $reportDirArg")

    val mockServer = MockServerManager()
    try {
        println("[1/4] Starting In-Process MockWebServer...")
        mockServer.start()
        println("      Mock server running at: ${mockServer.baseUrl}")

        println("[2/4] Resolving Test Suite Classes...")
        val testClasses = E2ETestSuite.resolveClasses(tierArg)
        println("      Discovered ${testClasses.size} test suite classes.")

        println("[3/4] Executing 4-Tier Test Runner...")
        val startTime = System.currentTimeMillis()
        val junitCore = JUnitCore()
        val tierListener = TierRunListener()
        junitCore.addListener(tierListener)
        val result = junitCore.run(*testClasses.toTypedArray())
        val executionDuration = System.currentTimeMillis() - startTime

        println("[4/4] Generating E2E Test Reports...")
        val reportGenerator = E2ETestReportGenerator(File(reportDirArg))
        val dynamicTierCounts = tierListener.getTierCounts()
        reportGenerator.generateReports(result, executionDuration, tierArg, dynamicTierCounts)

        printConsoleSummary(result, executionDuration)

        if (result.wasSuccessful()) {
            println("\n>>> ALL E2E TESTS PASSED (100% SUCCESS) <<<")
            exitProcess(0)
        } else {
            println("\n>>> E2E TEST FAILURES DETECTED <<<")
            result.failures.forEachIndexed { index, failure ->
                println("${index + 1}. ${failure.testHeader}: ${failure.message}")
            }
            exitProcess(1)
        }
    } catch (e: Exception) {
        println("\n>>> TEST RUNNER CRITICAL ERROR: ${e.message} <<<")
        e.printStackTrace()
        exitProcess(2)
    } finally {
        mockServer.stop()
    }
}

fun printConsoleSummary(result: Result, durationMs: Long) {
    val total = result.runCount
    val failed = result.failureCount
    val ignored = result.ignoreCount
    val passed = total - failed

    println("\n--------------------------------------------------------------------------------")
    println("| METRIC                     | VALUE                                           |")
    println("--------------------------------------------------------------------------------")
    println(String.format("| %-26s | %-47d |", "Total Tests Executed", total))
    println(String.format("| %-26s | %-47d |", "Tests Passed", passed))
    println(String.format("| %-26s | %-47d |", "Tests Failed", failed))
    println(String.format("| %-26s | %-47d |", "Tests Ignored/Skipped", ignored))
    println(String.format("| %-26s | %-47s |", "Execution Time", "${durationMs} ms (${durationMs / 1000.0}s)"))
    val passRateStr = if (total > 0) "${String.format("%.2f", (passed.toDouble() / total) * 100)}%" else "0.0%"
    println(String.format("| %-26s | %-47s |", "Pass Rate", passRateStr))
    println("--------------------------------------------------------------------------------")
}
