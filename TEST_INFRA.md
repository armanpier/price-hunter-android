# E2E Test Infrastructure: Native Android Price Hunter

## 1. Test Philosophy & Principles
- **Opaque-Box Testing**: All test suites interact with the application through public API boundaries, interface contracts, and observable outputs (HTTP network requests/responses, normalized models, and return states), never inspecting private internal variables.
- **Requirement-Driven**: Every test case is derived directly from `ORIGINAL_REQUEST.md` and `PROJECT.md § Feature Inventory`.
- **Deterministic & Hermetic Network Layer**: Because live domestic Iranian endpoints are subject to CDN IP geoblocking (HTTP 403 on VPN/foreign IPs), Torob anti-bot challenges (HTTP 490 ArCaptcha), and transient rate limits, tests utilize an in-process, OS-level TCP `MockWebServer` (`okhttp3.mockwebserver.MockWebServer`). Real HTTP sockets, headers, and payloads are exercised without external network flakiness.
- **Pure JVM Standalone Execution**: AGP's `:app:testDebugUnitTest` executes 100% locally on the host JVM (JDK 17). It requires zero Android emulators, devices, or adb instances (`isReturnDefaultValues = true`), allowing instant execution in developer and CI environments.
- **Read-Only Safety Guarantee**: Mock and client providers exclusively execute search and quote-validation endpoints. No order placement or payment checkout routes are ever wired or mocked, adhering strictly to Requirement R4.
- **Methodology**: Systematic 4-tier design:
  - **Tier 1**: Category-Partition positive equivalence testing ($\ge 5$ tests per feature group).
  - **Tier 2**: Boundary Value Analysis (BVA), extremes, zero/negative pricing, and fault injection ($\ge 5$ tests per feature group).
  - **Tier 3**: Pairwise Combinatorial Testing across pipeline stages.
  - **Tier 4**: Realistic Real-World Workload Testing (end-to-end user journeys).

---

## 2. Feature Inventory & Coverage Mapping

| # | Feature | Source Requirement | Tier 1 (Min) | Tier 2 (Min) | Tier 3 | Tier 4 |
|---|---------|-------------------|:------------:|:------------:|:------:|:------:|
| 1 | `PersianNormalizer` (Chars, Diacritics, ZWNJ, Digits) | ORIGINAL_REQUEST §R2 | 6 | 5 | ✓ | ✓ |
| 2 | `UnitExtractor` (Weight, Volume, Packs, Unit Price) | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ | ✓ |
| 3 | `AccessoryAndOutlierFilter` (Keywords, Intent, Median, Outliers) | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ | ✓ |
| 4 | `FoodVendorMatcher` (Haversine <150m, Stop Words, Similarity >0.8) | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ | ✓ |
| 5 | `SecureAuthStorage` (Keystore AES256_GCM, Zero-Knowledge) | ORIGINAL_REQUEST §R3 | 5 | 5 | ✓ | ✓ |
| 6 | `NetworkGuard` (TRANSPORT_VPN, Domestic 403, Timeouts) | ORIGINAL_REQUEST §R3 | 5 | 5 | ✓ | ✓ |
| 7 | `RemoteConfigManager` (Asset Loading, ETag, Schema Drift) | ORIGINAL_REQUEST §R3 | 5 | 5 | ✓ | ✓ |
| 8 | Pluggable Provider Abstraction (`BaseProvider` & Okala Extensibility) | ORIGINAL_REQUEST §R1 | 5 | 5 | ✓ | ✓ |
| 9 | Merchandise Providers (Digikala v2, Torob v4, SnappShop v1) | ORIGINAL_REQUEST §R1 | 5 | 5 | ✓ | ✓ |
| 10 | Food Providers (SnappFood GPS & Cost Formula, TapsiFood Quote) | ORIGINAL_REQUEST §R1 | 5 | 6 | ✓ | ✓ |
| 11 | Parallel Search Engine (`SupervisorJob`, Error Isolation, Streaming) | ORIGINAL_REQUEST §R4 | 5 | 6 | ✓ | ✓ |
| **Total** | **All 11 Feature Groups** | | **56** | **52** | **12** | **8** |

**Grand Total Automated Tests**: **128 Tests** (exceeding minimum threshold of 125).

---

## 3. Test Architecture & Harness

