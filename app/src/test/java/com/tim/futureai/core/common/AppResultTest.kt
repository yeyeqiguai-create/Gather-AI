package com.tim.futureai.core.common

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeoutException

class AppResultTest {

    // ---- AppResult 基本行为 ----

    @Test
    fun success_carriesData() {
        val r = AppResult.success(42)
        assertTrue(r.isSuccess)
        assertFalse(r.isFailure)
        assertEquals(42, r.getOrNull())
        assertNull(r.errorOrNull())
    }

    @Test
    fun failure_carriesError() {
        val r = AppResult.failure<Unit>(AppError.Auth("bad key"))
        assertTrue(r.isFailure)
        assertNull(r.getOrNull())
        assertEquals(AppError.Auth("bad key"), r.errorOrNull())
    }

    @Test
    fun map_transformsOnlySuccess() {
        assertEquals(84, AppResult.success(42).map { it * 2 }.getOrNull())
        val err = AppError.Network()
        assertEquals(err, AppResult.failure<Int>(err).map { it * 2 }.errorOrNull())
    }

    @Test
    fun flatMap_chains_andSkipsOnFailure() {
        val ok = AppResult.success(2).flatMap { AppResult.success(it * 10) }
        assertEquals(20, ok.getOrNull())

        val err = AppError.Parse("bad json")
        val failed = AppResult.failure<Int>(err).flatMap { AppResult.success(it) }
        assertEquals(err, failed.errorOrNull())
    }

    @Test
    fun onSuccess_onFailure_runSideEffects() {
        var got: Int? = null
        AppResult.success(1).onSuccess { got = it }.onFailure { got = -1 }
        assertEquals(1, got)

        var err: AppError? = null
        AppResult.failure<Int>(AppError.Server()).onFailure { err = it }
        assertTrue(err is AppError.Server)
    }

    @Test
    fun getOrElse_returnsFallbackOnFailure() {
        assertEquals("fb", AppResult.failure<String>(AppError.Unknown()).getOrElse { "fb" })
        assertEquals("ok", AppResult.success("ok").getOrElse { "fb" })
    }

    // ---- 错误映射 ----

    @Test
    fun toAppError_mapsIOExceptionToNetwork() {
        val e = IOException("no route")
        val err = e.toAppError()
        assertTrue(err is AppError.Network)
        assertEquals("no route", err.message)
    }

    @Test
    fun toAppError_mapsTimeoutToNetwork() {
        assertTrue(TimeoutException().toAppError() is AppError.Network)
    }

    @Test
    fun toAppError_mapsCancellation() {
        assertTrue(CancellationException("cancelled").toAppError() is AppError.Cancelled)
    }

    @Test
    fun toAppError_keepsExistingAppError() {
        val err: AppError = AppError.RateLimited("429", retryAfterSeconds = 30)
        assertEquals(err, (err as Throwable).toAppError())
    }

    @Test
    fun toAppError_unknownFallback() {
        assertTrue(IllegalStateException("??").toAppError() is AppError.Unknown)
    }

    // ---- runCatchingApp ----

    @Test
    fun runCatchingApp_success() {
        assertEquals("v", runCatchingApp { "v" }.getOrNull())
    }

    @Test
    fun runCatchingApp_thrownIOExceptionBecomesNetworkFailure() {
        val r = runCatchingApp<String> { throw IOException("down") }
        assertTrue(r.errorOrNull() is AppError.Network)
    }

    // ---- AppError 相等性与字段 ----

    @Test
    fun appError_equalityAndFields() {
        assertEquals(AppError.Auth("a"), AppError.Auth("a"))
        val rl = AppError.RateLimited("slow down", retryAfterSeconds = 60)
        assertEquals(60L, (rl as AppError.RateLimited).retryAfterSeconds)
        val sv = AppError.Server("oops", httpCode = 503) as AppError.Server
        assertEquals(503, sv.httpCode)
    }
}
