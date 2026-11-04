package app.kholo.reader.core

sealed interface DocResult<out T> {
    data class Success<T>(val value: T) : DocResult<T>

    data class Failure(val error: AppError) : DocResult<Nothing>
}

inline fun <T, R> DocResult<T>.map(transform: (T) -> R): DocResult<R> = when (this) {
    is DocResult.Success -> DocResult.Success(transform(value))
    is DocResult.Failure -> this
}
