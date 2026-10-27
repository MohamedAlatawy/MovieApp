package com.example.movieapp.domain.eligibility

import com.example.movieapp.domain.eligibility.MovieEligibilityPolicy.Decision.Allow
import com.example.movieapp.domain.eligibility.MovieEligibilityPolicy.Decision.Deny
import org.junit.Assert.assertEquals
import org.junit.Test

class MovieEligibilityPolicyTest {
    @Test
    fun eachAllowedCertificationAllows() {
        for (rating in listOf("G", "PG", "PG-13")) {
            assertEquals(rating, Allow, evaluate(ReleaseCertification("US", rating)))
        }
    }

    @Test
    fun restrictiveAndUnrecognizedCertificationsDeny() {
        for (rating in listOf("R", "NC-17", "NR", "UNKNOWN", "TV-PG", "PG13")) {
            assertEquals(rating, Deny, evaluate(ReleaseCertification("US", rating)))
        }
    }

    @Test
    fun missingEvidenceDeniesEvenWhenAdultIsFalse() {
        assertEquals(Deny, evaluate())
    }

    @Test
    fun onlyBlankOrNullCertificationsDeny() {
        assertEquals(Deny, evaluate(
            ReleaseCertification("US", null),
            ReleaseCertification("US", ""),
            ReleaseCertification("US", " \t\n"),
        ))
    }

    @Test
    fun conflictingEvidenceDeniesInEitherOrder() {
        for (rating in listOf("R", "NC-17", "UNKNOWN")) {
            val allowed = ReleaseCertification("US", "PG")
            val denied = ReleaseCertification("US", rating)
            assertEquals(Deny, evaluate(allowed, denied))
            assertEquals(Deny, evaluate(denied, allowed))
        }
    }

    @Test
    fun multipleAllowedRecordsAllow() {
        assertEquals(Allow, evaluate(
            ReleaseCertification("US", "G"),
            ReleaseCertification("US", "PG"),
            ReleaseCertification("US", "PG-13"),
        ))
    }

    @Test
    fun blankRecordsNeitherAuthorizeNorVetoAllowedProof() {
        assertEquals(Allow, evaluate(
            ReleaseCertification("US", null),
            ReleaseCertification("US", "PG-13"),
            ReleaseCertification("US", " "),
        ))
    }

    @Test
    fun nonUsRecordsAloneDeny() {
        assertEquals(Deny, evaluate(
            ReleaseCertification("GB", "PG"),
            ReleaseCertification("CA", "G"),
        ))
    }

    @Test
    fun nonUsRecordsDoNotOverrideUsEvidence() {
        assertEquals(Allow, evaluate(ReleaseCertification("US", "PG"), ReleaseCertification("GB", "R")))
        assertEquals(Deny, evaluate(ReleaseCertification("US", null), ReleaseCertification("CA", "G")))
    }

    @Test
    fun mismatchedMovieIdentityDenies() {
        assertEquals(Deny, evaluate(ReleaseCertification("US", "G"), evidenceId = 43))
    }

    @Test
    fun nonPositiveMovieAndEvidenceIdsDeny() {
        for (invalidId in listOf(0, -1, Int.MIN_VALUE)) {
            assertEquals(Deny, evaluate(ReleaseCertification("US", "G"), movieId = invalidId, evidenceId = invalidId))
            assertEquals(Deny, evaluate(ReleaseCertification("US", "G"), evidenceId = invalidId))
        }
    }

    @Test
    fun adultTrueVetoesAllowedCertification() {
        assertEquals(Deny, evaluate(ReleaseCertification("US", "G"), adult = true))
    }

    @Test
    fun unknownAdultEvidenceDeniesAllowedCertification() {
        assertEquals(Deny, evaluate(ReleaseCertification("US", "G"), adult = null))
    }

    @Test
    fun whitespaceAndAsciiCaseAreNormalized() {
        assertEquals(Allow, evaluate(ReleaseCertification(" us\t", " pg-13\n")))
        assertEquals(Deny, evaluate(ReleaseCertification("uS", " r ")))
    }

    @Test
    fun malformedCountryAndUnicodeLookalikesDoNotAuthorize() {
        for (country in listOf("", "USA", "U S", "U\u017f")) {
            assertEquals(Deny, evaluate(ReleaseCertification(country, "G")))
        }
        assertEquals(Deny, evaluate(ReleaseCertification("US", "P\uFF27")))
    }

    private fun evaluate(
        vararg records: ReleaseCertification,
        movieId: Int = 42,
        evidenceId: Int = movieId,
        adult: Boolean? = false,
    ) = MovieEligibilityPolicy.evaluate(movieId, adult, MovieCertificationEvidence(evidenceId, records.toList()))
}