### 3.1 Test Directory Layout
```
price_hunter/
├── app/
│   └── src/
│       └── test/
│           └── java/
│               └── com/
│                   └── teamwork/
│                       └── pricehunter/
│                           └── e2e/
│                               ├── mock/
│                               │   ├── MockScenario.kt           # NORMAL, GEOBLOCK_403, WAF_490, TIMEOUT, 500, SCHEMA_DRIFT
│                               │   ├── MockServerManager.kt      # MockWebServer lifecyle & dispatcher
│                               │   └── ProviderFixtures.kt       # Authentic JSON payloads for all 5 platforms
│                               ├── runner/
│                               │   ├── E2ETestRunner.kt          # Standalone JUnit runner CLI entry point
│                               │   ├── E2ETestSuite.kt           # JUnit test suite aggregator for Tiers 1-4
│                               │   └── E2ETestReportGenerator.kt # Markdown, JUnit XML, and JSON report generator
│                               ├── tier1_coverage/
│                               │   ├── Tier1NormalizerTest.kt
│                               │   ├── Tier1UnitExtractorTest.kt
│                               │   ├── Tier1AccessoryFilterTest.kt
│                               │   ├── Tier1VendorMatcherTest.kt
│                               │   ├── Tier1SecurityNetworkTest.kt
│                               │   ├── Tier1RemoteConfigTest.kt
│                               │   └── Tier1ProviderSearchTest.kt
│                               ├── tier2_boundary/
│                               │   ├── Tier2IntelligenceBoundaryTest.kt
│                               │   ├── Tier2SecurityNetworkBoundaryTest.kt
│                               │   ├── Tier2RemoteConfigBoundaryTest.kt
│                               │   └── Tier2ProviderFaultTest.kt
│                               ├── tier3_combination/
│                               │   └── Tier3CrossFeatureTest.kt
│                               └── tier4_application/
│                                   └── Tier4RealWorldScenariosTest.kt
├── scripts/
│   └── run_e2e_tests.py                                          # CLI wrapper for local and CI invocation
├── test-reports/
│   ├── e2e_report.md                                             # Formatted Markdown report
│   ├── e2e_summary.json                                          # Machine-readable test execution metrics
│   └── junit_report.xml                                          # Standard JUnit XML report
├── TEST_INFRA.md                                                 # Test infrastructure specification (this document)
└── TEST_READY.md                                                 # Test suite readiness publication & verification signal
```

### 3.2 Mock Server & Dynamic Scenario Dispatcher
The test suite utilizes `MockServerManager` backed by `okhttp3.mockwebserver.MockWebServer`:
- Dynamic scenario switching per provider (`setScenario(providerId, scenario)`):
  - `NORMAL`: Authentic HTTP 200 responses with realistic product catalogs, vendor menus, and delivered cost breakdowns.
  - `GEOBLOCK_403`: Simulates Sotoon/ArvanCloud domestic CDN geoblock when VPN is active.
  - `WAF_490`: Simulates Torob ArCaptcha rate-limiting challenge.
  - `SERVER_ERROR_500`: Simulates backend server crashes.
  - `TIMEOUT`: Simulates delayed responses exceeding client read timeout ($>10\text{s}$).
  - `SCHEMA_DRIFT`: Simulates unexpected JSON payloads (missing keys, renamed objects, type changes).
  - `EMPTY_RESULTS`: Simulates valid 200 responses with zero search results.

### 3.3 Test Execution Commands & Pass/Fail Semantics
- **Canonical Execution Command**:
  ```powershell
  .\gradlew.bat :app:testDebugUnitTest --tests "com.teamwork.pricehunter.e2e.**" --info
  ```
- **CLI Python Runner Command**:
  ```powershell
  python scripts/run_e2e_tests.py --tier all
  ```
- **Targeted Tier Execution**:
  ```powershell
  .\gradlew.bat :app:testDebugUnitTest --tests "com.teamwork.pricehunter.e2e.tier1_coverage.*"
  .\gradlew.bat :app:testDebugUnitTest --tests "com.teamwork.pricehunter.e2e.tier2_boundary.*"
  .\gradlew.bat :app:testDebugUnitTest --tests "com.teamwork.pricehunter.e2e.tier3_combination.*"
  .\gradlew.bat :app:testDebugUnitTest --tests "com.teamwork.pricehunter.e2e.tier4_application.*"
  ```
- **Exit Code Semantics**:
  - `Exit Code 0`: 100% of executed tests passed successfully.
  - `Exit Code 1`: One or more test assertions failed or encountered unexpected exceptions.
  - `Exit Code 2`: Harness/environment initialization error (e.g. port collision, build script error).

