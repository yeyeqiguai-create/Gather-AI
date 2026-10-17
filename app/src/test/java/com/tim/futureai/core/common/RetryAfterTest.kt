package com.tim.futureai.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RetryAfterTest {
    @Test fun parsesSeconds() {
        assertEquals(30L, RetryAfter.parse("30", 1_000))
        assertEquals(0L, RetryAfter.parse("-5", 1_000))
        assertEquals(86_400L, RetryAfter.parse("999999", 1_000))
    }

    @Test fun parsesHttpDate() {
        val now = 784_111_777L // 1994-11-06T08:49:37Z
        assertEquals(63L, RetryAfter.parse("Sun, 06 Nov 1994 08:50:40 GMT", now))
    }

    @Test fun malformedIsNull() {
        assertNull(RetryAfter.parse(null, 0))
        assertNull(RetryAfter.parse("  ", 0))
        assertNull(RetryAfter.parse("soon", 0))
    }
}