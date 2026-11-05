package app.kholo.reader.unlock

private const val NAME_LETTERS = 4
private const val PAN_PREFIX = 5
private const val MAX_CANDIDATES = 8
private const val DDMM = 4
private const val YEAR_START = 4
private const val MOBILE_TAIL = 5
private const val MOBILE_SHORT_TAIL = 4
private const val YEAR_SHORT_START = 6

/** Document ki kisme jinka password format tay hai. */
enum class DocKind { Aadhaar, IncomeTax, Form16, Form26As, Bank, Other }

/** Sirf user ke apne diye details. Yeh kabhi save ya log nahi hote. */
data class UnlockDetails(
    val name: String = "",
    val birthYear: String = "",
    val birthDate: String = "",
    val pan: String = "",
    val mobile: String = "",
)

/**
 * Password formats ka data. Har rule ka source likha hai.
 * Aadhaar: UIDAI ka format, ClearTax aur TaxGuru dono par.
 * Income Tax, Form 16, 26AS: taxguru.in/income-tax/password-formats-income-tax-tds-related-documents.html
 * Bank: har bank ka alag hai aur sources aapas me nahi milte, isliye yeh pakke nahi, sirf koshish hain.
 *
 * Yeh brute force nahi hai: candidates sirf user ke details se bante hain aur unki ginti tay hai.
 */
object PasswordRules {
    fun candidates(kind: DocKind, details: UnlockDetails): List<String> {
        val dob = details.birthDate.filter(Char::isDigit)
        val pan = details.pan.trim()
        val list = when (kind) {
            DocKind.Aadhaar -> aadhaar(details)
            DocKind.IncomeTax -> if (pan.isNotEmpty() && dob.isNotEmpty()) listOf(pan.lowercase() + dob) else emptyList()
            DocKind.Form16 -> if (pan.isNotEmpty() && dob.isNotEmpty()) listOf(pan.take(PAN_PREFIX).uppercase() + dob) else emptyList()
            DocKind.Form26As -> listOf(dob).filter { it.isNotEmpty() }
            DocKind.Bank -> bank(details, dob)
            DocKind.Other -> emptyList()
        }
        return list.distinct().take(MAX_CANDIDATES)
    }

    /** Naam ke pehle 4 akshar CAPS me, phir janm saal. Naam me space ho to dono tarah. */
    private fun aadhaar(details: UnlockDetails): List<String> {
        val year = details.birthYear.filter(Char::isDigit)
        val name = details.name.trim().uppercase()
        if (name.isEmpty() || year.isEmpty()) return emptyList()
        val noSpaces = name.filterNot(Char::isWhitespace)
        return listOf(noSpaces.take(NAME_LETTERS) + year, name.take(NAME_LETTERS) + year)
    }

    private fun bank(details: UnlockDetails, dob: String): List<String> {
        val name = details.name.trim().filterNot(Char::isWhitespace).take(NAME_LETTERS)
        val mobile = details.mobile.filter(Char::isDigit)
        val result = mutableListOf<String>()
        if (dob.isNotEmpty()) result += dob
        if (name.isNotEmpty() && dob.isNotEmpty()) {
            result += name.uppercase() + dob.take(DDMM)
            result += name.uppercase() + dob
            result += name.lowercase() + dob.take(DDMM)
            result += name.lowercase() + dob
        }
        if (mobile.isNotEmpty() && dob.isNotEmpty()) {
            result += mobile.takeLast(MOBILE_TAIL) + dob.take(DDMM) + dob.drop(YEAR_SHORT_START)
            result += mobile.takeLast(MOBILE_SHORT_TAIL) + dob.take(DDMM)
            result += mobile.takeLast(MOBILE_SHORT_TAIL) + dob.drop(YEAR_START)
        }
        return result
    }
}
