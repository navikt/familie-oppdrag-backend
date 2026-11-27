package no.nav.familie.oppdrag.grensesnittavstemming

import no.nav.familie.kontrakter.felles.oppdrag.OppdragStatus
import no.nav.familie.kontrakter.felles.oppdrag.Utbetalingsoppdrag
import no.nav.familie.oppdrag.avstemming.SystemKode
import no.nav.familie.oppdrag.repository.OppdragTilAvstemming
import no.nav.familie.oppdrag.repository.somAvstemming
import no.nav.familie.oppdrag.util.TestOppdragMedAvstemmingsdato.lagTestUtbetalingsoppdrag
import no.nav.familie.oppdrag.util.TestOppdragMedAvstemmingsdato.lagUtbetalingsperiode
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.AksjonType
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.Aksjonsdata
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.AvstemmingType
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.DetaljType
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.Detaljdata
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.Fortegn
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.Grunnlagsdata
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.KildeType
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.Periodedata
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.Totaldata
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals

class GrensesnittavstemmingMapperTest {
    val fagområde = "BA"
    val tidspunktFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS")

    @Test
    fun testMappingAvTomListe() {
        val mapper = GrensesnittavstemmingMapper(fagområde, LocalDateTime.now(), LocalDateTime.now())
        assertThatThrownBy { mapper.lagAvstemmingsmeldinger(emptyList()) }
            .hasMessageContaining("Kan ikke lage avstemminger med tom liste")
    }

    @Test
    fun testMappingTilGrensesnittavstemming() {
        val avstemmingstidspunkt = LocalDateTime.now().minusDays(1).withHour(13)
        val avstemmingFom = avstemmingstidspunkt.toLocalDate().atStartOfDay()
        val avstemmingTom = avstemmingstidspunkt.toLocalDate().atTime(LocalTime.MAX)
        val utbetalingsoppdrag = lagTestUtbetalingsoppdrag(avstemmingstidspunkt, fagområde)
        val oppdragLager = utbetalingsoppdrag.somAvstemming
        val mapper = GrensesnittavstemmingMapper(fagområde, avstemmingFom, avstemmingTom)
        val meldinger = mapper.lagAlleMeldinger(listOf(oppdragLager))
        assertEquals(4, meldinger.size)
        val datamelding = meldinger[1]
        val totalmelding = meldinger[2]

        assertAksjon(avstemmingFom, avstemmingTom, AksjonType.START, meldinger[0].aksjon)
        assertAksjon(avstemmingFom, avstemmingTom, AksjonType.DATA, datamelding.aksjon)
        assertAksjon(avstemmingFom, avstemmingTom, AksjonType.DATA, totalmelding.aksjon)
        assertAksjon(avstemmingFom, avstemmingTom, AksjonType.AVSL, meldinger.last().aksjon)

        assertThat(meldinger[0].detalj).isEmpty()
        assertThat(datamelding.detalj).hasSize(1)
        assertThat(totalmelding.detalj).isEmpty()
        assertThat(meldinger[3].detalj).isEmpty()

        assertDetaljData(utbetalingsoppdrag, datamelding.detalj.single())

        assertTotalData(utbetalingsoppdrag, totalmelding.total)
        assertPeriodeData(utbetalingsoppdrag, totalmelding.periode)
        assertGrunnlagsdata(utbetalingsoppdrag, totalmelding.grunnlag)
    }

    @Test
    fun `skal summere flere batcher med oppdrag`() {
        val now = LocalDateTime.now()
        val periode = lagUtbetalingsperiode()
        val oppdrag = lagTestUtbetalingsoppdrag(now.minusDays(1), fagområde, utbetalingsperiode = arrayOf(periode))
        val oppdrag2 = lagTestUtbetalingsoppdrag(now.plusDays(1), fagområde, utbetalingsperiode = arrayOf(periode))
        val oppdrag3 = lagTestUtbetalingsoppdrag(now, fagområde, utbetalingsperiode = arrayOf(periode, periode))

        val mapper = GrensesnittavstemmingMapper(fagområde, now.withHour(0), now.withHour(23))
        listOf(oppdrag, oppdrag2, oppdrag3)
            .forEach { mapper.lagAvstemmingsmeldinger(listOf(it.somAvstemming.copy(status = OppdragStatus.KVITTERT_OK))) }

        val totalmelding = mapper.lagTotalMelding()
        assertThat(totalmelding.total.totalAntall).isEqualTo(3)
        assertThat(totalmelding.total.totalBelop.toInt()).isEqualTo(400)
        assertThat(totalmelding.total.fortegn).isEqualTo(Fortegn.T)

        val avstemtFormatter = DateTimeFormatter.ofPattern("yyyyMMddHH")
        assertThat(totalmelding.periode.datoAvstemtFom).isEqualTo(now.minusDays(1).format(avstemtFormatter))
        assertThat(totalmelding.periode.datoAvstemtTom).isEqualTo(now.plusDays(1).format(avstemtFormatter))

        assertThat(totalmelding.grunnlag.godkjentAntall).isEqualTo(3)
        assertThat(totalmelding.grunnlag.godkjentBelop.toInt()).isEqualTo(400)
        assertThat(totalmelding.grunnlag.varselAntall).isEqualTo(0)
        assertThat(totalmelding.grunnlag.varselBelop.toInt()).isEqualTo(0)
        assertThat(totalmelding.grunnlag.manglerAntall).isEqualTo(0)
        assertThat(totalmelding.grunnlag.manglerBelop.toInt()).isEqualTo(0)
        assertThat(totalmelding.grunnlag.avvistAntall).isEqualTo(0)
        assertThat(totalmelding.grunnlag.avvistBelop.toInt()).isEqualTo(0)
    }

