package no.nav.familie.oppdrag.simulering.sokos

import no.nav.familie.kontrakter.felles.oppdrag.Opphør
import no.nav.familie.kontrakter.felles.oppdrag.Utbetalingsoppdrag
import no.nav.familie.oppdrag.simulering.SimulerBeregningRequestMapper
import no.nav.familie.oppdrag.simulering.sokos.SokosSimulerBeregningRequest.Attestant
import no.nav.familie.oppdrag.simulering.sokos.SokosSimulerBeregningRequest.Enhet
import no.nav.familie.oppdrag.simulering.sokos.SokosSimulerBeregningRequest.Grad
import no.nav.familie.oppdrag.simulering.sokos.SokosSimulerBeregningRequest.Oppdrag
import no.nav.familie.oppdrag.simulering.sokos.SokosSimulerBeregningRequest.Oppdragslinje
import no.nav.familie.oppdrag.simulering.sokos.SokosSimulerBeregningRequest.RequestSimulering
import no.nav.familie.oppdrag.simulering.util.lagTestUtbetalingsoppdragForFGBMedEttBarn
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals

class SokosSimulerBeregningRequestMapperTest {
    @Test
    fun `ny periode med opphør, referanse til forrige periode og utbetalingsgrad mappes likt som SOAP`() {
        // Arrange
        val utbetalingsoppdrag =
            oppdragMedPeriode(erEndring = false, opphør = Opphør(LocalDate.of(2024, 3, 1)), forrigePeriodeId = 1, utbetalingsgrad = 50)

        // Act
        val rest = SokosSimulerBeregningRequestMapper.tilSokosSimulerBeregningRequest(utbetalingsoppdrag)

        // Assert
        assertEquals(forventetFraSoap(utbetalingsoppdrag), rest.operation.container.requestSimulering)
    }

    @Test
    fun `endring på eksisterende periode mappes likt som SOAP uten referanse til forrige periode`() {
        // Arrange
        val utbetalingsoppdrag = oppdragMedPeriode(erEndring = true, opphør = null, forrigePeriodeId = 1, utbetalingsgrad = null)

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

    private fun oppdragMedPeriode(
        erEndring: Boolean,
        opphør: Opphør?,
        forrigePeriodeId: Long?,
        utbetalingsgrad: Int?,
    ): Utbetalingsoppdrag {
        val mal = lagTestUtbetalingsoppdragForFGBMedEttBarn()
        val periode =
            mal.utbetalingsperiode.single().copy(
                erEndringPåEksisterendePeriode = erEndring,
                opphør = opphør,
                periodeId = 2,
                forrigePeriodeId = forrigePeriodeId,
                utbetalingsgrad = utbetalingsgrad,
            )
        return mal.copy(utbetalingsperiode = listOf(periode))
    }
}
