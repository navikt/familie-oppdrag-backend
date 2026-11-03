package no.nav.familie.oppdrag.simulering.sokos

import no.nav.familie.kontrakter.felles.oppdrag.Utbetalingsoppdrag
import no.nav.familie.kontrakter.felles.oppdrag.Utbetalingsperiode
import no.nav.familie.oppdrag.iverksetting.EndringsKode
import no.nav.familie.oppdrag.iverksetting.GradTypeKode
import no.nav.familie.oppdrag.iverksetting.OppdragSkjemaConstants
import no.nav.familie.oppdrag.iverksetting.SatsTypeKode
import no.nav.familie.oppdrag.iverksetting.UtbetalingsfrekvensKode
import no.nav.familie.oppdrag.simulering.sokos.SokosSimulerBeregningRequest.Attestant
import no.nav.familie.oppdrag.simulering.sokos.SokosSimulerBeregningRequest.Container
import no.nav.familie.oppdrag.simulering.sokos.SokosSimulerBeregningRequest.Enhet
import no.nav.familie.oppdrag.simulering.sokos.SokosSimulerBeregningRequest.Grad
import no.nav.familie.oppdrag.simulering.sokos.SokosSimulerBeregningRequest.Operation
import no.nav.familie.oppdrag.simulering.sokos.SokosSimulerBeregningRequest.Oppdrag
import no.nav.familie.oppdrag.simulering.sokos.SokosSimulerBeregningRequest.Oppdragslinje
import no.nav.familie.oppdrag.simulering.sokos.SokosSimulerBeregningRequest.RequestSimulering
import org.springframework.stereotype.Component

@Component
class SokosSimulerBeregningRequestMapper {
    fun tilSokosSimulerBeregningRequest(utbetalingsoppdrag: Utbetalingsoppdrag): SokosSimulerBeregningRequest =
        SokosSimulerBeregningRequest(
            operation =
                Operation(
                    container =
                        Container(
                            requestSimulering =
                                RequestSimulering(
                                    oppdrag = tilOppdrag(utbetalingsoppdrag),
                                    enhetOppdrag = listOf(oppdragsEnhet()),
                                    oppdragslinje = utbetalingsoppdrag.utbetalingsperiode.map { tilOppdragslinje(it, utbetalingsoppdrag) },
                                ),
                        ),
                ),
        )

    private fun tilOppdrag(utbetalingsoppdrag: Utbetalingsoppdrag): Oppdrag =
        Oppdrag(
            kodeEndring = EndringsKode.fromKode(utbetalingsoppdrag.kodeEndring.name).kode,
            kodeFagomraade = utbetalingsoppdrag.fagSystem,
            fagsystemId = utbetalingsoppdrag.saksnummer,
            utbetFrekvens = UtbetalingsfrekvensKode.MÅNEDLIG.kode,
            oppdragGjelderId = utbetalingsoppdrag.aktoer,
            datoOppdragGjelderFom = OppdragSkjemaConstants.OPPDRAG_GJELDER_DATO_FOM.toString(),
            saksbehId = utbetalingsoppdrag.saksbehandlerId,
        )

    private fun oppdragsEnhet(): Enhet =
        Enhet(
            typeEnhet = OppdragSkjemaConstants.ENHET_TYPE,
            enhet = OppdragSkjemaConstants.ENHET,
            datoEnhetFom = OppdragSkjemaConstants.ENHET_DATO_FOM.toString(),
        )

    private fun tilOppdragslinje(
        utbetalingsperiode: Utbetalingsperiode,
        utbetalingsoppdrag: Utbetalingsoppdrag,
    ): Oppdragslinje {
        val erNyPeriode = !utbetalingsperiode.erEndringPåEksisterendePeriode
        val refDelytelseId = utbetalingsperiode.forrigePeriodeId?.takeIf { erNyPeriode }?.let { utbetalingsoppdrag.saksnummer + it }

        return Oppdragslinje(
            kodeEndringLinje = if (erNyPeriode) EndringsKode.NY.kode else EndringsKode.ENDRING.kode,
            kodeStatusLinje = utbetalingsperiode.opphør?.let { KODE_STATUS_LINJE_OPPHØR },
            datoStatusFom = utbetalingsperiode.opphør?.opphørDatoFom?.toString(),
            refDelytelseId = refDelytelseId,
            refFagsystemId = refDelytelseId?.let { utbetalingsoppdrag.saksnummer },
            vedtakId = utbetalingsperiode.datoForVedtak.toString(),
            delytelseId = utbetalingsoppdrag.saksnummer + utbetalingsperiode.periodeId,
            kodeKlassifik = utbetalingsperiode.klassifisering,
            datoVedtakFom = utbetalingsperiode.vedtakdatoFom.toString(),
            datoVedtakTom = utbetalingsperiode.vedtakdatoTom.toString(),
            sats = utbetalingsperiode.sats,
            typeSats = SatsTypeKode.fromKode(utbetalingsperiode.satsType.name).kode,
            brukKjoreplan = false,
            saksbehId = utbetalingsoppdrag.saksbehandlerId,
            utbetalesTilId = utbetalingsperiode.utbetalesTil,
            henvisning = utbetalingsperiode.behandlingId.toString(),
            attestant = listOf(Attestant(attestantId = utbetalingsoppdrag.saksbehandlerId)),
            gradOppdragslinje =
                utbetalingsperiode.utbetalingsgrad?.let {
                    listOf(Grad(typeGrad = GradTypeKode.UTBETALINGSGRAD.kode, grad = it))
                },
        )
    }

    companion object {
        private const val KODE_STATUS_LINJE_OPPHØR = "OPPH"
    }
}
