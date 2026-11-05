package app.kholo.reader.unlock

private val AADHAAR = listOf("aadhaar", "aadhar", "uidai")
private val FORM16 = listOf("form16", "form-16", "form 16", "form_16", "16a")
private val FORM26 = listOf("26as", "26-as", "26 as")
private val INCOME = listOf("itr", "ais", "tis", "epan", "e-pan", "e_pan", "intimation", "143(1)")
private val BANK = listOf("statement", "bank", "sbi", "hdfc", "icici", "axis", "kotak", "pnb", "account")

/** File naam se andaza. Galat ho to user chip se badal leta hai. Sender intent me aata hi nahi. */
object DocClassifier {
    fun guess(fileName: String?): DocKind {
        val name = fileName.orEmpty().lowercase()
        return when {
            AADHAAR.any(name::contains) -> DocKind.Aadhaar
            FORM26.any(name::contains) -> DocKind.Form26As
            FORM16.any(name::contains) -> DocKind.Form16
            INCOME.any(name::contains) -> DocKind.IncomeTax
            BANK.any(name::contains) -> DocKind.Bank
            else -> DocKind.Other
        }
    }
}
