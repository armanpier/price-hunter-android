package com.teamwork.pricehunter.core.network

import okhttp3.Interceptor
import okhttp3.Response

class DebugCaptureInterceptor(
    private val capturePool: DebugNetworkCapturePool,
    private val isDebugEnabled: Boolean = true
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!isDebugEnabled) {
            return chain.proceed(request)
        }

        val startTime = System.currentTimeMillis()
        val headersMap = mutableMapOf<String, String>()
        for (name in request.headers.names()) {
            headersMap[name] = request.headers[name] ?: ""
        }
        val maskedHeaders = DebugNetworkCapturePool.maskHeaders(headersMap)

        val response = try {
            chain.proceed(request)
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            capturePool.logCapture(
                NetworkCaptureEntry(
                    method = request.method,
                    url = request.url.toString(),
                    statusCode = 0,
                    durationMs = duration,
                    requestHeaders = maskedHeaders,
                    responseSnippet = "Exception: ${e.message}"
                )
            )
            throw e
        }

        val duration = System.currentTimeMillis() - startTime
        val responseHeadersMap = mutableMapOf<String, String>()
        for (name in response.headers.names()) {
            responseHeadersMap[name] = response.headers[name] ?: ""
        }
        val maskedResponseHeaders = DebugNetworkCapturePool.maskHeaders(responseHeadersMap)

        capturePool.logCapture(
            NetworkCaptureEntry(
                method = request.method,
                url = request.url.toString(),
                statusCode = response.code,
                durationMs = duration,
                requestHeaders = maskedHeaders,
                responseHeaders = maskedResponseHeaders,
                responseSnippet = response.message
            )
        )

        return response
    }
}
