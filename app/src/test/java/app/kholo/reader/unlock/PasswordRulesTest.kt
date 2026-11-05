package app.kholo.reader.unlock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordRulesTest {
    private val person = UnlockDetails(
        name = "Asha Verma",
        birthYear = "1990",
        birthDate = "15031990",
        pan = "ABCDE1234F",
        mobile = "9876543210",
    )

    @Test
    fun `aadhaar uses four capital letters and the birth year`() {
        assertEquals("ASHA1990", PasswordRules.candidates(DocKind.Aadhaar, person).first())
    }

    @Test
    fun `aadhaar tries both forms for a short first name`() {
        val list = PasswordRules.candidates(DocKind.Aadhaar, person.copy(name = "R K Sharma"))
        assertEquals(listOf("RKSH1990", "R K 1990"), list)
    }

    @Test
    fun `name shorter than four letters is used as it is`() {
        assertEquals("OM1990", PasswordRules.candidates(DocKind.Aadhaar, person.copy(name = "Om")).first())
    }

    @Test
    fun `income tax uses lowercase pan and full date of birth`() {
        assertEquals(listOf("abcde1234f15031990"), PasswordRules.candidates(DocKind.IncomeTax, person))
    }

    @Test
    fun `form 16 uses first five capital pan letters and date of birth`() {
        assertEquals(listOf("ABCDE15031990"), PasswordRules.candidates(DocKind.Form16, person))
    }

    @Test
    fun `form 26as is only the date of birth`() {
        assertEquals(listOf("15031990"), PasswordRules.candidates(DocKind.Form26As, person))
    }

    @Test
    fun `bank tries a small fixed list only`() {
        val list = PasswordRules.candidates(DocKind.Bank, person)
        assertTrue(list.size <= 8)
        assertTrue("ASHA1503" in list)
        assertTrue("15031990" in list)
        assertEquals(list.distinct(), list)
    }

    @Test
    fun `missing details give nothing to try`() {
        assertTrue(PasswordRules.candidates(DocKind.Aadhaar, UnlockDetails()).isEmpty())
        assertTrue(PasswordRules.candidates(DocKind.IncomeTax, UnlockDetails(pan = "ABCDE1234F")).isEmpty())
        assertTrue(PasswordRules.candidates(DocKind.Other, person).isEmpty())
    }

    @Test
    fun `file names are guessed`() {
        assertEquals(DocKind.Aadhaar, DocClassifier.guess("EAadhaar_1234.pdf"))
        assertEquals(DocKind.Form16, DocClassifier.guess("Form16_2025.pdf"))
        assertEquals(DocKind.Form26As, DocClassifier.guess("26AS.pdf"))
        assertEquals(DocKind.IncomeTax, DocClassifier.guess("AIS_report.pdf"))
        assertEquals(DocKind.Bank, DocClassifier.guess("HDFC_statement.pdf"))
        assertEquals(DocKind.Other, DocClassifier.guess("notes.pdf"))
        assertEquals(DocKind.Other, DocClassifier.guess(null))
    }
}
