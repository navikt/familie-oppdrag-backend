package no.nav.familie.oppdrag.config

import no.nav.familie.kontrakter.felles.Ressurs
import no.nav.familie.kontrakter.felles.jsonMapper
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import tools.jackson.module.kotlin.readValue

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    classes = [SecurityIntegrationTest::class, SecurityConfig::class],
    properties = ["management.endpoint.health.validate-group-membership=false"],
)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@ContextConfiguration(initializers = [MockOAuth2ServerInitializer::class])
@EnableAutoConfiguration(exclude = [DataSourceAutoConfiguration::class])
class SecurityIntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mockOAuth2Server: MockOAuth2Server

    private fun gyldigSimuleringRequest() =
        """
        {
          "kodeEndring": "NY",
          "fagSystem": "BA",
          "saksnummer": "TEST-123",
          "aktoer": "12345678901",
          "saksbehandlerId": "TEST-USER",
          "avstemmingTidspunkt": "2025-05-12T10:00:00",
          "utbetalingsperiode": []
        }
        """.trimIndent()

    @Nested
    inner class OffentligeEndepunkter {
        @Test
        fun `skal tillate tilgang til health uten token`() {
            mockMvc
                .perform(MockMvcRequestBuilders.get("/internal/health"))
                .andExpect(MockMvcResultMatchers.status().isOk)
        }

        @Test
        fun `skal tillate tilgang til actuator uten token`() {
            mockMvc
                .perform(MockMvcRequestBuilders.get("/internal/prometheus"))
                .andExpect(MockMvcResultMatchers.status().isOk)
        }

        @Test
        fun `skal tillate tilgang til swagger-ui uten token`() {
            mockMvc
                .perform(MockMvcRequestBuilders.get("/swagger-ui/index.html"))
                .andExpect(MockMvcResultMatchers.status().isOk)
        }

        @Test
        fun `skal tillate tilgang til api-docs uten token`() {
            mockMvc.perform(MockMvcRequestBuilders.get("/v3/api-docs")).andExpect(MockMvcResultMatchers.status().isOk)
        }
    }

    @Nested
    inner class BeskyttedeEndepunkter {
        @Test
        fun `skal avvise simulering uten token`() {
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/api/simulering/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gyldigSimuleringRequest()),
                ).andExpect(MockMvcResultMatchers.status().isForbidden)
        }

        @Test
        fun `skal akseptere gyldig Azure AD-token`() {
            val token = JwtTokenTestUtil.lagToken(mockOAuth2Server)

            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/api/simulering/v1")
                        .header("Authorization", "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gyldigSimuleringRequest()),
                )
                // testen laster ikke handlere så 404 er ok, forventer bare å ikke få 401 eller 403
                .andExpect(MockMvcResultMatchers.status().isNotFound)
        }

        @Test
        fun `skal avvise utgått token`() {
            val token = JwtTokenTestUtil.lagToken(mockOAuth2Server, expiry = -3600)

            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/api/simulering/v1")
                        .header("Authorization", "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gyldigSimuleringRequest()),
                ).andExpect(MockMvcResultMatchers.status().isUnauthorized)
        }

        @Test
        fun `skal avvise token med feil issuer`() {
            val token = JwtTokenTestUtil.lagToken(mockOAuth2Server, issuer = "feil-issuer")
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/api/simulering/v1")
                        .header("Authorization", "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gyldigSimuleringRequest()),
                ).andExpect(MockMvcResultMatchers.status().isUnauthorized)
                .andExpect {
                    val content = jsonMapper.readValue<Ressurs<Any>>(it.response.contentAsString)
                    assertThat(content.status).isEqualTo(Ressurs.Status.FEILET)
                }
        }

        @Test
        fun `skal avvise token med feil audience`() {
            val token = JwtTokenTestUtil.lagToken(mockOAuth2Server, audience = "feil-audience")

            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/api/simulering/v1")
                        .header("Authorization", "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gyldigSimuleringRequest()),
                ).andExpect(MockMvcResultMatchers.status().isUnauthorized)
                .andExpect {
                    val content = jsonMapper.readValue<Ressurs<Any>>(it.response.contentAsString)
                    assertThat(content.status).isEqualTo(Ressurs.Status.FEILET)
                }
        }
    }
}
