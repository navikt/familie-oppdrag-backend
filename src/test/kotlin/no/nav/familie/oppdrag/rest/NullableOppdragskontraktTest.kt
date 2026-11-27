package no.nav.familie.oppdrag.rest

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.familie.kontrakter.felles.jsonMapper
import no.nav.familie.kontrakter.felles.simulering.DetaljertSimuleringResultat
import no.nav.familie.oppdrag.iverksetting.OppdragMapper
import no.nav.familie.oppdrag.kontrakt.Utbetalingsoppdrag
import no.nav.familie.oppdrag.service.OppdragService
import no.nav.familie.oppdrag.simulering.SimuleringTjeneste
import no.nav.familie.oppdrag.util.TestUtbetalingsoppdrag.utbetalingsoppdragMedTilfeldigAktoer
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

internal class NullableOppdragskontraktTest {
    private val oppdragService = mockk<OppdragService>(relaxed = true)
    private val simuleringTjeneste = mockk<SimuleringTjeneste>()
    private val mockMvc =
        MockMvcBuilders
            .standaloneSetup(
                OppdragController(oppdragService, OppdragMapper()),
                SimuleringController(simuleringTjeneste),
            ).build()

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `mottar null og manglende sluttdato ved iverksetting og simulering`(utelatSluttdato: Boolean) {
        val opprinneligOppdrag = utbetalingsoppdragMedTilfeldigAktoer()
        val åpentOppdrag =
            opprinneligOppdrag.copy(
                utbetalingsperiode = opprinneligOppdrag.utbetalingsperiode.map { it.copy(vedtakdatoTom = null, opphør = null) },
            )
        val requestJson = jsonMapper.readTree(jsonMapper.writeValueAsString(åpentOppdrag))
        if (utelatSluttdato) {
            (requestJson["utbetalingsperiode"][0] as tools.jackson.databind.node.ObjectNode).remove("vedtakdatoTom")
        }
        every { simuleringTjeneste.utførSimuleringOghentDetaljertSimuleringResultat(any()) } returns
            DetaljertSimuleringResultat(emptyList())

        listOf("/api/oppdrag", "/api/simulering/v1").forEach { path ->
            mockMvc
                .perform(post(path).contentType(MediaType.APPLICATION_JSON).content(requestJson.toString()))
                .andExpect(status().isOk)
        }

        verify(exactly = 1) {
            oppdragService.opprettOppdrag(match<Utbetalingsoppdrag> { it.utbetalingsperiode.single().vedtakdatoTom == null }, any(), 0)
            simuleringTjeneste.utførSimuleringOghentDetaljertSimuleringResultat(
                match { it.utbetalingsperiode.single().vedtakdatoTom == null },
            )
        }
    }
}
