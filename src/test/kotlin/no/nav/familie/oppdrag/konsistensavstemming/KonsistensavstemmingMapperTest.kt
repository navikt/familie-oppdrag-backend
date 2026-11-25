package no.nav.familie.oppdrag.konsistensavstemming

import no.nav.familie.kontrakter.felles.oppdrag.Utbetalingsoppdrag
import no.nav.familie.kontrakter.felles.oppdrag.Utbetalingsperiode
import no.nav.familie.oppdrag.avstemming.SystemKode
import no.nav.familie.oppdrag.iverksetting.OppdragSkjemaConstants
import no.nav.familie.oppdrag.iverksetting.SatsTypeKode
import no.nav.familie.oppdrag.iverksetting.UtbetalingsfrekvensKode
import no.nav.familie.oppdrag.util.TestOppdragMedAvstemmingsdato.lagTestUtbetalingsoppdrag
import no.nav.familie.oppdrag.util.TestOppdragMedAvstemmingsdato.lagUtbetalingsperiode
import no.nav.virksomhet.tjenester.avstemming.informasjon.konsistensavstemmingsdata.v1.Aksjonsdata
import no.nav.virksomhet.tjenester.avstemming.informasjon.konsistensavstemmingsdata.v1.Enhet
import no.nav.virksomhet.tjenester.avstemming.informasjon.konsistensavstemmingsdata.v1.Konsistensavstemmingsdata
import no.nav.virksomhet.tjenester.avstemming.informasjon.konsistensavstemmingsdata.v1.Oppdragsdata
import no.nav.virksomhet.tjenester.avstemming.informasjon.konsistensavstemmingsdata.v1.Oppdragslinje
import no.nav.virksomhet.tjenester.avstemming.informasjon.konsistensavstemmingsdata.v1.Totaldata
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class KonsistensavstemmingMapperTest {
    private val fagområde = "BA"
    private val idag: LocalDateTime = LocalDateTime.now()
    private val tidspunktFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS")
    private val datoFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    @Test
    fun `løpende periode tas med i avstemming uten sluttdato`() {
        val utbetalingsperiode = lagUtbetalingsperiode(beløp = 100).copy(vedtakdatoTom = null)
        val utbetalingsoppdrag = lagTestUtbetalingsoppdrag(idag, fagområde, "1", utbetalingsperiode)
        val mapper = KonsistensavstemmingMapper(fagområde, listOf(utbetalingsoppdrag), idag.plusYears(10), 0, 0, true, true)

        val meldinger = mapper.lagAvstemmingsmeldinger()

        val oppdragslinje =
            meldinger[1]
                .oppdragsdataListe
                .single()
                .oppdragslinjeListe
                .single()
        assertThat(oppdragslinje.vedtakPeriode.tom).isNull()
        assertThat(oppdragslinje.vedtakPeriode.fom).isEqualTo(utbetalingsperiode.vedtakdatoFom.format(datoFormatter))
        assertEquals(100.toBigDecimal(), meldinger[2].totaldata.totalBelop)
    }

    @Test
    fun `Tester at det mappes riktig til konsistensavstemming`() {
        val (utbetalingsoppdrag, meldinger) = lagMeldinger(inkluderStartmelding = true, inkluderAvsluttmelding = true)
        assertEquals(4, meldinger.size)
        // START-meldingen
        assertAksjon(KonsistensavstemmingConstants.START, meldinger.first().aksjonsdata)
        // DATA-meldingen
        assertAksjon(KonsistensavstemmingConstants.DATA, meldinger[1].aksjonsdata)
        assertOppdragsdata(utbetalingsoppdrag, meldinger[1].oppdragsdataListe.first())
        // TOTALDATA-meldingen
        assertAksjon(KonsistensavstemmingConstants.DATA, meldinger[2].aksjonsdata)
        assertTotaldata(utbetalingsoppdrag.utbetalingsperiode.first(), meldinger[2].totaldata)
        // AVSLUTT-meldingen
        assertAksjon(KonsistensavstemmingConstants.AVSLUTT, meldinger.last().aksjonsdata)
    }

    @Test
    fun `Tester at det mappes riktig til konsistensavstemming uten startmelding`() {
        val (utbetalingsoppdrag, meldinger) = lagMeldinger(inkluderStartmelding = false, inkluderAvsluttmelding = true)
        assertEquals(3, meldinger.size)
        // DATA-meldingen
        assertAksjon(KonsistensavstemmingConstants.DATA, meldinger[0].aksjonsdata)
        assertOppdragsdata(utbetalingsoppdrag, meldinger[0].oppdragsdataListe.first())
        // TOTALDATA-meldingen
        assertAksjon(KonsistensavstemmingConstants.DATA, meldinger[1].aksjonsdata)
        assertTotaldata(utbetalingsoppdrag.utbetalingsperiode.first(), meldinger[1].totaldata)
        // AVSLUTT-meldingen
        assertAksjon(KonsistensavstemmingConstants.AVSLUTT, meldinger.last().aksjonsdata)
    }

    @Test
    fun `Tester at det mappes riktig til konsistensavstemming uten avsluttmelding`() {
        val (utbetalingsoppdrag, meldinger) = lagMeldinger(inkluderStartmelding = true, inkluderAvsluttmelding = false)
        assertEquals(2, meldinger.size)
        // START-meldingen
        assertAksjon(KonsistensavstemmingConstants.START, meldinger.first().aksjonsdata)
        // DATA-meldingen
        assertAksjon(KonsistensavstemmingConstants.DATA, meldinger[1].aksjonsdata)
        assertOppdragsdata(utbetalingsoppdrag, meldinger[1].oppdragsdataListe.first())
    }

    @Test
    fun `Tester at det mappes riktig til konsistensavstemming uten starte- og avsluttmelding`() {
        val (utbetalingsoppdrag, meldinger) = lagMeldinger(inkluderStartmelding = false, inkluderAvsluttmelding = false)
        assertEquals(1, meldinger.size)
        // DATA-meldingen
        assertAksjon(KonsistensavstemmingConstants.DATA, meldinger[0].aksjonsdata)
        assertOppdragsdata(utbetalingsoppdrag, meldinger[0].oppdragsdataListe.first())
    }

    @Test
    fun `totaldata skal akkumulere totalbeløp på alle perioder og totalAntall på alle oppdrag`() {
        val utbetalingsoppdrag =
            lagTestUtbetalingsoppdrag(
                idag,
                fagområde,
                "1",
                lagUtbetalingsperiode(beløp = 100),
                lagUtbetalingsperiode(beløp = 200),
            )
        val utbetalingsoppdrag2 = lagTestUtbetalingsoppdrag(idag, fagområde, "2", lagUtbetalingsperiode(beløp = 50))
        val mapper =
            KonsistensavstemmingMapper(
                fagområde,
                listOf(utbetalingsoppdrag, utbetalingsoppdrag2),
                idag,
                0,
                0,
                true,
                true,
            )
        val meldinger = mapper.lagAvstemmingsmeldinger()
        assertEquals(5, meldinger.size)
        assertEquals(KonsistensavstemmingConstants.DATA, meldinger[3].aksjonsdata.aksjonsType)
        assertEquals(BigInteger.TWO, meldinger[3].totaldata.totalAntall)
        assertEquals(350.toBigDecimal(), meldinger[3].totaldata.totalBelop)
    }

    @Test
    fun `skal ikke lage melding hvis periode ikke er aktiv`() {
        val utbetalingsperiode =
            lagUtbetalingsperiode(
                fagområde,
                1,
                100,
                LocalDate.now().minusYears(1),
                LocalDate.now().minusYears(1),
            )
        val utbetalingsoppdrag = lagTestUtbetalingsoppdrag(idag.plusYears(7), fagområde, "1", utbetalingsperiode)
        val mapper = KonsistensavstemmingMapper(fagområde, listOf(utbetalingsoppdrag), idag, 0, 0, true, true)
        val meldinger = mapper.lagAvstemmingsmeldinger()
        assertThat(meldinger).hasSize(4)
        assertThat(meldinger[1].oppdragsdataListe).hasSize(1)
        assertThat(meldinger[1].oppdragsdataListe[0].oppdragslinjeListe).isEmpty()
        assertEquals(BigInteger.ONE, meldinger[2].totaldata.totalAntall)
        assertEquals(BigDecimal.ZERO, meldinger[2].totaldata.totalBelop)
    }

    @Test
    internal fun `skal kaste feil hvis det finnes 2 utbetalingsoppdrag med samme saksnummer`() {
        val utbetalingsoppdrag = lagTestUtbetalingsoppdrag(idag.plusYears(7), fagområde, "1")
        val utbetalingsoppdrag2 = lagTestUtbetalingsoppdrag(idag.plusYears(7), fagområde, "1")
        val mapper =
            KonsistensavstemmingMapper(
                fagområde,
                listOf(utbetalingsoppdrag, utbetalingsoppdrag2),
                idag,
                0,
                0,
                true,
                true,
            )

        assertThat(catchThrowable { mapper.lagAvstemmingsmeldinger() })
            .hasMessage("Har allerede lagt til 1 i listen over avstemminger")
    }

    private fun assertAksjon(
        expected: String,
        actual: Aksjonsdata,
    ) {
        assertEquals(expected, actual.aksjonsType)
        assertEquals(KonsistensavstemmingConstants.KILDETYPE, actual.kildeType)
        assertEquals(KonsistensavstemmingConstants.KONSISTENSAVSTEMMING, actual.avstemmingType)
        assertEquals(fagområde, actual.avleverendeKomponentKode)
        assertEquals(SystemKode.OPPDRAGSSYSTEMET.kode, actual.mottakendeKomponentKode)
        assertEquals(fagområde, actual.underkomponentKode)
        assertEquals(idag.format(tidspunktFormatter), actual.tidspunktAvstemmingTom)
        assertEquals(fagområde, actual.brukerId)
    }

    private fun assertTotaldata(
        utbetalingsperiode: Utbetalingsperiode,
        actual: Totaldata,
    ) {
        assertEquals(BigInteger.ONE, actual.totalAntall)
        assertEquals(utbetalingsperiode.sats, actual.totalBelop)
        assertEquals(KonsistensavstemmingConstants.FORTEGN_T, actual.fortegn)
    }

    private fun assertOppdragsdata(
        utbetalingsoppdrag: Utbetalingsoppdrag,
        actual: Oppdragsdata,
    ) {
        assertEquals(fagområde, actual.fagomradeKode)
        assertEquals(utbetalingsoppdrag.saksnummer, actual.fagsystemId)
        assertEquals(UtbetalingsfrekvensKode.MÅNEDLIG.kode, actual.utbetalingsfrekvens)
        assertEquals(utbetalingsoppdrag.aktoer, actual.oppdragGjelderId)
        assertEquals(OppdragSkjemaConstants.OPPDRAG_GJELDER_DATO_FOM.format(datoFormatter), actual.oppdragGjelderFom)
        assertEquals(utbetalingsoppdrag.saksbehandlerId, actual.saksbehandlerId)
        assertEnhet(actual.oppdragsenhetListe.first())
        assertOppdragsLinjeListe(
            utbetalingsoppdrag.utbetalingsperiode.first(),
            utbetalingsoppdrag.saksbehandlerId,
            actual.oppdragslinjeListe.first(),
        )
    }

    private fun assertOppdragsLinjeListe(
        utbetalingsperiode: Utbetalingsperiode,
        saksbehandler: String,
        actual: Oppdragslinje,
    ) {
        assertEquals(utbetalingsperiode.datoForVedtak.format(datoFormatter), actual.vedtakId)
        assertEquals(utbetalingsperiode.klassifisering, actual.klassifikasjonKode)
        assertEquals(utbetalingsperiode.vedtakdatoFom.format(datoFormatter), actual.vedtakPeriode.fom)
        assertEquals(utbetalingsperiode.vedtakdatoTom?.format(datoFormatter), actual.vedtakPeriode.tom)
        assertEquals(utbetalingsperiode.sats, actual.sats)
        assertEquals(SatsTypeKode.fromKode(utbetalingsperiode.satsType.name).kode, actual.satstypeKode)
        assertEquals(OppdragSkjemaConstants.BRUK_KJØREPLAN_DEFAULT, actual.brukKjoreplan)
        assertEquals(OppdragSkjemaConstants.FRADRAG_TILLEGG.value(), actual.fradragTillegg)
        assertEquals(saksbehandler, actual.saksbehandlerId)
        assertEquals(utbetalingsperiode.utbetalesTil, actual.utbetalesTilId)
        assertEquals(utbetalingsperiode.behandlingId.toString(), actual.henvisning)
        assertEquals(saksbehandler, actual.attestantListe.first().attestantId)

        assertNotNull(utbetalingsperiode.utbetalingsgrad)
        assertEquals(utbetalingsperiode.utbetalingsgrad, actual.gradListe.firstOrNull()?.grad)
    }

    private fun assertEnhet(enhet: Enhet) {
        assertEquals(OppdragSkjemaConstants.ENHET_TYPE, enhet.enhetType)
        assertEquals(OppdragSkjemaConstants.ENHET, enhet.enhet)
        assertEquals(OppdragSkjemaConstants.ENHET_DATO_FOM.format(datoFormatter), enhet.enhetFom)
    }

    private fun lagMeldinger(
        inkluderStartmelding: Boolean,
        inkluderAvsluttmelding: Boolean,
    ): Pair<Utbetalingsoppdrag, List<Konsistensavstemmingsdata>> {
        val utbetalingsoppdrag = lagTestUtbetalingsoppdrag(idag, fagområde)
        val mapper =
            KonsistensavstemmingMapper(
                fagområde,
                listOf(utbetalingsoppdrag),
                idag,
                0,
                0,
                inkluderStartmelding,
                inkluderAvsluttmelding,
            )
        val meldinger = mapper.lagAvstemmingsmeldinger()
        return Pair(utbetalingsoppdrag, meldinger)
    }
}
