package com.teamwork.pricehunter.core.state

enum class ServiceStatus {
    IDLE, LOADING, SUCCESS, FAILED
}

data class ProgressiveLoadingSnapshot(
    val statuses: Map<String, ServiceStatus>,
    val completedCount: Int,
    val totalCount: Int,
    val progressFraction: Float,
    val isComplete: Boolean,
    val hasFailures: Boolean
)

class ProgressiveLoadingTracker(providerIds: Collection<String>) {
    private val lock = Any()
    private val statuses = providerIds.associateWith { ServiceStatus.IDLE }.toMutableMap()

    fun startLoading(providerId: String) = synchronized(lock) {
        if (statuses.containsKey(providerId)) {
            statuses[providerId] = ServiceStatus.LOADING
        }
    }

    fun markSuccess(providerId: String) = synchronized(lock) {
        if (statuses.containsKey(providerId)) {
            statuses[providerId] = ServiceStatus.SUCCESS
        }
    }

    fun markFailed(providerId: String) = synchronized(lock) {
        if (statuses.containsKey(providerId)) {
            statuses[providerId] = ServiceStatus.FAILED
        }
    }

    fun getSnapshot(): ProgressiveLoadingSnapshot = synchronized(lock) {
        val total = statuses.size
        val completed = statuses.values.count { it == ServiceStatus.SUCCESS || it == ServiceStatus.FAILED }
        val fraction = if (total > 0) completed.toFloat() / total.toFloat() else 0f
        val isDone = total > 0 && completed == total
        val hasFailed = statuses.values.any { it == ServiceStatus.FAILED }
        ProgressiveLoadingSnapshot(
            statuses = statuses.toMap(),
            completedCount = completed,
            totalCount = total,
            progressFraction = fraction,
            isComplete = isDone,
            hasFailures = hasFailed
        )
    }
}
