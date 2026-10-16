package com.tim.futureai.core.common

/**
 * 统一错误模型（纯 Kotlin，供 domain/data/platform 复用）。
 *
 * 可区分：连接/超时、配置、HTTP、模型、鉴权、限流、服务端、解析和未知错误。
 */
enum class NetworkFailureKind { CONNECTION, TIMEOUT }

sealed class AppError : Throwable() {

    /** 网络错误：连接失败、超时、无网络等。 */
    data class Network(
        override val message: String? = null,
        override val cause: Throwable? = null,
        val kind: NetworkFailureKind = NetworkFailureKind.CONNECTION,
    ) : AppError()

    data class Configuration(override val message: String, override val cause: Throwable? = null) : AppError()

    data class ModelUnavailable(
        override val message: String,
        val httpCode: Int,
        override val cause: Throwable? = null,
    ) : AppError()

    data class Http(
        override val message: String,
        val httpCode: Int,
        override val cause: Throwable? = null,
    ) : AppError()

    /** 鉴权错误：API Key 无效、未授权、token 过期（HTTP 401/403）。 */
    data class Auth(
        override val message: String? = null,
        override val cause: Throwable? = null,
        val httpCode: Int? = null,
    ) : AppError()

    /** 限流：HTTP 429 或 provider 自定义限流。 */
    data class RateLimited(
        override val message: String? = null,
        override val cause: Throwable? = null,
        /** 重试等待秒数，来自 Retry-After；无则为 null。 */
        val retryAfterSeconds: Long? = null,
        val httpCode: Int? = 429,
    ) : AppError()

    /** 服务端错误：HTTP 5xx 等。 */
    data class Server(
        override val message: String? = null,
        override val cause: Throwable? = null,
        val httpCode: Int? = null,
    ) : AppError()

    /** 解析错误：响应体无法反序列化或结构不符合预期。 */
    data class Parse(
        override val message: String? = null,
        override val cause: Throwable? = null,
    ) : AppError()

    /** 取消：协程被取消时使用（不算失败但需要显式区分）。 */
    data object Cancelled : AppError()

    /** 未知错误：无法归类的情况兜底。 */
    data class Unknown(
        override val message: String? = null,
        override val cause: Throwable? = null,
    ) : AppError()

}

/**
 * 将 Throwable 映射为 [AppError]。data 层捕获异常后统一走这里，
 * 后续 AI Provider / Repository 复用同一映射，避免各处重复判断。
 */
fun Throwable.toAppError(): AppError = when (this) {
    is AppError -> this
    is kotlinx.coroutines.CancellationException -> AppError.Cancelled
    is java.net.SocketTimeoutException -> AppError.Network(message, this, NetworkFailureKind.TIMEOUT)
    is java.util.concurrent.TimeoutException -> AppError.Network(message, this, NetworkFailureKind.TIMEOUT)
    is java.io.IOException -> AppError.Network(message, this)
    else -> AppError.Unknown(message ?: "Unexpected error", this)
}

/**
 * 通用结果类型：成功携带数据，失败携带 [AppError]。
 *
 * 不使用 Kotlin Result，因为其失败侧只能携带 Throwable，
 * 无法直接承载结构化的 AppError；且业务上不应让原始异常泄漏到 UI 层。
 */
sealed class AppResult<out T> {

    data class Success<T>(val data: T) : AppResult<T>()

    data class Failure(val error: AppError) : AppResult<Nothing>()

    companion object {
        fun <T> success(data: T): AppResult<T> = Success(data)
        fun <T> failure(error: AppError): AppResult<T> = Failure(error)
    }

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    /** 成功时返回数据，失败时返回 null。 */
    fun getOrNull(): T? = (this as? Success)?.data

    /** 失败时返回错误，成功时返回 null。 */
    fun errorOrNull(): AppError? = (this as? Failure)?.error

    /** 成功时变换数据；失败原样透传（含错误映射）。 */
    inline fun <R> map(transform: (T) -> R): AppResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Failure -> this
    }

    /** 成功时继续下一个返回 [AppResult] 的操作；失败原样透传（链式组合）。 */
    inline fun <R> flatMap(transform: (T) -> AppResult<R>): AppResult<R> = when (this) {
        is Success -> transform(data)
        is Failure -> this
    }

    /** 成功时执行副作用，返回原结果。 */
    inline fun onSuccess(action: (T) -> Unit): AppResult<T> {
        if (this is Success) action(data)
        return this
    }

    /** 失败时执行副作用，返回原结果。 */
    inline fun onFailure(action: (AppError) -> Unit): AppResult<T> {
        if (this is Failure) action(error)
        return this
    }
}

/** 失败时提供回退值（顶层扩展，规避 out T 的逆变限制）。 */
inline fun <T> AppResult<T>.getOrElse(default: (AppError) -> T): T = when (this) {
    is AppResult.Success -> data
    is AppResult.Failure -> default(error)
}

/** 捕获异常并映射为 [AppResult]，用于包装可能抛异常的调用。 */
inline fun <T> runCatchingApp(block: () -> T): AppResult<T> = try {
    AppResult.success(block())
} catch (t: Throwable) {
    AppResult.failure(t.toAppError())
}
