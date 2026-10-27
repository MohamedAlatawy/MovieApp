package com.example.movieapp.domain.eligibility

data class MovieCertificationEvidence(
    val movieId: Int,
    val records: List<ReleaseCertification>,
)

data class ReleaseCertification(
    val countryCode: String,
    val certification: String?,
)

/**
 * Evaluates trusted evidence supplied by the caller; does not establish its provenance.
 * The caller must supply trusted, successfully retrieved, sufficiently complete certification
 * evidence for the requested movie before claiming new eligibility. Network failures or
 * incomplete retrieval must never be treated as successful authorization.
 *
 * The adult flag must describe the requested movie ID. Unknown adult evidence denies,
 * just like adult=true.
 * An explicit adult=false is necessary but never sufficient without US certification proof.
 * Missing certification evidence is represented by an empty records list.
 */
object MovieEligibilityPolicy {
    enum class Decision { Allow, Deny }

    fun evaluate(
        movieId: Int,
        adult: Boolean?,
        evidence: MovieCertificationEvidence,
    ): Decision {
        if (movieId <= 0 || evidence.movieId != movieId || adult != false) {
            return Decision.Deny
        }

        var hasAllowedCertification = false
        for (record in evidence.records) {
            if (record.countryCode.normalized() != "US") continue
            val certification = record.certification?.normalized()
            if (certification.isNullOrEmpty()) continue
            when (certification) {
                "G", "PG", "PG-13" -> hasAllowedCertification = true
                else -> return Decision.Deny
            }
        }
        return if (hasAllowedCertification) Decision.Allow else Decision.Deny
    }

    // ASCII-only casing avoids treating Unicode lookalikes as authoritative codes.
    private fun String.normalized(): String = trim().map { character ->
        if (character in 'a'..'z') character.uppercaseChar() else character
    }.joinToString("")
}
