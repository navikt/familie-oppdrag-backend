package no.nav.familie.oppdrag.simulering

import no.nav.familie.kontrakter.felles.oppdrag.Utbetalingsoppdrag
import no.nav.familie.kontrakter.felles.oppdrag.Utbetalingsperiode
import no.nav.familie.oppdrag.iverksetting.EndringsKode
import no.nav.familie.oppdrag.iverksetting.GradTypeKode
import no.nav.familie.oppdrag.iverksetting.OppdragSkjemaConstants
import no.nav.familie.oppdrag.iverksetting.SatsTypeKode
import no.nav.familie.oppdrag.iverksetting.UtbetalingsfrekvensKode
import no.nav.system.os.entiteter.typer.simpletypes.FradragTillegg
import no.nav.system.os.entiteter.typer.simpletypes.KodeStatusLinje
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.Oppdrag
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.Oppdragslinje
import org.springframework.stereotype.Component
import java.time.format.DateTimeFormatter

@Component
class SimulerBeregningRequestMapper {
    private val fpServiceGrensesnittFactory =
        no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt
            .ObjectFactory()
    private val fpServiceTypesFactory =
        no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes
            .ObjectFactory()
    private val oppdragSkjemaFactory =
        no.nav.system.os.entiteter.oppdragskjema
            .ObjectFactory()

    private val tidspunktFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS")

    fun tilSimulerBeregningRequest(utbetalingsoppdrag: Utbetalingsoppdrag): SimulerBeregningRequest =
        fpServiceGrensesnittFactory
            .createSimulerBeregningRequest()
            .apply { request = tilSimulerBeregningTypesRequest(utbetalingsoppdrag) }

    private fun tilSimulerBeregningTypesRequest(utbetalingsoppdrag: Utbetalingsoppdrag) =
        fpServiceTypesFactory.createSimulerBeregningRequest().apply { oppdrag = tilOppdrag(utbetalingsoppdrag) }

    private fun tilOppdrag(utbetalingsoppdrag: Utbetalingsoppdrag): Oppdrag {
        oppdragSkjemaFactory.createAvstemmingsnokkel().apply {
            avstemmingsNokkel = utbetalingsoppdrag.avstemmingTidspunkt.format(tidspunktFormatter)
            kodeKomponent = utbetalingsoppdrag.fagSystem
            tidspktReg = utbetalingsoppdrag.avstemmingTidspunkt.format(tidspunktFormatter)
        }

        val oppdragsEnhet =
            oppdragSkjemaFactory.createEnhet().apply {
                enhet = OppdragSkjemaConstants.ENHET
                typeEnhet = OppdragSkjemaConstants.ENHET_TYPE
                datoEnhetFom = OppdragSkjemaConstants.ENHET_DATO_FOM.toString()
            }

        return fpServiceTypesFactory.createOppdrag().apply {
            kodeEndring = EndringsKode.fromKode(utbetalingsoppdrag.kodeEndring.name).kode
            kodeFagomraade = utbetalingsoppdrag.fagSystem
            fagsystemId = utbetalingsoppdrag.saksnummer
            utbetFrekvens = UtbetalingsfrekvensKode.MÅNEDLIG.kode
            oppdragGjelderId = utbetalingsoppdrag.aktoer
            datoOppdragGjelderFom = OppdragSkjemaConstants.OPPDRAG_GJELDER_DATO_FOM.toString()
            saksbehId = utbetalingsoppdrag.saksbehandlerId
            enhet.add(oppdragsEnhet)
            avstemmingsnokkel.addAll(avstemmingsnokkel)

            utbetalingsoppdrag.utbetalingsperiode.map { periode ->
                oppdragslinje.add(tilOppdragsLinje(utbetalingsperiode = periode, utbetalingsoppdrag = utbetalingsoppdrag))
            }
        }
    }

    private fun tilOppdragsLinje(
        utbetalingsperiode: Utbetalingsperiode,
        utbetalingsoppdrag: Utbetalingsoppdrag,
    ): Oppdragslinje {
        val attest =
            oppdragSkjemaFactory.createAttestant().apply {
                attestantId = utbetalingsoppdrag.saksbehandlerId
            }

        return fpServiceTypesFactory.createOppdragslinje().apply {
            kodeEndringLinje =
                if (utbetalingsperiode.erEndringPåEksisterendePeriode) EndringsKode.ENDRING.kode else EndringsKode.NY.kode
            utbetalingsperiode.opphør?.let {
                kodeStatusLinje = KodeStatusLinje.OPPH
                datoStatusFom = it.opphørDatoFom.toString()
            }
            if (!utbetalingsperiode.erEndringPåEksisterendePeriode) {
                utbetalingsperiode.forrigePeriodeId?.let {
                    refDelytelseId = utbetalingsoppdrag.saksnummer + it
                    refFagsystemId = utbetalingsoppdrag.saksnummer
                }
            }
            vedtakId = utbetalingsperiode.datoForVedtak.toString()
            delytelseId = utbetalingsoppdrag.saksnummer + utbetalingsperiode.periodeId
            kodeKlassifik = utbetalingsperiode.klassifisering
            datoVedtakFom = utbetalingsperiode.vedtakdatoFom.toString()
            datoVedtakTom = utbetalingsperiode.vedtakdatoTom?.toString()
            sats = utbetalingsperiode.sats
            fradragTillegg = FradragTillegg.T
            typeSats = SatsTypeKode.fromKode(utbetalingsperiode.satsType.name).kode
            brukKjoreplan = OppdragSkjemaConstants.BRUK_KJØREPLAN_DEFAULT
            saksbehId = utbetalingsoppdrag.saksbehandlerId
            utbetalesTilId = utbetalingsperiode.utbetalesTil
            henvisning = utbetalingsperiode.behandlingId.toString()
            attestant.add(attest)

            utbetalingsperiode.utbetalingsgrad?.let { utbetalingsgrad ->
                grad.add(
                    oppdragSkjemaFactory.createGrad().apply {
                        typeGrad = GradTypeKode.UTBETALINGSGRAD.kode
                        grad = utbetalingsgrad.toBigInteger()
                    },
                )
            }
        }
    }
}