    fun GrensesnittavstemmingMapper.lagAlleMeldinger(oppdragsliste: List<OppdragTilAvstemming>) =
        listOf(lagStartmelding()) + lagAvstemmingsmeldinger(oppdragsliste) + lagTotalMelding() + lagSluttmelding()

    @Test
    fun testerAtFomOgTomBlirSattRiktigVedGrensesnittavstemming() {
        val førsteAvstemmingstidspunkt = LocalDateTime.now().minusDays(1).withHour(13)
        val andreAvstemmingstidspunkt = LocalDateTime.now().minusDays(1).withHour(15)
        val avstemmingFom = førsteAvstemmingstidspunkt.toLocalDate().atStartOfDay()
        val avstemmingTom = andreAvstemmingstidspunkt.toLocalDate().atTime(LocalTime.MAX)
        val baOppdragLager1 =
            lagTestUtbetalingsoppdrag(førsteAvstemmingstidspunkt, fagområde).somAvstemming
        val baOppdragLager2 =
            lagTestUtbetalingsoppdrag(andreAvstemmingstidspunkt, fagområde).somAvstemming
        val mapper =
            GrensesnittavstemmingMapper(fagområde, avstemmingFom, avstemmingTom)
        val meldinger = mapper.lagAlleMeldinger(listOf(baOppdragLager1, baOppdragLager2))
        assertEquals(4, meldinger.size)
        assertEquals(avstemmingFom.format(tidspunktFormatter), meldinger[2].aksjon.nokkelFom)
        assertEquals(avstemmingTom.format(tidspunktFormatter), meldinger[2].aksjon.nokkelTom)
    }

    fun assertAksjon(
        avstemmingFom: LocalDateTime,
        avstemmingTom: LocalDateTime,
        expected: AksjonType,
        actual: Aksjonsdata,
    ) {
        assertEquals(expected, actual.aksjonType)
        assertEquals(KildeType.AVLEV, actual.kildeType)
        assertEquals(AvstemmingType.GRSN, actual.avstemmingType)
        assertEquals(fagområde, actual.avleverendeKomponentKode)
        assertEquals(SystemKode.OPPDRAGSSYSTEMET.kode, actual.mottakendeKomponentKode)
        assertEquals(fagområde, actual.underkomponentKode)
        assertEquals(avstemmingFom.format(tidspunktFormatter), actual.nokkelFom)
        assertEquals(avstemmingTom.format(tidspunktFormatter), actual.nokkelTom)
        assertEquals(fagområde, actual.brukerId)
    }

    fun assertDetaljData(
        utbetalingsoppdrag: Utbetalingsoppdrag,
        actual: Detaljdata,
    ) {
        assertEquals(DetaljType.MANG, actual.detaljType)
        assertEquals(utbetalingsoppdrag.aktoer, actual.offnr)
        assertEquals(fagområde, actual.avleverendeTransaksjonNokkel)
        assertEquals(utbetalingsoppdrag.avstemmingTidspunkt.format(tidspunktFormatter), actual.tidspunkt)
        assertEquals(null, actual.meldingKode)
        assertEquals(null, actual.alvorlighetsgrad)
        assertEquals(null, actual.tekstMelding)
    }

    fun assertTotalData(
        utbetalingsoppdrag: Utbetalingsoppdrag,
        actual: Totaldata,
    ) {
        assertEquals(1, actual.totalAntall)
        assertEquals(utbetalingsoppdrag.utbetalingsperiode.first().sats, actual.totalBelop)
        assertEquals(Fortegn.T, actual.fortegn)
    }

    fun assertPeriodeData(
        utbetalingsoppdrag: Utbetalingsoppdrag,
        actual: Periodedata,
    ) {
        assertEquals(
            utbetalingsoppdrag.avstemmingTidspunkt.format(DateTimeFormatter.ofPattern("yyyyMMddHH")),
            actual.datoAvstemtFom,
        )
        assertEquals(
            utbetalingsoppdrag.avstemmingTidspunkt.format(DateTimeFormatter.ofPattern("yyyyMMddHH")),
            actual.datoAvstemtTom,
        )
    }

    fun assertGrunnlagsdata(
        utbetalingsoppdrag: Utbetalingsoppdrag,
        actual: Grunnlagsdata,
    ) {
        assertEquals(1, actual.manglerAntall)
        assertEquals(utbetalingsoppdrag.utbetalingsperiode.first().sats, actual.manglerBelop)
        assertEquals(Fortegn.T, actual.manglerFortegn)

        assertEquals(0, actual.godkjentAntall)
        assertEquals(BigDecimal.ZERO, actual.godkjentBelop)
        assertEquals(Fortegn.T, actual.godkjentFortegn)

        assertEquals(0, actual.avvistAntall)
        assertEquals(BigDecimal.ZERO, actual.avvistBelop)
        assertEquals(Fortegn.T, actual.avvistFortegn)
    }
}
