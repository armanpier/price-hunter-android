package com.teamwork.pricehunter.e2e.runner

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.notification.Failure

class TierRunListenerTest {

    private lateinit var listener: TierRunListener

    @Before
    fun setUp() {
        listener = TierRunListener()
    }

    @Test
    fun testTierResolutionByPackageAndClassName() {
        val descTier1 = Description.createTestDescription("com.teamwork.pricehunter.e2e.tier1_coverage.Tier1NormalizerTest", "testNorm")
        val descTier2 = Description.createTestDescription("com.teamwork.pricehunter.e2e.tier2_boundary.Tier2IntelligenceBoundaryTest", "testBound")
        val descTier3 = Description.createTestDescription("com.teamwork.pricehunter.e2e.tier3_combination.Tier3CrossFeatureTest", "testCross")
        val descTier4 = Description.createTestDescription("com.teamwork.pricehunter.e2e.tier4_application.Tier4RealWorldScenariosTest", "testReal")
        val descOther = Description.createTestDescription("com.teamwork.pricehunter.other.SomeOtherTest", "testOther")

        assertEquals("Tier 1", TierRunListener.resolveTier(descTier1))
        assertEquals("Tier 2", TierRunListener.resolveTier(descTier2))
        assertEquals("Tier 3", TierRunListener.resolveTier(descTier3))
        assertEquals("Tier 4", TierRunListener.resolveTier(descTier4))
        assertNull(TierRunListener.resolveTier(descOther))
    }

    @Test
    fun testSuccessfulExecutionAggregation() {
        val desc1 = Description.createTestDescription("com.teamwork.pricehunter.e2e.tier1_coverage.Tier1NormalizerTest", "test1")
        val desc2 = Description.createTestDescription("com.teamwork.pricehunter.e2e.tier1_coverage.Tier1NormalizerTest", "test2")

        listener.testStarted(desc1)
        listener.testFinished(desc1)
        listener.testStarted(desc2)
        listener.testFinished(desc2)

        val counts = listener.getTierCounts()
        assertEquals(Pair(2, 2), counts["Tier 1"])
        assertEquals(Pair(0, 0), counts["Tier 2"])
        assertEquals(Pair(0, 0), counts["Tier 3"])
        assertEquals(Pair(0, 0), counts["Tier 4"])
    }

    @Test
    fun testFailureAggregationWithoutDoubleCounting() {
        val desc1 = Description.createTestDescription("com.teamwork.pricehunter.e2e.tier2_boundary.Tier2BoundaryTest", "testFail")

        listener.testStarted(desc1)
        listener.testFailure(Failure(desc1, AssertionError("Expected true")))
        // Simulate duplicate failure event (e.g., teardown exception)
        listener.testFailure(Failure(desc1, RuntimeException("Teardown error")))
        listener.testFinished(desc1)

        val counts = listener.getTierCounts()
        assertEquals(Pair(0, 1), counts["Tier 2"])
        assertEquals(1, listener.getFailedCount("Tier 2"))
        assertEquals(1, listener.getExecutedCount("Tier 2"))
    }
}
