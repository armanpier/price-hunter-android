package com.teamwork.pricehunter.e2e.mock

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class MockServerManager {
    private val server = MockWebServer()
    private val providerScenarios = ConcurrentHashMap<String, MockScenario>()

    val baseUrl: String
        get() = server.url("/").toString().removeSuffix("/")

    val port: Int
        get() = server.port

    fun start() {
        providerScenarios["digikala"] = MockScenario.NORMAL
        providerScenarios["torob"] = MockScenario.NORMAL
        providerScenarios["snappshop"] = MockScenario.NORMAL
        providerScenarios["snappfood"] = MockScenario.NORMAL
        providerScenarios["tapsifood"] = MockScenario.NORMAL
        providerScenarios["okala"] = MockScenario.NORMAL

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: ""
                val method = request.method ?: "GET"

                return when {
                    // Digikala Discovery v2 Search
                    path.startsWith("/discovery/api/v2/search") -> {
                        handleDigikala(request, providerScenarios["digikala"] ?: MockScenario.NORMAL)
                    }
                    // Torob v4 Search & Sellers
                    path.startsWith("/v4/base-product/search") -> {
                        handleTorobSearch(request, providerScenarios["torob"] ?: MockScenario.NORMAL)
                    }
                    path.startsWith("/v4/base-product/sellers") -> {
                        handleTorobSellers(request, providerScenarios["torob"] ?: MockScenario.NORMAL)
                    }
                    // SnappShop v1 Search
                    path.startsWith("/search/v1") && method == "POST" -> {
                        handleSnappShop(request, providerScenarios["snappshop"] ?: MockScenario.NORMAL)
                    }
                    // SnappFood Endpoints
                    path.startsWith("/search/api/v1/desktop/vendors-list") -> {
                        handleSnappFoodVendors(request, providerScenarios["snappfood"] ?: MockScenario.NORMAL)
                    }
                    path.startsWith("/search/api/v5/search") -> {
                        handleSnappFoodDishes(request, providerScenarios["snappfood"] ?: MockScenario.NORMAL)
                    }
                    path.startsWith("/mobile/v2/restaurant/details/state") -> {
                        handleSnappFoodState(request, providerScenarios["snappfood"] ?: MockScenario.NORMAL)
                    }
                    path.startsWith("/mobile/v2/restaurant/details/dynamic") -> {
                        handleSnappFoodMenu(request, providerScenarios["snappfood"] ?: MockScenario.NORMAL)
                    }
                    // TapsiFood Endpoints
                    path.startsWith("/v1/api/search-discovery/Search/product-search") -> {
                        handleTapsiFoodDishes(request, providerScenarios["tapsifood"] ?: MockScenario.NORMAL)
                    }
                    path.startsWith("/v1/api/search-discovery/Search/vendor-search") -> {
                        handleTapsiFoodVendors(request, providerScenarios["tapsifood"] ?: MockScenario.NORMAL)
                    }
                    path.contains("/v3/api/Cart/") && path.endsWith("/validate") -> {
                        handleTapsiFoodValidate(request, providerScenarios["tapsifood"] ?: MockScenario.NORMAL)
                    }
                    // Okala Endpoint
                    path.startsWith("/okala/search") || path.startsWith("/search/okala") -> {
                        handleOkala(request, providerScenarios["okala"] ?: MockScenario.NORMAL)
                    }
                    // Remote Config Endpoint
                    path.startsWith("/remote-config/providers_config.json") -> {
                        val ifNoneMatch = request.getHeader("If-None-Match")
                        if (ifNoneMatch == "\"v1-hash\"" || ifNoneMatch == "\"v2-etag\"") {
                            MockResponse().setResponseCode(304)
                        } else {
                            MockResponse().setResponseCode(200)
                                .setHeader("Content-Type", "application/json; charset=utf-8")
                                .setHeader("ETag", "\"v2-etag\"")
                                .setBody(ProviderFixtures.REMOTE_CONFIG_JSON)
                        }
                    }
                    else -> MockResponse().setResponseCode(404).setBody("""{"error":"Not Found"}""")
                }
            }
        }
        server.start()
    }

    fun setScenario(providerId: String, scenario: MockScenario) {
        providerScenarios[providerId.lowercase()] = scenario
    }

    fun resetScenarios() {
        providerScenarios.keys.forEach { providerScenarios[it] = MockScenario.NORMAL }
    }

    fun stop() {
        try {
            server.shutdown()
        } catch (_: Exception) {}
    }

    private fun handleDigikala(request: RecordedRequest, scenario: MockScenario): MockResponse {
        return when (scenario) {
            MockScenario.NORMAL -> {
                val query = request.requestUrl?.queryParameter("q") ?: ""
                val body = if (query.contains("شیر") || query.contains("dairy")) {
                    ProviderFixtures.DIGIKALA_DAIRY_RESPONSE
                } else {
                    ProviderFixtures.DIGIKALA_SAMSUNG_RESPONSE
                }
                MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "application/json; charset=utf-8")
                    .setBody(body)
            }
            MockScenario.GEOBLOCK_403 -> MockResponse().setResponseCode(403)
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .setBody("<html><title>403 Forbidden</title><body>Access Denied by Digikala CDN</body></html>")
            MockScenario.SERVER_ERROR_500 -> MockResponse().setResponseCode(500)
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody("""{"status":500,"message":"Internal Server Error"}""")
            MockScenario.TIMEOUT -> MockResponse().setBodyDelay(12, TimeUnit.SECONDS)
                .setResponseCode(200).setBody("{}")
            MockScenario.SCHEMA_DRIFT -> MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody(ProviderFixtures.DIGIKALA_SCHEMA_DRIFT_RESPONSE)
            MockScenario.EMPTY_RESULTS -> MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody("""{"status":200,"data":{"widgets":[]}}""")
            else -> MockResponse().setResponseCode(200).setBody("{}")
        }
    }

    private fun handleTorobSearch(request: RecordedRequest, scenario: MockScenario): MockResponse {
        return when (scenario) {
            MockScenario.NORMAL -> MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody(ProviderFixtures.TOROB_IPHONE_SEARCH_RESPONSE)
            MockScenario.WAF_490 -> MockResponse().setResponseCode(490)
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .setBody(ProviderFixtures.TOROB_WAF_490_HTML)
            MockScenario.GEOBLOCK_403 -> MockResponse().setResponseCode(403)
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .setBody("<html><body>403 Forbidden</body></html>")
            MockScenario.SERVER_ERROR_500 -> MockResponse().setResponseCode(500)
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody("""{"status":500,"error":"Internal Server Error"}""")
            MockScenario.TIMEOUT -> MockResponse().setBodyDelay(12, TimeUnit.SECONDS)
                .setResponseCode(200).setBody("{}")
            MockScenario.SCHEMA_DRIFT -> MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody("""{"unknown_format": true, "items": []}""")
            MockScenario.EMPTY_RESULTS -> MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody("""{"count":0,"results":[]}""")
        }
    }

    private fun handleTorobSellers(request: RecordedRequest, scenario: MockScenario): MockResponse {
        return when (scenario) {
            MockScenario.WAF_490 -> MockResponse().setResponseCode(490)
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .setBody(ProviderFixtures.TOROB_WAF_490_HTML)
            else -> MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody(ProviderFixtures.TOROB_SELLERS_RESPONSE)
        }
    }

    private fun handleSnappShop(request: RecordedRequest, scenario: MockScenario): MockResponse {
        return when (scenario) {
            MockScenario.NORMAL -> MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody(ProviderFixtures.SNAPPSHOP_SEARCH_RESPONSE)
            MockScenario.GEOBLOCK_403 -> MockResponse().setResponseCode(403)
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .setBody(ProviderFixtures.SNAPPSHOP_SOTOON_403_HTML)
            MockScenario.SERVER_ERROR_500 -> MockResponse().setResponseCode(500)
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody("""{"status":500,"error":"SnappShop Internal Error"}""")
            MockScenario.TIMEOUT -> MockResponse().setBodyDelay(12, TimeUnit.SECONDS)
                .setResponseCode(200).setBody("{}")
            MockScenario.SCHEMA_DRIFT -> MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody("""{"data":{"new_schema_structure":[]}}""")
            MockScenario.EMPTY_RESULTS -> MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody("""{"data":{"structure":[]}}""")
            else -> MockResponse().setResponseCode(200).setBody("{}")
        }
    }

    private fun handleSnappFoodVendors(request: RecordedRequest, scenario: MockScenario): MockResponse {
        return when (scenario) {
            MockScenario.NORMAL -> MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody(ProviderFixtures.SNAPPFOOD_VENDORS_RESPONSE)
            MockScenario.GEOBLOCK_403 -> MockResponse().setResponseCode(403)
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .setBody("<html><body>Blocked by SnappFood CDN</body></html>")
            MockScenario.SERVER_ERROR_500 -> MockResponse().setResponseCode(500).setBody("Server Error")
            MockScenario.TIMEOUT -> MockResponse().setBodyDelay(12, TimeUnit.SECONDS).setBody("{}")
            MockScenario.EMPTY_RESULTS -> MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody("""{"status":true,"data":{"finalResult":[]}}""")
            else -> MockResponse().setResponseCode(200).setBody("{}")
        }
    }

    private fun handleSnappFoodDishes(request: RecordedRequest, scenario: MockScenario): MockResponse {
        return when (scenario) {
            MockScenario.NORMAL -> MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody(ProviderFixtures.SNAPPFOOD_DISHES_RESPONSE)
            MockScenario.GEOBLOCK_403 -> MockResponse().setResponseCode(403)
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .setBody("<html><body>Blocked</body></html>")
            MockScenario.SERVER_ERROR_500 -> MockResponse().setResponseCode(500).setBody("Error")
            MockScenario.TIMEOUT -> MockResponse().setBodyDelay(12, TimeUnit.SECONDS).setBody("{}")
            MockScenario.EMPTY_RESULTS -> MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody("""{"status":true,"data":{"product_variations":{"items":[]}}}""")
            else -> MockResponse().setResponseCode(200).setBody("{}")
        }
    }

    private fun handleSnappFoodState(request: RecordedRequest, scenario: MockScenario): MockResponse {
        return MockResponse().setResponseCode(200)
            .setHeader("Content-Type", "application/json; charset=utf-8")
            .setBody(ProviderFixtures.SNAPPFOOD_STATE_RESPONSE)
    }

    private fun handleSnappFoodMenu(request: RecordedRequest, scenario: MockScenario): MockResponse {
        return MockResponse().setResponseCode(200)
            .setHeader("Content-Type", "application/json; charset=utf-8")
            .setBody(ProviderFixtures.SNAPPFOOD_MENU_RESPONSE)
    }

    private fun handleTapsiFoodDishes(request: RecordedRequest, scenario: MockScenario): MockResponse {
        return when (scenario) {
            MockScenario.NORMAL -> MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody(ProviderFixtures.TAPSIFOOD_DISHES_RESPONSE)
            MockScenario.SERVER_ERROR_500 -> MockResponse().setResponseCode(500).setBody("TapsiFood 500")
            MockScenario.GEOBLOCK_403 -> MockResponse().setResponseCode(403).setBody("TapsiFood 403")
            MockScenario.TIMEOUT -> MockResponse().setBodyDelay(12, TimeUnit.SECONDS).setBody("{}")
            MockScenario.EMPTY_RESULTS -> MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody("""{"status":"SUCCESS","data":{"products":[]}}""")
            else -> MockResponse().setResponseCode(200).setBody("{}")
        }
    }

    private fun handleTapsiFoodVendors(request: RecordedRequest, scenario: MockScenario): MockResponse {
        return MockResponse().setResponseCode(200)
            .setHeader("Content-Type", "application/json; charset=utf-8")
            .setBody(ProviderFixtures.TAPSIFOOD_VENDORS_RESPONSE)
    }

    private fun handleTapsiFoodValidate(request: RecordedRequest, scenario: MockScenario): MockResponse {
        return MockResponse().setResponseCode(200)
            .setHeader("Content-Type", "application/json; charset=utf-8")
            .setBody(ProviderFixtures.TAPSIFOOD_CART_VALIDATE_RESPONSE)
    }

    private fun handleOkala(request: RecordedRequest, scenario: MockScenario): MockResponse {
        return when (scenario) {
            MockScenario.NORMAL -> MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody(ProviderFixtures.OKALA_SEARCH_RESPONSE)
            MockScenario.SERVER_ERROR_500 -> MockResponse().setResponseCode(500).setBody("Okala 500")
            MockScenario.TIMEOUT -> MockResponse().setBodyDelay(12, TimeUnit.SECONDS).setBody("{}")
            MockScenario.EMPTY_RESULTS -> MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody("""{"status":200,"data":[]}""")
            else -> MockResponse().setResponseCode(200).setBody("{}")
        }
    }
}
