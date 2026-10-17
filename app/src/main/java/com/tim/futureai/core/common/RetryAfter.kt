package com.tim.futureai.core.common

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Parses HTTP Retry-After (seconds or RFC 1123 date) into seconds from now. */
object RetryAfter {
    fun parse(value: String?, nowEpochSeconds: Long = System.currentTimeMillis() / 1000): Long? {
        val raw = value?.trim().orEmpty()
        if (raw.isEmpty()) return null
        raw.toLongOrNull()?.let { return it.coerceIn(0L, 86_400L) }
        return runCatching {
            val target = ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toEpochSecond()
            (target - nowEpochSeconds).coerceIn(0L, 86_400L)
        }.getOrNull()
    }
}
