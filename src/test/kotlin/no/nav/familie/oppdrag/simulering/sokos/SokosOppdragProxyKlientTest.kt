package no.nav.familie.oppdrag.simulering.sokos

import no.nav.familie.oppdrag.simulering.util.lagTestUtbetalingsoppdragForFGBMedEttBarn
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import kotlin.test.assertEquals

class SokosOppdragProxyKlientTest {
    @Test
    fun `serialiserer request med kontraktens feltnavn og mapper respons med ukjente felt og null-lister`() {
        // Arrange
        // EntraIDRestClientFactory bygger på RestClient.builder(), så testen bruker de samme standardkonverterne.
        val restClientBuilder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(restClientBuilder).build()
        val utbetalingsoppdrag = lagTestUtbetalingsoppdragForFGBMedEttBarn()
        server
            .expect(requestTo("http://sokos/api/osSimulerBeregningApi/simulerBeregning"))
            .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
            .andExpect(
                jsonPath("$.OsSimulerBeregningOperation.ZS1ICont.requestSimulering.oppdrag.fagsystemId")
                    .value(utbetalingsoppdrag.saksnummer),
            ).andRespond(withSuccess(RESPONS, MediaType.APPLICATION_JSON))
        val request = SokosSimulerBeregningRequestMapper.tilSokosSimulerBeregningRequest(utbetalingsoppdrag)

        // Act
        val respons = SokosOppdragProxyKlient("http://sokos", restClientBuilder.build()).simulerBeregning(request)

        // Assert
        server.verify()
        val resultat = SokosSimuleringResultatMapper.tilDetaljertSimuleringResultat(respons, utbetalingsoppdrag)
        assertEquals(listOf(BigDecimal("1054.00")), resultat.simuleringMottaker.flatMap { it.simulertPostering }.map { it.beløp })
    }

    companion object {
        private val RESPONS =
            """
            {"OsSimulerBeregningOperationResponse": {"ZS1OCont": {"responsSimulering": {"statusKode": 0, "ukjentFelt": "tåles",
              "beregning": {"periodeStoppnivaa": [
                {"beregningStoppnivaa": {"kodeFagomraade": "BA", "fagsystemId": "5566", "utbetalesTilId": "12345678901",
                  "datoForfall": "2024-01-20", "stoppnivaaDetaljer": [{"faktiskFom": "2024-01-01", "faktiskTom": "2024-01-31",
                  "belop": 1054.00, "typeKlasse": "YTEL"}]}},
                {"beregningStoppnivaa": {"utbetalesTilId": "12345678901", "stoppnivaaDetaljer": null}}
              ]}
            }}}}
            """.trimIndent()
    }
}
