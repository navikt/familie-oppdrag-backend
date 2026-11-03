package no.nav.familie.oppdrag.simulering.sokos

import no.nav.familie.kontrakter.felles.oppdrag.Opphør
import no.nav.familie.kontrakter.felles.oppdrag.Utbetalingsoppdrag
import no.nav.familie.kontrakter.felles.oppdrag.Utbetalingsperiode
import no.nav.familie.oppdrag.simulering.SimulerBeregningRequestMapper
import no.nav.familie.oppdrag.simulering.util.lagTestUtbetalingsoppdragForFGBMedEttBarn
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SokosSimulerBeregningRequestMapperTest {
    private val sokosMapper = SokosSimulerBeregningRequestMapper()
    private val soapMapper = SimulerBeregningRequestMapper()

    @Test
    fun `oppdrag mappes likt som SOAP`() {
        // Arrange
        val utbetalingsoppdrag = lagTestUtbetalingsoppdragForFGBMedEttBarn()

        // Act
        val rest = sokosMapper.tilSokosSimulerBeregningRequest(utbetalingsoppdrag).requestSimulering()
        val soap = soapMapper.tilSimulerBeregningRequest(utbetalingsoppdrag).request.oppdrag

        // Assert
        assertEquals(soap.kodeEndring, rest.oppdrag.kodeEndring)
        assertEquals(soap.kodeFagomraade, rest.oppdrag.kodeFagomraade)
        assertEquals(soap.fagsystemId, rest.oppdrag.fagsystemId)
        assertEquals(soap.utbetFrekvens, rest.oppdrag.utbetFrekvens)
        assertEquals(soap.oppdragGjelderId, rest.oppdrag.oppdragGjelderId)
        assertEquals(soap.datoOppdragGjelderFom, rest.oppdrag.datoOppdragGjelderFom)
        assertEquals(soap.saksbehId, rest.oppdrag.saksbehId)

        val soapEnhet = soap.enhet.single()
        val restEnhet = rest.enhetOppdrag!!.single()
        assertEquals(soapEnhet.enhet, restEnhet.enhet)
        assertEquals(soapEnhet.typeEnhet, restEnhet.typeEnhet)
        assertEquals(soapEnhet.datoEnhetFom, restEnhet.datoEnhetFom)
        assertNull(rest.avstemmingsnokkel)
    }

    @Test
    fun `oppdragslinje mappes likt som SOAP`() {
        // Arrange
        val utbetalingsoppdrag = lagTestUtbetalingsoppdragForFGBMedEttBarn()

        // Act
        val rest =
            sokosMapper
                .tilSokosSimulerBeregningRequest(utbetalingsoppdrag)
                .requestSimulering()
                .oppdragslinje
                .single()
        val soap =
            soapMapper
                .tilSimulerBeregningRequest(utbetalingsoppdrag)
                .request.oppdrag.oppdragslinje
                .single()

        // Assert
        assertEquals(soap.kodeEndringLinje, rest.kodeEndringLinje)
        assertEquals(soap.vedtakId, rest.vedtakId)
        assertEquals(soap.delytelseId, rest.delytelseId)
        assertEquals(soap.kodeKlassifik, rest.kodeKlassifik)
        assertEquals(soap.datoVedtakFom, rest.datoVedtakFom)
        assertEquals(soap.datoVedtakTom, rest.datoVedtakTom)
        assertEquals(soap.sats, rest.sats)
        assertEquals(soap.typeSats, rest.typeSats)
        assertEquals(false, rest.brukKjoreplan)
        assertEquals(soap.saksbehId, rest.saksbehId)
        assertEquals(soap.utbetalesTilId, rest.utbetalesTilId)
        assertEquals(soap.henvisning, rest.henvisning)
        assertEquals(soap.attestant.single().attestantId, rest.attestant!!.single().attestantId)
        assertNull(rest.kodeStatusLinje)
        assertNull(rest.refDelytelseId)
        assertNull(rest.gradOppdragslinje)
    }

    @Test
    fun `opphør, referanse til forrige periode og utbetalingsgrad mappes likt som SOAP`() {
        // Arrange
        val utbetalingsoppdrag =
            oppdragMedPeriode(
                erEndring = false,
                opphør = Opphør(LocalDate.of(2024, 3, 1)),
                forrigePeriodeId = 1,
                utbetalingsgrad = 50,
            )

        // Act
        val rest =
            sokosMapper
                .tilSokosSimulerBeregningRequest(utbetalingsoppdrag)
                .requestSimulering()
                .oppdragslinje
                .single()
        val soap =
            soapMapper
                .tilSimulerBeregningRequest(utbetalingsoppdrag)
                .request.oppdrag.oppdragslinje
                .single()

        // Assert
        assertEquals(soap.kodeStatusLinje.value(), rest.kodeStatusLinje)
        assertEquals(soap.datoStatusFom, rest.datoStatusFom)
        assertEquals(soap.refDelytelseId, rest.refDelytelseId)
        assertEquals(soap.refFagsystemId, rest.refFagsystemId)
        assertEquals(soap.grad.single().typeGrad, rest.gradOppdragslinje!!.single().typeGrad)
        assertEquals(
            soap.grad
                .single()
                .grad
                .toInt(),
            rest.gradOppdragslinje.single().grad,
        )
    }

    @Test
    fun `endring på eksisterende periode sender ikke referanse til forrige periode`() {
        // Arrange
        val utbetalingsoppdrag = oppdragMedPeriode(erEndring = true, opphør = null, forrigePeriodeId = 1, utbetalingsgrad = null)

        // Act
        val rest =
            sokosMapper
                .tilSokosSimulerBeregningRequest(utbetalingsoppdrag)
                .requestSimulering()
                .oppdragslinje
                .single()
        val soap =
            soapMapper
                .tilSimulerBeregningRequest(utbetalingsoppdrag)
                .request.oppdrag.oppdragslinje
                .single()

        // Assert
        assertEquals(soap.kodeEndringLinje, rest.kodeEndringLinje)
        assertNull(soap.refDelytelseId)
        assertNull(rest.refDelytelseId)
        assertNull(rest.refFagsystemId)
    }

    private fun SokosSimulerBeregningRequest.requestSimulering() = operation.container.requestSimulering

    private fun oppdragMedPeriode(
        erEndring: Boolean,
        opphør: Opphør?,
        forrigePeriodeId: Long?,
        utbetalingsgrad: Int?,
    ): Utbetalingsoppdrag {
        val mal = lagTestUtbetalingsoppdragForFGBMedEttBarn()
        val periode = mal.utbetalingsperiode.single()
        return mal.copy(
            utbetalingsperiode =
                listOf(
                    Utbetalingsperiode(
                        erEndringPåEksisterendePeriode = erEndring,
                        opphør = opphør,
                        periodeId = 2,
                        forrigePeriodeId = forrigePeriodeId,
                        datoForVedtak = periode.datoForVedtak,
                        klassifisering = periode.klassifisering,
                        vedtakdatoFom = periode.vedtakdatoFom,
                        vedtakdatoTom = periode.vedtakdatoTom,
                        sats = BigDecimal(1054),
                        satsType = periode.satsType,
                        utbetalesTil = periode.utbetalesTil,
                        behandlingId = periode.behandlingId,
                        utbetalingsgrad = utbetalingsgrad,
                    ),
                ),
        )
    }
}
