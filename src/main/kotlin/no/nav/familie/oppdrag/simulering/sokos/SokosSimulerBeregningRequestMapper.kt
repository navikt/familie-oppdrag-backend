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

object SokosSimulerBeregningRequestMapper {
    fun tilSokosSimulerBeregningRequest(utbetalingsoppdrag: Utbetalingsoppdrag): SokosSimulerBeregningRequest {
        val oppdrag =
            Oppdrag(
                kodeEndring = EndringsKode.fromKode(utbetalingsoppdrag.kodeEndring.name).kode,
                kodeFagomraade = utbetalingsoppdrag.fagSystem,
                fagsystemId = utbetalingsoppdrag.saksnummer,
                utbetFrekvens = UtbetalingsfrekvensKode.MÅNEDLIG.kode,
                oppdragGjelderId = utbetalingsoppdrag.aktoer,
                datoOppdragGjelderFom = OppdragSkjemaConstants.OPPDRAG_GJELDER_DATO_FOM.toString(),
                saksbehId = utbetalingsoppdrag.saksbehandlerId,
            )
        val enhet =
            Enhet(
                typeEnhet = OppdragSkjemaConstants.ENHET_TYPE,
                enhet = OppdragSkjemaConstants.ENHET,
                datoEnhetFom = OppdragSkjemaConstants.ENHET_DATO_FOM.toString(),
            )
        val oppdragslinjer = utbetalingsoppdrag.utbetalingsperiode.map { tilOppdragslinje(it, utbetalingsoppdrag) }
        return SokosSimulerBeregningRequest(Operation(Container(RequestSimulering(oppdrag, listOf(enhet), oppdragslinjer))))
    }

    private fun tilOppdragslinje(
        periode: Utbetalingsperiode,
        utbetalingsoppdrag: Utbetalingsoppdrag,
    ): Oppdragslinje {
        // En endring på en eksisterende periode skal ikke referere til forrige periode.
        val refPeriodeId = periode.forrigePeriodeId.takeUnless { periode.erEndringPåEksisterendePeriode }

        return Oppdragslinje(
            kodeEndringLinje = if (periode.erEndringPåEksisterendePeriode) EndringsKode.ENDRING.kode else EndringsKode.NY.kode,
            kodeStatusLinje = periode.opphør?.let { "OPPH" },
            datoStatusFom = periode.opphør?.opphørDatoFom?.toString(),
            vedtakId = periode.datoForVedtak.toString(),
            delytelseId = utbetalingsoppdrag.saksnummer + periode.periodeId,
            kodeKlassifik = periode.klassifisering,
            datoVedtakFom = periode.vedtakdatoFom.toString(),
            datoVedtakTom = periode.vedtakdatoTom.toString(),
            sats = periode.sats,
            typeSats = SatsTypeKode.fromKode(periode.satsType.name).kode,
            brukKjoreplan = false,
            saksbehId = utbetalingsoppdrag.saksbehandlerId,
            utbetalesTilId = periode.utbetalesTil,
            henvisning = periode.behandlingId.toString(),
            refFagsystemId = refPeriodeId?.let { utbetalingsoppdrag.saksnummer },
            refDelytelseId = refPeriodeId?.let { utbetalingsoppdrag.saksnummer + it },
            attestant = listOf(Attestant(utbetalingsoppdrag.saksbehandlerId)),
            gradOppdragslinje = periode.utbetalingsgrad?.let { listOf(Grad(GradTypeKode.UTBETALINGSGRAD.kode, it)) },
        )
    }
}
