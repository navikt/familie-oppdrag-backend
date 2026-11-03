package no.nav.familie.oppdrag.simulering.sokos

import no.nav.familie.oppdrag.simulering.util.lagTestUtbetalingsoppdragForFGBMedEttBarn
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.mock.http.client.MockClientHttpResponse
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.ResponseCreator
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import kotlin.test.assertEquals

class SokosSimuleringSkyggekjørerTest {
    @ParameterizedTest(name = "{0} gir {1}")
    @MethodSource("feiltilfeller")
    fun `feilårsak skiller feil fra proxyen, nettverket og egen mapping`(
        tilfelle: String,
        forventetÅrsak: String,
        svarFraProxy: ResponseCreator,
    ) {
        // Arrange
        val restClientBuilder = RestClient.builder()
        MockRestServiceServer
            .bindTo(restClientBuilder)
            .build()
            .expect(requestTo("http://sokos/api/osSimulerBeregningApi/simulerBeregning"))
            .andRespond(svarFraProxy)
        val klient = SokosOppdragProxyKlient("http://sokos", restClientBuilder.build())
        val utbetalingsoppdrag = lagTestUtbetalingsoppdragForFGBMedEttBarn()
        val request = SokosSimulerBeregningRequestMapper().tilSokosSimulerBeregningRequest(utbetalingsoppdrag)

        // Act
        val feil =
            assertThrows<Exception> {
                SokosSimuleringResultatMapper().tilDetaljertSimuleringResultat(klient.simulerBeregning(request), utbetalingsoppdrag)
            }

        // Assert
        assertEquals(forventetÅrsak, SokosSimuleringSkyggekjører.feilårsak(feil))
    }

    companion object {
        @JvmStatic
        fun feiltilfeller(): List<Arguments> =
            listOf(
                Arguments.of("503 fra proxyen", "http_503", withStatus(HttpStatus.SERVICE_UNAVAILABLE)),
                Arguments.of("timeout ved oppkobling", "timeout", withException(SocketTimeoutException("Connect timed out"))),
                Arguments.of("timeout mens vi venter på svar", "timeout", feilerVedLesingAvSvar(SocketTimeoutException("Read timed out"))),
                Arguments.of("oppkobling avvist", "nettverk", withException(ConnectException("Connection refused"))),
                Arguments.of(
                    "tilkobling brutt mens vi venter på svar",
                    "nettverk",
                    feilerVedLesingAvSvar(SocketException("Connection reset")),
                ),
                Arguments.of("svar som ikke er JSON", "respons", withSuccess("ikke json", MediaType.APPLICATION_JSON)),
                Arguments.of(
                    "statusKode 8 fra Oppdrag",
                    "statuskode",
                    withSuccess(
                        """{"OsSimulerBeregningOperationResponse": {"ZS1OCont": {"responsSimulering": {"statusKode": 8, "statusMelding": "Feil"}}}}""",
                        MediaType.APPLICATION_JSON,
                    ),
                ),
                Arguments.of(
                    "postering uten datoForfall",
                    "mapping",
                    withSuccess(
                        """
                        {"OsSimulerBeregningOperationResponse": {"ZS1OCont": {"responsSimulering": {"statusKode": 0,
                          "beregning": {"periodeStoppnivaa": [{"beregningStoppnivaa": {"kodeFagomraade": "BA", "fagsystemId": "5566",
                            "utbetalesTilId": "12345678901", "stoppnivaaDetaljer": [{"faktiskFom": "2024-01-01",
                            "faktiskTom": "2024-01-31", "belop": 1054, "typeKlasse": "YTEL"}]}}]}
                        }}}}
                        """.trimIndent(),
                        MediaType.APPLICATION_JSON,
                    ),
                ),
            )

        // Med SimpleClientHttpRequestFactory leses svaret på en POST først når statusen hentes, så lesefeil oppstår der.
        private fun feilerVedLesingAvSvar(feil: IOException) =
            ResponseCreator {
                object : MockClientHttpResponse() {
                    override fun getStatusCode(): HttpStatusCode = throw feil
                }
            }
    }
}
