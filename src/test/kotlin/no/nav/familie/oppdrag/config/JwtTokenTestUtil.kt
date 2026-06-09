package no.nav.familie.oppdrag.config

import no.nav.security.mock.oauth2.MockOAuth2Server

object JwtTokenTestUtil {
    fun lagToken(
        mockOAuth2Server: MockOAuth2Server,
        issuer: String = "azuread",
        subject: String = "test-user@nav.no",
        audience: String = "aud-localhost",
        groups: List<String> = emptyList(),
        tilleggsClaims: Map<String, Any> = emptyMap(),
        expiry: Long = 3600,
    ): String =
        mockOAuth2Server
            .issueToken(
                issuerId = issuer,
                subject = subject,
                audience = audience,
                claims = mapOf("groups" to groups, "preferred_username" to subject) + tilleggsClaims,
                expiry = expiry,
            ).serialize()
}
