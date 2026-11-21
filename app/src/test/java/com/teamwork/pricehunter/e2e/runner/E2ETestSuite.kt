package com.teamwork.pricehunter.e2e.runner

import com.teamwork.pricehunter.e2e.tier1_coverage.*
import com.teamwork.pricehunter.e2e.tier2_boundary.*
import com.teamwork.pricehunter.e2e.tier3_combination.*
import com.teamwork.pricehunter.e2e.tier4_application.*
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    // Tier 1: Feature Coverage (56 tests)
    Tier1NormalizerTest::class,
    Tier1UnitExtractorTest::class,
    Tier1AccessoryFilterTest::class,
    Tier1VendorMatcherTest::class,
    Tier1SecurityNetworkTest::class,
    Tier1RemoteConfigTest::class,
    Tier1ProviderSearchTest::class,

    // Tier 2: Boundary & Corner Cases (52 tests)
    Tier2IntelligenceBoundaryTest::class,
    Tier2SecurityNetworkBoundaryTest::class,
    Tier2RemoteConfigBoundaryTest::class,
    Tier2ProviderFaultTest::class,

    // Tier 3: Cross-Feature Combinations (12 tests)
    Tier3CrossFeatureTest::class,

    // Tier 4: Real-World Scenarios (8 tests)
    Tier4RealWorldScenariosTest::class
)
class E2ETestSuite {
    companion object {
        fun resolveClasses(tier: String): List<Class<*>> {
            return when (tier.lowercase()) {
                "1", "tier1" -> listOf(
                    Tier1NormalizerTest::class.java,
                    Tier1UnitExtractorTest::class.java,
                    Tier1AccessoryFilterTest::class.java,
                    Tier1VendorMatcherTest::class.java,
                    Tier1SecurityNetworkTest::class.java,
                    Tier1RemoteConfigTest::class.java,
                    Tier1ProviderSearchTest::class.java
                )
                "2", "tier2" -> listOf(
                    Tier2IntelligenceBoundaryTest::class.java,
                    Tier2SecurityNetworkBoundaryTest::class.java,
                    Tier2RemoteConfigBoundaryTest::class.java,
                    Tier2ProviderFaultTest::class.java
                )
                "3", "tier3" -> listOf(
                    Tier3CrossFeatureTest::class.java
                )
                "4", "tier4" -> listOf(
                    Tier4RealWorldScenariosTest::class.java
                )
                else -> listOf(
                    Tier1NormalizerTest::class.java,
                    Tier1UnitExtractorTest::class.java,
                    Tier1AccessoryFilterTest::class.java,
                    Tier1VendorMatcherTest::class.java,
                    Tier1SecurityNetworkTest::class.java,
                    Tier1RemoteConfigTest::class.java,
                    Tier1ProviderSearchTest::class.java,
                    Tier2IntelligenceBoundaryTest::class.java,
                    Tier2SecurityNetworkBoundaryTest::class.java,
                    Tier2RemoteConfigBoundaryTest::class.java,
                    Tier2ProviderFaultTest::class.java,
                    Tier3CrossFeatureTest::class.java,
                    Tier4RealWorldScenariosTest::class.java
                )
            }
        }
    }
}
