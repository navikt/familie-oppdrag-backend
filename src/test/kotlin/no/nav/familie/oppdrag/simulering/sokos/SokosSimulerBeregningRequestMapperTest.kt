package no.nav.familie.oppdrag.simulering.sokos

import no.nav.familie.kontrakter.felles.jsonMapper
import no.nav.familie.oppdrag.iverksetting.Jaxb
import no.nav.familie.oppdrag.kontrakt.Opphør
import no.nav.familie.oppdrag.kontrakt.Utbetalingsoppdrag
import no.nav.familie.oppdrag.simulering.SimulerBeregningRequestMapper
import no.nav.familie.oppdrag.simulering.sokos.SokosSimulerBeregningRequest.Attestant
import no.nav.familie.oppdrag.simulering.sokos.SokosSimulerBeregningRequest.Enhet
import no.nav.familie.oppdrag.simulering.sokos.SokosSimulerBeregningRequest.Grad
import no.nav.familie.oppdrag.simulering.sokos.SokosSimulerBeregningRequest.Oppdrag
import no.nav.familie.oppdrag.simulering.sokos.SokosSimulerBeregningRequest.Oppdragslinje
import no.nav.familie.oppdrag.simulering.sokos.SokosSimulerBeregningRequest.RequestSimulering
import no.nav.familie.oppdrag.simulering.util.lagTestUtbetalingsoppdragForFGBMedEttBarn
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SokosSimulerBeregningRequestMapperTest {
    @Test
    fun `løpende periode beholder null i SOAP og REST og utelater sluttdato i JSON`() {
        val mal = lagTestUtbetalingsoppdragForFGBMedEttBarn()
        val utbetalingsoppdrag = mal.copy(utbetalingsperiode = mal.utbetalingsperiode.map { it.copy(vedtakdatoTom = null) })

        val soap = SimulerBeregningRequestMapper().tilSimulerBeregningRequest(utbetalingsoppdrag)
        val rest = SokosSimulerBeregningRequestMapper.tilSokosSimulerBeregningRequest(utbetalingsoppdrag)

        assertNull(
            soap.request.oppdrag.oppdragslinje
                .single()
                .datoVedtakTom,
        )
        assertNull(
            rest.operation.container.requestSimulering.oppdragslinje
                .single()
                .datoVedtakTom,
        )
        val json = jsonMapper.writeValueAsString(rest)
        assertThat(json).doesNotContain("datoVedtakTom")
        assertThat(Jaxb.tilXml(soap)).doesNotContain("datoVedtakTom")
    }

    @Test
    fun `ny periode med opphør, referanse og utbetalingsgrad og endring på eksisterende periode mappes likt som SOAP`() {
        // Arrange
        val mal = lagTestUtbetalingsoppdragForFGBMedEttBarn()
        val periode = mal.utbetalingsperiode.single()
        val utbetalingsoppdrag =
            mal.copy(
                utbetalingsperiode =
                    listOf(
                        periode.copy(periodeId = 2, forrigePeriodeId = 1, opphør = Opphør(LocalDate.of(2024, 3, 1)), utbetalingsgrad = 50),
                        periode.copy(periodeId = 3, forrigePeriodeId = 2, erEndringPåEksisterendePeriode = true),
                    ),
            )

        // Act
        val rest = SokosSimulerBeregningRequestMapper.tilSokosSimulerBeregningRequest(utbetalingsoppdrag)

        // Assert
        assertEquals(forventetFraSoap(utbetalingsoppdrag), rest.operation.container.requestSimulering)
    }

    // REST-requesten bygd fra SOAP-requesten, så testen sammenligner alle felt. Avvik fra SOAP: brukKjoreplan "N" er false i
    // REST, og fradragTillegg finnes ikke i REST-kontrakten.
    private fun forventetFraSoap(utbetalingsoppdrag: Utbetalingsoppdrag): RequestSimulering {
        val soap = SimulerBeregningRequestMapper().tilSimulerBeregningRequest(utbetalingsoppdrag).request.oppdrag
        return RequestSimulering(
            oppdrag =
                Oppdrag(
                    kodeEndring = soap.kodeEndring,
                    kodeFagomraade = soap.kodeFagomraade,
                    fagsystemId = soap.fagsystemId,
                    utbetFrekvens = soap.utbetFrekvens,
                    oppdragGjelderId = soap.oppdragGjelderId,
                    datoOppdragGjelderFom = soap.datoOppdragGjelderFom,
                    saksbehId = soap.saksbehId,
                ),
            enhetOppdrag = soap.enhet.map { Enhet(it.typeEnhet, it.enhet, it.datoEnhetFom) },
            oppdragslinje =
                soap.oppdragslinje.map {
                    Oppdragslinje(
                        kodeEndringLinje = it.kodeEndringLinje,
                        kodeStatusLinje = it.kodeStatusLinje?.value(),
                        datoStatusFom = it.datoStatusFom,
                        vedtakId = it.vedtakId,
                        delytelseId = it.delytelseId,
                        kodeKlassifik = it.kodeKlassifik,
                        datoVedtakFom = it.datoVedtakFom,
                        datoVedtakTom = it.datoVedtakTom,
                        sats = it.sats,
                        typeSats = it.typeSats,
                        brukKjoreplan = false,
                        saksbehId = it.saksbehId,
                        utbetalesTilId = it.utbetalesTilId,
                        henvisning = it.henvisning,
                        refFagsystemId = it.refFagsystemId,
                        refDelytelseId = it.refDelytelseId,
                        attestant = it.attestant.map { attestant -> Attestant(attestant.attestantId) },
                        gradOppdragslinje = it.grad.map { grad -> Grad(grad.typeGrad, grad.grad.toInt()) }.ifEmpty { null },
                    )
                },
        )
    }
}
