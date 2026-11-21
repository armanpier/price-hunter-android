package com.teamwork.pricehunter.core.provider

sealed class SearchUiState<out T> {
    object Idle : SearchUiState<Nothing>()
    data class Loading(val providerStatuses: Map<String, String>) : SearchUiState<Nothing>()
    data class Success<T>(val items: List<T>) : SearchUiState<T>()
    data class Empty(val query: String, val emptyMessage: String = "هیچ محصولی یافت نشد") : SearchUiState<Nothing>()
    data class Error(val message: String, val isVpnIssue: Boolean = false) : SearchUiState<Nothing>()

    companion object {
        fun <T> fromResults(query: String, results: List<T>): SearchUiState<T> {
            return if (results.isEmpty()) {
                Empty(query = query, emptyMessage = "هیچ محصولی یافت نشد")
            } else {
                Success(items = results)
            }
        }
    }
}
