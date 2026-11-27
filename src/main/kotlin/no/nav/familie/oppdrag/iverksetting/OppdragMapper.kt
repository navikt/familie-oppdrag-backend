package no.nav.familie.oppdrag.iverksetting

import no.nav.familie.kontrakter.felles.oppdrag.Utbetalingsoppdrag
import no.nav.familie.kontrakter.felles.oppdrag.Utbetalingsperiode
import no.nav.familie.oppdrag.avstemming.AvstemmingMapper.fagområdeTilAvleverendeKomponentKode
import no.trygdeetaten.skjema.oppdrag.ObjectFactory
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import no.trygdeetaten.skjema.oppdrag.Oppdrag110
import no.trygdeetaten.skjema.oppdrag.OppdragsLinje150
import no.trygdeetaten.skjema.oppdrag.TkodeStatusLinje
import org.springframework.stereotype.Component
import java.time.format.DateTimeFormatter

@Component
class OppdragMapper {
    private val objectFactory = ObjectFactory()
    val tidspunktFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS")

    fun tilOppdrag110(utbetalingsoppdrag: Utbetalingsoppdrag): Oppdrag110 {
        val avstemming =
            objectFactory.createAvstemming115().apply {
                nokkelAvstemming = utbetalingsoppdrag.avstemmingTidspunkt.format(tidspunktFormatter)
                kodeKomponent = fagområdeTilAvleverendeKomponentKode(utbetalingsoppdrag.fagSystem)
                tidspktMelding = utbetalingsoppdrag.avstemmingTidspunkt.format(tidspunktFormatter)
            }

        val oppdragsEnhet =
            objectFactory.createOppdragsEnhet120().apply {
                enhet = OppdragSkjemaConstants.ENHET
                typeEnhet = OppdragSkjemaConstants.ENHET_TYPE
                datoEnhetFom = OppdragSkjemaConstants.ENHET_DATO_FOM.toXMLDate()
            }

        val oppdrag110 =
            objectFactory.createOppdrag110().apply {
                kodeAksjon = OppdragSkjemaConstants.KODE_AKSJON
                kodeEndring = EndringsKode.fromKode(utbetalingsoppdrag.kodeEndring.name).kode
                kodeFagomraade = utbetalingsoppdrag.fagSystem
                fagsystemId = utbetalingsoppdrag.saksnummer
                utbetFrekvens = UtbetalingsfrekvensKode.MÅNEDLIG.kode
                oppdragGjelderId = utbetalingsoppdrag.aktoer
                datoOppdragGjelderFom = OppdragSkjemaConstants.OPPDRAG_GJELDER_DATO_FOM.toXMLDate()
                saksbehId = utbetalingsoppdrag.saksbehandlerId
                avstemming115 = avstemming
                oppdragsEnhet120.add(oppdragsEnhet)
                utbetalingsoppdrag.utbetalingsperiode.map { periode ->
                    oppdragsLinje150.add(tilOppdragsLinje150(utbetalingsperiode = periode, utbetalingsoppdrag = utbetalingsoppdrag))
                }
            }

        return oppdrag110
    }

    private fun tilOppdragsLinje150(
        utbetalingsperiode: Utbetalingsperiode,
        utbetalingsoppdrag: Utbetalingsoppdrag,
    ): OppdragsLinje150 {
        val attestant =
            objectFactory.createAttestant180().apply {
                attestantId = utbetalingsoppdrag.saksbehandlerId
            }

        return objectFactory.createOppdragsLinje150().apply {
            kodeEndringLinje =
                if (utbetalingsperiode.erEndringPåEksisterendePeriode) EndringsKode.ENDRING.kode else EndringsKode.NY.kode
            utbetalingsperiode.opphør?.let {
                kodeStatusLinje = TkodeStatusLinje.OPPH
                datoStatusFom = it.opphørDatoFom.toXMLDate()
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
            datoVedtakFom = utbetalingsperiode.vedtakdatoFom.toXMLDate()
            datoVedtakTom = utbetalingsperiode.vedtakdatoTom?.toXMLDate()
            sats = utbetalingsperiode.sats
            fradragTillegg = OppdragSkjemaConstants.FRADRAG_TILLEGG
            typeSats = SatsTypeKode.fromKode(utbetalingsperiode.satsType.name).kode
            brukKjoreplan =
                if (utbetalingsoppdrag.gOmregning) {
                    OppdragSkjemaConstants.BRUK_KJØREPLAN_G_OMBEREGNING
                } else {
                    OppdragSkjemaConstants.BRUK_KJØREPLAN_DEFAULT
                }
            saksbehId = utbetalingsoppdrag.saksbehandlerId
            utbetalesTilId = utbetalingsperiode.utbetalesTil
            henvisning = utbetalingsperiode.behandlingId.toString()
            attestant180.add(attestant)

            utbetalingsperiode.utbetalingsgrad?.let { utbetalingsgrad ->
                grad170.add(
                    objectFactory.createGrad170().apply {
                        typeGrad = GradTypeKode.UTBETALINGSGRAD.kode
                        grad = utbetalingsgrad.toBigInteger()
                    },
                )
            }
        }
    }

    fun tilOppdrag(oppdrag110: Oppdrag110): Oppdrag =
        objectFactory.createOppdrag().apply {
            this.oppdrag110 = oppdrag110
        }
}
