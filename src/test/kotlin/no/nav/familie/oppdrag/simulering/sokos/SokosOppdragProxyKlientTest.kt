package no.nav.familie.oppdrag.simulering.sokos

import no.nav.familie.oppdrag.simulering.util.lagTestUtbetalingsoppdragForFGBMedEttBarn
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SokosOppdragProxyKlientTest {
    // EntraIDRestClientFactory bygger på RestClient.builder(), så testen bruker de samme standardkonverterne.
    private val restClientBuilder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(restClientBuilder).build()
    private val klient = SokosOppdragProxyKlient("http://sokos", restClientBuilder.build())

    private val utbetalingsoppdrag = lagTestUtbetalingsoppdragForFGBMedEttBarn()
    private val request = SokosSimulerBeregningRequestMapper().tilSokosSimulerBeregningRequest(utbetalingsoppdrag)

    @Test
    fun `request sendes med feltnavnene fra Swagger-kontrakten`() {
        // Arrange
        server
            .expect(requestTo("http://sokos/api/osSimulerBeregningApi/simulerBeregning"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(
                jsonPath("$.OsSimulerBeregningOperation.ZS1ICont.requestSimulering.oppdrag.fagsystemId")
                    .value(utbetalingsoppdrag.saksnummer),
            ).andRespond(withSuccess(respons(stoppnivaaDetaljer = "[]"), MediaType.APPLICATION_JSON))

        // Act
        klient.simulerBeregning(request)

        // Assert
        server.verify()
    }

    @Test
    fun `respons leses med feltnavnene fra Swagger-kontrakten og tåler ukjente felt`() {
        // Arrange
        server
            .expect(requestTo("http://sokos/api/osSimulerBeregningApi/simulerBeregning"))
            .andRespond(withSuccess(respons(stoppnivaaDetaljer = DETALJER), MediaType.APPLICATION_JSON))

        // Act
        val respons = klient.simulerBeregning(request)

        // Assert
        val responsSimulering = respons.operationResponse?.container?.responsSimulering
        assertEquals(0, responsSimulering?.statusKode)
        val detaljer =
            responsSimulering
                ?.beregning
                ?.periodeStoppnivaa
                ?.single()
                ?.beregningStoppnivaa
                ?.stoppnivaaDetaljer
                ?.single()
        assertEquals(BigDecimal("1054.00"), detaljer?.belop)
        assertEquals("YTEL", detaljer?.typeKlasse)
    }

    @Test
    fun `stoppnivaaDetaljer som er null gir mottaker uten posteringer`() {
        // Arrange
        server
            .expect(requestTo("http://sokos/api/osSimulerBeregningApi/simulerBeregning"))
            .andRespond(withSuccess(respons(stoppnivaaDetaljer = "null"), MediaType.APPLICATION_JSON))

        // Act
        val resultat =
            SokosSimuleringResultatMapper().tilDetaljertSimuleringResultat(klient.simulerBeregning(request), utbetalingsoppdrag)

        // Assert
        assertTrue(
            resultat.simuleringMottaker
                .single()
                .simulertPostering
                .isEmpty(),
        )
    }

    @Test
    fun `periodeStoppnivaa som er null gir tomt resultat`() {
        // Arrange
        server
            .expect(requestTo("http://sokos/api/osSimulerBeregningApi/simulerBeregning"))
            .andRespond(
                withSuccess(
                    """
                    {"OsSimulerBeregningOperationResponse": {"ZS1OCont": {"responsSimulering": {
                      "statusKode": 0, "statusMelding": null,
                      "beregning": {"gjelderId": "12345678901", "belopTotalt": 0, "periodeStoppnivaa": null}
                    }}}}
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        // Act
        val resultat =
            SokosSimuleringResultatMapper().tilDetaljertSimuleringResultat(klient.simulerBeregning(request), utbetalingsoppdrag)

        // Assert
        assertTrue(resultat.simuleringMottaker.isEmpty())
    }

    private fun respons(stoppnivaaDetaljer: String) =
        """
        {"OsSimulerBeregningOperationResponse": {"ZS1OCont": {"responsSimulering": {
          "statusKode": 0, "statusMelding": null, "ukjentFelt": "tåles",
          "beregning": {"gjelderId": "12345678901", "gjelderNavn": null, "datoBeregnet": "2024-01-10", "kodeFaggruppe": "BA",
            "belopTotalt": 1054.00,
            "periodeStoppnivaa": [{
              "beregningsPeriode": {"periodeFom": "2024-01-01", "periodeTom": "2024-01-31"},
              "beregningStoppnivaa": {"kodeFagomraade": "BA", "stoppNivaaId": 1, "fagsystemId": "5566",
                "utbetalesTilId": "12345678901", "datoForfall": "2024-01-20", "feilkonto": false,
                "stoppnivaaDetaljer": $stoppnivaaDetaljer}
            }]
          }
        }}}}
        """.trimIndent()

    companion object {
        private const val DETALJER =
            """[{"faktiskFom": "2024-01-01", "faktiskTom": "2024-01-31", "belop": 1054.00, "klassekode": "BATR",
            "typeKlasse": "YTEL", "kontoStreng": null}]"""
    }
}
