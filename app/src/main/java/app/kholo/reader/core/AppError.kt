package app.kholo.reader.core

import androidx.annotation.StringRes
import app.kholo.reader.R

/** Expected failures. Inka user message hamesha strings.xml se aata hai, raw exception kabhi nahi. */
sealed interface AppError {
    @get:StringRes
    val messageRes: Int

    data object WrongPassword : AppError {
        override val messageRes = R.string.error_wrong_password
    }

    /** Is phone ka PDF engine password wali file khol hi nahi sakta. */
    data object PasswordUnsupported : AppError {
        override val messageRes = R.string.error_password_unsupported
    }

    /** Password wali Word ya Excel. Yeh ZIP nahi, OLE2 file hoti hai. */
    data object OfficePassword : AppError {
        override val messageRes = R.string.error_office_password
    }

    data object FileGone : AppError {
        override val messageRes = R.string.error_file_gone
    }

    data object Corrupt : AppError {
        override val messageRes = R.string.error_corrupt
    }

    data object Empty : AppError {
        override val messageRes = R.string.error_empty
    }

    data object Unsupported : AppError {
        override val messageRes = R.string.error_unsupported
    }

    data object NoSpace : AppError {
        override val messageRes = R.string.error_no_space
    }

    data object TooLarge : AppError {
        override val messageRes = R.string.error_too_large
    }
}
