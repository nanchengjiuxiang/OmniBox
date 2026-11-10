package com.omnibox.core.common

/** 统一结果封装：所有 UseCase 不允许把异常直接抛到 UI 层。 */
sealed interface OmniResult<out T> {
    data class Success<T>(val data: T) : OmniResult<T>
    data class Failure(val cause: Throwable, val message: String = cause.message.orEmpty()) : OmniResult<Nothing>
    data object Loading : OmniResult<Nothing>
}

inline fun <T> runCatchingOmni(block: () -> T): OmniResult<T> = try {
    OmniResult.Success(block())
} catch (t: Throwable) {
    OmniResult.Failure(t)
}
