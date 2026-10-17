package com.tim.futureai.domain.health

/** Extensible registration for one health check. Checks are pure probes; repairs live in SelfHealing. */
data class HealthCheckRegistration(
    val id: String,
    val title: String,
    val category: String,
    val selfHealable: Boolean,
    val check: suspend () -> HealthCheckResult,
)

class HealthCheckRegistry {
    private val registrations = linkedMapOf<String, HealthCheckRegistration>()

    @Synchronized
    fun register(registration: HealthCheckRegistration) {
        registrations[registration.id] = registration
    }

    @Synchronized
    fun all(): List<HealthCheckRegistration> = registrations.values.toList()

    @Synchronized
    fun find(checkId: String): HealthCheckRegistration? = registrations[checkId]
}