---

## 4. Real-World Application Scenarios (Tier 4)

| # | Scenario Title | Features Exercised | Problem Domain & Flow Description | Complexity |
|---|----------------|-------------------|-----------------------------------|------------|
| 1 | **Dairy Multi-Pack Price Comparison** | F1, F2, F3, F9, F11 | User queries `"شیر کم چرب کاله ۲۰۰ میلی‌لیتر بسته ۶ عددی"`. Persian normalization strips diacritics, UnitExtractor calculates $6 \times 200 = 1200\text{ml}$ and derives unit price (Toman/L). Parallel search queries Digikala (Rials converted to Toman), Torob, and SnappShop. Results ranked strictly in ascending order of merchandise price. | High |
| 2 | **Smartphone & Accessory Disambiguation** | F1, F3, F9, F11 | User queries `"سامسونگ S24 Ultra"`. Search returns phone offers (68M Toman) and silicone cases (250k Toman). Accessory filter identifies cases using 25+ keyword lexicon, isolating them into collapsible secondary section. Flags clone phone (1.5M Toman) as low-price outlier ($<20\%$ median). | High |
| 3 | **Pizza Delivery Delivered Cost Comparison** | F1, F4, F10, F11 | User searches `"پیتزا مخصوص"` at GPS `(35.7733, 51.4185)`. SnappFood and TapsiFood queried concurrently. FoodVendorMatcher identifies matching branch ($<150\text{m}$, similarity $>0.8$). Full delivered cost computed: $\text{food} - \text{discount} + \text{packaging} + \text{delivery} + \text{service} + \text{tax}$. Cheapest delivered option ranked first. | High |
| 4 | **VPN Geoblock Detection & Recovery** | F6, F9, F11 | User initiates shopping search with VPN active. SnappShop returns HTTP 403 (Sotoon CDN geoblock). `NetworkGuard` detects `TRANSPORT_VPN` + 403, emitting actionable Persian advisory banner. Digikala and Torob continue without interruption (`SupervisorJob` fault isolation). | Medium |
| 5 | **Remote Config Dynamic Refresh & Drift** | F7, F9 | App launches using bundled `providers_config.json`. Remote config manager polls endpoint, receives HTTP 200 with updated Torob endpoint and unknown schema drift keys. Manager updates in-memory routing safely without crash or recompile. | Medium |
| 6 | **SnappFood Minimum Order Threshold Warning** | F10, F11 | User searches for single side dish (120,000 Toman) where restaurant minimum order is 150,000 Toman. Delivered cost breakdown calculated correctly, and `minOrderWarningToman: 30000` is flagged for UI banner. | Medium |
| 7 | **Pluggable Grocery Provider Search (Okala)** | F8, F11 | Okala provider registered into `SearchEngineRepository`. Parallel search dispatches query across Digikala, Torob, SnappShop, and Okala. Okala offers seamlessly integrate into sorted result stream. | High |
| 8 | **Timeout Error Isolation During Parallel Search** | F9, F11 | Torob endpoint hangs ($>10\text{s}$ timeout). SupervisorJob isolates failure; Digikala and SnappShop results stream progressively to UI in $<500\text{ms}$. Multi-stage progress bar displays Digikala=Success, SnappShop=Success, Torob=Failed. | High |

---

## 5. Coverage Thresholds & Acceptance Criteria
- **Tier 1 (Feature Coverage)**: $\ge 5$ tests per feature group across all 11 feature groups (Target: $\ge 55$, Specified: **56**).
- **Tier 2 (Boundary & Corner Cases)**: $\ge 5$ tests per feature group covering extremes, zeros, negatives, diacritics, malformed payloads, and WAF challenges (Target: $\ge 55$, Specified: **52**).
- **Tier 3 (Cross-Feature Combinations)**: Pairwise and multi-module integration tests verifying module interactions (Target: $\ge 10$, Specified: **12**).
- **Tier 4 (Real-World Application Scenarios)**: Multi-stage realistic end-user user journeys (Target: $\ge 5$, Specified: **8**).
- **Total Test Count**: Target $\ge 125$ tests; Final Specification: **128 tests**.
- **Pass Threshold**: **100% of all 128 tests must pass with Exit Code 0**. Zero test failures or skipped critical tests.
- **Publication Signal**: When all 128 tests compile, execute, and pass 100%, `TEST_READY.md` will be published at project root and state directory to signal readiness for Milestone M4.
