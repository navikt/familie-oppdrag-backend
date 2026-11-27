package no.nav.familie.oppdrag.konsistensavstemming

import no.nav.familie.kontrakter.felles.oppdrag.Utbetalingsoppdrag
import no.nav.familie.kontrakter.felles.oppdrag.Utbetalingsperiode
import no.nav.familie.oppdrag.avstemming.AvstemmingMapper
import no.nav.familie.oppdrag.avstemming.SystemKode
import no.nav.familie.oppdrag.iverksetting.GradTypeKode
import no.nav.familie.oppdrag.iverksetting.OppdragSkjemaConstants
import no.nav.familie.oppdrag.iverksetting.SatsTypeKode
import no.nav.familie.oppdrag.iverksetting.UtbetalingsfrekvensKode
import no.nav.virksomhet.tjenester.avstemming.informasjon.konsistensavstemmingsdata.v1.Aksjonsdata
import no.nav.virksomhet.tjenester.avstemming.informasjon.konsistensavstemmingsdata.v1.Attestant
import no.nav.virksomhet.tjenester.avstemming.informasjon.konsistensavstemmingsdata.v1.Enhet
import no.nav.virksomhet.tjenester.avstemming.informasjon.konsistensavstemmingsdata.v1.Grad
import no.nav.virksomhet.tjenester.avstemming.informasjon.konsistensavstemmingsdata.v1.Konsistensavstemmingsdata
import no.nav.virksomhet.tjenester.avstemming.informasjon.konsistensavstemmingsdata.v1.Oppdragsdata
import no.nav.virksomhet.tjenester.avstemming.informasjon.konsistensavstemmingsdata.v1.Oppdragslinje
import no.nav.virksomhet.tjenester.avstemming.informasjon.konsistensavstemmingsdata.v1.Periode
import no.nav.virksomhet.tjenester.avstemming.informasjon.konsistensavstemmingsdata.v1.Totaldata
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class KonsistensavstemmingMapper(
    private val fagsystem: String,
    private val utbetalingsoppdrag: List<Utbetalingsoppdrag>,
    private val avstemmingsDato: LocalDateTime,
    private var aggregertTotalBeløp: Long,
    private var aggregertAntallOppdrag: Int,
    private val sendStartmelding: Boolean,
    private val sendAvsluttmelding: Boolean,
    transaksjonsId: UUID? = UUID.randomUUID(),
) {
    private val tidspunktFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS")
    private val datoFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val avstemmingId = AvstemmingMapper.encodeUUIDBase64(transaksjonsId ?: UUID.randomUUID())
    var totalBeløp = 0L
    var antallOppdrag = 0
    private val behandledeSaker = mutableSetOf<String>()

    fun lagAvstemmingsmeldinger(): List<Konsistensavstemmingsdata> =
        when {
            sendStartmelding && sendAvsluttmelding -> (
                listOf(lagStartmelding()) + lagDatameldinger() +
                    listOf(
                        lagTotaldata(),
                        lagSluttmelding(),
                    )
            )

            sendStartmelding -> (listOf(lagStartmelding()) + lagDatameldinger())

            sendAvsluttmelding -> (lagDatameldinger() + listOf(lagTotaldata(), lagSluttmelding()))

            else -> lagDatameldinger()
        }

    private fun lagStartmelding() = lagAksjonsmelding(KonsistensavstemmingConstants.START)

    private fun lagSluttmelding() = lagAksjonsmelding(KonsistensavstemmingConstants.AVSLUTT)

    private fun lagDatameldinger(): List<Konsistensavstemmingsdata> {
        val dataListe: MutableList<Konsistensavstemmingsdata> = arrayListOf()

        for (utbetalingsoppdrag in utbetalingsoppdrag) {
            if (!behandledeSaker.add(utbetalingsoppdrag.saksnummer)) {
                error("Har allerede lagt til ${utbetalingsoppdrag.saksnummer} i listen over avstemminger")
            }

            val konsistensavstemmingsdata = lagAksjonsmelding(KonsistensavstemmingConstants.DATA)
            konsistensavstemmingsdata.apply {
                oppdragsdataListe.add(lagOppdragsdata(utbetalingsoppdrag))
            }
            dataListe.add(konsistensavstemmingsdata)
        }
        antallOppdrag = dataListe.size
        return dataListe
    }

    private fun lagOppdragsdata(utbetalingsoppdrag: Utbetalingsoppdrag): Oppdragsdata =
        Oppdragsdata().apply {
            fagomradeKode = utbetalingsoppdrag.fagSystem
            fagsystemId = utbetalingsoppdrag.saksnummer
            utbetalingsfrekvens = UtbetalingsfrekvensKode.MÅNEDLIG.kode
            oppdragGjelderId = utbetalingsoppdrag.aktoer
            oppdragGjelderFom = OppdragSkjemaConstants.OPPDRAG_GJELDER_DATO_FOM.format(datoFormatter)
            saksbehandlerId = utbetalingsoppdrag.saksbehandlerId
            oppdragsenhetListe.add(lagEnhet())
            utbetalingsoppdrag.utbetalingsperiode
                .filter { verifiserAtPerioderErAktiv(it) }
                .map { periode ->
                    oppdragslinjeListe.add(
                        lagOppdragsLinjeListe(
                            utbetalingsperiode = periode,
                            utbetalingsoppdrag = utbetalingsoppdrag,
                        ),
                    )
                }
        }

    private fun lagOppdragsLinjeListe(
        utbetalingsperiode: Utbetalingsperiode,
        utbetalingsoppdrag: Utbetalingsoppdrag,
    ): Oppdragslinje {
        totalBeløp += utbetalingsperiode.sats.toLong()
        return Oppdragslinje().apply {
            vedtakId = utbetalingsperiode.datoForVedtak.format(datoFormatter)
            delytelseId = utbetalingsoppdrag.saksnummer + utbetalingsperiode.periodeId
            utbetalingsperiode.forrigePeriodeId?.let {
                refDelytelseId = utbetalingsoppdrag.saksnummer + it
            }
            klassifikasjonKode = utbetalingsperiode.klassifisering
            vedtakPeriode =
                Periode().apply {
                    fom = utbetalingsperiode.vedtakdatoFom.format(datoFormatter)
                    tom = utbetalingsperiode.vedtakdatoTom?.format(datoFormatter)
                }
            sats = utbetalingsperiode.sats
            satstypeKode = SatsTypeKode.fromKode(utbetalingsperiode.satsType.name).kode
            brukKjoreplan = OppdragSkjemaConstants.BRUK_KJØREPLAN_DEFAULT
            fradragTillegg = OppdragSkjemaConstants.FRADRAG_TILLEGG.value()
            saksbehandlerId = utbetalingsoppdrag.saksbehandlerId
            utbetalesTilId = utbetalingsperiode.utbetalesTil
            henvisning = utbetalingsperiode.behandlingId.toString()
            attestantListe.add(lagAttestant(utbetalingsoppdrag))

            utbetalingsperiode.utbetalingsgrad?.let { utbetalingsgrad ->
                gradListe.add(
                    Grad().apply {
                        gradKode = GradTypeKode.UTBETALINGSGRAD.kode
                        grad = utbetalingsgrad
                    },
                )
            }
        }
    }

    private fun verifiserAtPerioderErAktiv(utbetalingsperiode: Utbetalingsperiode): Boolean {
        val avstemmingsdato = avstemmingsDato.toLocalDate()
        val vedtakdatoTom = utbetalingsperiode.vedtakdatoTom
        val aktiv = vedtakdatoTom == null || !vedtakdatoTom.isBefore(avstemmingsdato)
        if (!aktiv) {
            LOG.error(
                "fagsystem=$fagsystem vedtakdatoTom=$vedtakdatoTom (periodens tom-dato) " +
                    "er etter avstemmingsdato=$avstemmingsdato for" +
                    " periodeId=${utbetalingsperiode.periodeId} behandlingId=${utbetalingsperiode.behandlingId}",
            )
        }
        return aktiv
    }

    private fun lagAttestant(utbetalingsoppdrag: Utbetalingsoppdrag): Attestant =
        Attestant().apply {
            attestantId = utbetalingsoppdrag.saksbehandlerId
        }

    private fun lagEnhet(): Enhet =
        Enhet().apply {
            enhetType = OppdragSkjemaConstants.ENHET_TYPE
            enhet = OppdragSkjemaConstants.ENHET
            enhetFom = OppdragSkjemaConstants.ENHET_DATO_FOM.format(datoFormatter)
        }

    private fun lagTotaldata(): Konsistensavstemmingsdata {
        val konsistensavstemmingsdata = lagAksjonsmelding(KonsistensavstemmingConstants.DATA)
        konsistensavstemmingsdata.apply {
            totaldata =
                Totaldata().apply {
                    totalAntall = antallOppdrag.toBigInteger() + aggregertAntallOppdrag.toBigInteger()
                    totalBelop = BigDecimal.valueOf(totalBeløp) + BigDecimal.valueOf(aggregertTotalBeløp)
                    fortegn = getFortegn(totalBeløp + aggregertTotalBeløp)
                }
        }
        return konsistensavstemmingsdata
    }

    private fun getFortegn(satsbeløp: Long): String =
        if (BigDecimal.valueOf(satsbeløp) >= BigDecimal.ZERO) {
            KonsistensavstemmingConstants.FORTEGN_T
        } else {
            KonsistensavstemmingConstants.FORTEGN_F
        }

    private fun lagAksjonsmelding(aksjontype: String): Konsistensavstemmingsdata =
        Konsistensavstemmingsdata().apply {
            aksjonsdata = opprettAksjonsdata(aksjontype)
        }

    private fun opprettAksjonsdata(aksjonstype: String): Aksjonsdata =
        Aksjonsdata().apply {
            this.aksjonsType = aksjonstype
            this.kildeType = KonsistensavstemmingConstants.KILDETYPE
            this.avstemmingType = KonsistensavstemmingConstants.KONSISTENSAVSTEMMING
            this.avleverendeKomponentKode = AvstemmingMapper.fagområdeTilAvleverendeKomponentKode(fagsystem)
            this.mottakendeKomponentKode = SystemKode.OPPDRAGSSYSTEMET.kode
            this.underkomponentKode = fagsystem
            this.tidspunktAvstemmingTom = avstemmingsDato.format(tidspunktFormatter)
            this.avleverendeAvstemmingId = avstemmingId
            this.brukerId = fagsystem
        }

    companion object {
        val LOG: Logger = LoggerFactory.getLogger(KonsistensavstemmingMapper::class.java)
    }
}
