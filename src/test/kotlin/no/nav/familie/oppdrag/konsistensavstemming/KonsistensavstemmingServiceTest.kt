package no.nav.familie.oppdrag.konsistensavstemming

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import no.nav.familie.kontrakter.felles.oppdrag.KonsistensavstemmingRequestV2
import no.nav.familie.kontrakter.felles.oppdrag.Opphør
import no.nav.familie.kontrakter.felles.oppdrag.PerioderForBehandling
import no.nav.familie.kontrakter.felles.oppdrag.Utbetalingsoppdrag
import no.nav.familie.kontrakter.felles.oppdrag.Utbetalingsperiode
import no.nav.familie.oppdrag.avstemming.AvstemmingSender
import no.nav.familie.oppdrag.repository.MellomlagringKonsistensavstemming
import no.nav.familie.oppdrag.repository.MellomlagringKonsistensavstemmingRepository
import no.nav.familie.oppdrag.repository.OppdragLagerRepository
import no.nav.familie.oppdrag.repository.UtbetalingsoppdragForKonsistensavstemming
import no.nav.familie.oppdrag.service.KonsistensavstemmingService
import no.nav.familie.oppdrag.service.MellomlagringKonsistensavstemmingService
import no.nav.virksomhet.tjenester.avstemming.informasjon.konsistensavstemmingsdata.v1.Konsistensavstemmingsdata
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KonsistensavstemmingServiceTest {
    private lateinit var konsistensavstemmingService: KonsistensavstemmingService
    private lateinit var oppdragLagerRepository: OppdragLagerRepository
    private lateinit var avstemmingSender: AvstemmingSender
    private lateinit var mellomlagringKonsistensavstemmingService: MellomlagringKonsistensavstemmingService
    private lateinit var mellomlagringKonsistensavstemmingRepository: MellomlagringKonsistensavstemmingRepository

    private val saksnummer = "1"
    private val saksnummer2 = "2"

    private val aktiveFødselsnummere = listOf("12345678910", "11111111111")

    private val utbetalingsoppdrag1Init =
        lagUtbetalingsoppdrag(
            saksnummer,
            "1",
            lagUtbetalingsperiode(periodeId = 1, beløp = 111, behandlingsId = 1),
            lagUtbetalingsperiode(periodeId = 2, beløp = 100, behandlingsId = 1),
        )

    // Opphør på periode 2, ny periode med annet beløp
    private val utbetalingsoppdrag1Periode2Opphørt =
        lagUtbetalingsoppdrag(
            saksnummer,
            "2",
            lagUtbetalingsperiode(periodeId = 2, beløp = 100, behandlingsId = 1, opphør = true),
            lagUtbetalingsperiode(periodeId = 3, beløp = 211, behandlingsId = 2),
        )
    private val utbetalingsoppdrag2Init =
        lagUtbetalingsoppdrag(
            saksnummer2,
            "3",
            lagUtbetalingsperiode(periodeId = 1, beløp = 20, behandlingsId = 3),
            lagUtbetalingsperiode(periodeId = 2, beløp = 30, behandlingsId = 3),
        )

    @BeforeEach
    fun setUp() {
        oppdragLagerRepository = mockk()
        avstemmingSender = mockk()
        mellomlagringKonsistensavstemmingRepository = mockk()
        mellomlagringKonsistensavstemmingService =
            MellomlagringKonsistensavstemmingService(mellomlagringKonsistensavstemmingRepository)
        konsistensavstemmingService =
            KonsistensavstemmingService(avstemmingSender, oppdragLagerRepository, mellomlagringKonsistensavstemmingService)
        every { avstemmingSender.sendKonsistensAvstemming(any()) } just Runs

        every { mellomlagringKonsistensavstemmingRepository.hentAggregertAntallOppdrag(any()) } returns 0
        every { mellomlagringKonsistensavstemmingRepository.hentAggregertTotalBeløp(any()) } returns 0L

        val mkSlot: MellomlagringKonsistensavstemming = mockk()
        every { mellomlagringKonsistensavstemmingRepository.insert(any()) } returns mkSlot
    }

    @Test
    internal fun `plukker ut perioder fra 2 utbetalingsoppdrag fra samme fagsak til en melding`() {
        every { oppdragLagerRepository.hentUtbetalingsoppdragForKonsistensavstemming(any(), eq(setOf("1", "2"))) } returns
            listOf(utbetalingsoppdrag1Init, utbetalingsoppdrag1Periode2Opphørt)

        val perioder =
            listOf(
                PerioderForBehandling("1", setOf(1), aktiveFødselsnummere[0]),
                PerioderForBehandling("2", setOf(3), aktiveFødselsnummere[0]),
            )
        val request = KonsistensavstemmingRequestV2("BA", perioder, LocalDateTime.now())

        konsistensavstemmingService.utførKonsistensavstemming(request, true, true, null)

        val oppdrag = slot<Konsistensavstemmingsdata>()
        val totalData = slot<Konsistensavstemmingsdata>()
        verifyOrder {
            avstemmingSender.sendKonsistensAvstemming(any())
            avstemmingSender.sendKonsistensAvstemming(capture(oppdrag))
            avstemmingSender.sendKonsistensAvstemming(capture(totalData))
            avstemmingSender.sendKonsistensAvstemming(any())
        }

        assertThat(oppdrag.captured.oppdragsdataListe).hasSize(1)
        assertThat(oppdrag.captured.oppdragsdataListe[0].oppdragslinjeListe).hasSize(2)
        assertThat(oppdrag.captured.oppdragsdataListe[0].oppdragGjelderId).isEqualTo(aktiveFødselsnummere[0])
        assertTrue {
            oppdrag.captured.oppdragsdataListe[0]
                .oppdragslinjeListe
                .all { it.utbetalesTilId == aktiveFødselsnummere[0] }
        }

        assertThat(
            totalData.captured.totaldata.totalBelop
                .toInt(),
        ).isEqualTo(322)
        assertThat(
            totalData.captured.totaldata.totalAntall
                .toInt(),
        ).isEqualTo(1)
    }

    @Test
    internal fun `Bruk verdi fra input til å sette utbetalTilId`() {
        every { oppdragLagerRepository.hentUtbetalingsoppdragForKonsistensavstemming(any(), eq(setOf("1", "2"))) } returns
            listOf(utbetalingsoppdrag1Init, utbetalingsoppdrag1Periode2Opphørt)

        val perioder =
            listOf(
                PerioderForBehandling("1", setOf(1), aktiveFødselsnummere[0], "tss-id"),
                PerioderForBehandling("2", setOf(2, 3), aktiveFødselsnummere[0]),
            )
        val request = KonsistensavstemmingRequestV2("BA", perioder, LocalDateTime.now())

        konsistensavstemmingService.utførKonsistensavstemming(request, true, true, null)

        val oppdrag = slot<Konsistensavstemmingsdata>()
        val totalData = slot<Konsistensavstemmingsdata>()
        verifyOrder {
            avstemmingSender.sendKonsistensAvstemming(any())
            avstemmingSender.sendKonsistensAvstemming(capture(oppdrag))
            avstemmingSender.sendKonsistensAvstemming(capture(totalData))
            avstemmingSender.sendKonsistensAvstemming(any())
        }

        assertThat(oppdrag.captured.oppdragsdataListe).hasSize(1)
        assertThat(oppdrag.captured.oppdragsdataListe[0].oppdragslinjeListe).hasSize(3)
        assertThat(oppdrag.captured.oppdragsdataListe[0].oppdragGjelderId).isEqualTo(aktiveFødselsnummere[0])
        assertThat(
            oppdrag.captured.oppdragsdataListe[0]
                .oppdragslinjeListe[0]
                .utbetalesTilId == "tss-id",
        )
        assertThat(
            oppdrag.captured.oppdragsdataListe[0]
                .oppdragslinjeListe[1]
                .utbetalesTilId == aktiveFødselsnummere[0],
        )

        assertThat(
            totalData.captured.totaldata.totalBelop
                .toInt(),
        ).isEqualTo(422)
        assertThat(
            totalData.captured.totaldata.totalAntall
                .toInt(),
        ).isEqualTo(1)
    }

    @Test
    internal fun `sender hver fagsak i ulike meldinger`() {
        every { oppdragLagerRepository.hentUtbetalingsoppdragForKonsistensavstemming(any(), eq(setOf("1", "3"))) } returns
            listOf(utbetalingsoppdrag1Init, utbetalingsoppdrag2Init)

        val perioder =
            listOf(
                PerioderForBehandling("1", setOf(1), aktiveFødselsnummere[0]),
                PerioderForBehandling("3", setOf(1, 2), aktiveFødselsnummere[1]),
            )

        val request = KonsistensavstemmingRequestV2("BA", perioder, LocalDateTime.now())

        konsistensavstemmingService.utførKonsistensavstemming(request, true, true, null)

        val oppdrag = slot<Konsistensavstemmingsdata>()
        val oppdrag2 = slot<Konsistensavstemmingsdata>()
        val totalData = slot<Konsistensavstemmingsdata>()
        verifyOrder {
            avstemmingSender.sendKonsistensAvstemming(any())
            avstemmingSender.sendKonsistensAvstemming(capture(oppdrag))
            avstemmingSender.sendKonsistensAvstemming(capture(oppdrag2))
            avstemmingSender.sendKonsistensAvstemming(capture(totalData))
            avstemmingSender.sendKonsistensAvstemming(any())
        }

        assertThat(oppdrag.captured.oppdragsdataListe).hasSize(1)
        assertThat(oppdrag.captured.oppdragsdataListe[0].oppdragslinjeListe).hasSize(1)
        assertThat(oppdrag.captured.oppdragsdataListe[0].oppdragGjelderId).isEqualTo(aktiveFødselsnummere[0])
        assertTrue {
            oppdrag.captured.oppdragsdataListe[0]
                .oppdragslinjeListe
                .any { it.utbetalesTilId == aktiveFødselsnummere[0] }
        }

        assertThat(oppdrag2.captured.oppdragsdataListe).hasSize(1)
        assertThat(oppdrag2.captured.oppdragsdataListe[0].oppdragslinjeListe).hasSize(2)
        assertThat(oppdrag2.captured.oppdragsdataListe[0].oppdragGjelderId).isEqualTo(aktiveFødselsnummere[1])
        assertTrue {
            oppdrag2.captured.oppdragsdataListe[0]
                .oppdragslinjeListe
                .any { it.utbetalesTilId == aktiveFødselsnummere[1] }
        }

        assertThat(
            totalData.captured.totaldata.totalBelop
                .toInt(),
        ).isEqualTo(161)
        assertThat(
            totalData.captured.totaldata.totalAntall
                .toInt(),
        ).isEqualTo(2)
    }

    @Test
    internal fun `Sender startmelding uten oppdrag`() {
        every { oppdragLagerRepository.hentUtbetalingsoppdragForKonsistensavstemming(any(), eq(emptySet())) } returns
            emptyList()
        every { mellomlagringKonsistensavstemmingRepository.insert(any()) } returns mockk()
        every { mellomlagringKonsistensavstemmingRepository.findAllByTransaksjonsId(any()) } returns emptyList()

        val avstemmingstidspunkt = LocalDateTime.now()
        val request = KonsistensavstemmingRequestV2("BA", emptyList(), avstemmingstidspunkt)
        val transaksjonsId = UUID.randomUUID()

        konsistensavstemmingService.utførKonsistensavstemming(request, true, false, transaksjonsId)

        val transaksjonsIdSlot = slot<UUID>()
        val startmeldingSlot = slot<Konsistensavstemmingsdata>()
        val mkSlot = slot<MellomlagringKonsistensavstemming>()

        verify(exactly = 1) { oppdragLagerRepository.hentUtbetalingsoppdragForKonsistensavstemming(any(), eq(emptySet())) }
        verify(exactly = 1) { mellomlagringKonsistensavstemmingRepository.findAllByTransaksjonsId(capture(transaksjonsIdSlot)) }
        verify(exactly = 1) { avstemmingSender.sendKonsistensAvstemming(capture(startmeldingSlot)) }
        verify(exactly = 1) { mellomlagringKonsistensavstemmingRepository.insert(capture(mkSlot)) }

        assertEquals(transaksjonsId, transaksjonsIdSlot.captured)
        assertEquals(0, mkSlot.captured.antallOppdrag)
        assertEquals(0L, mkSlot.captured.totalBeløp)
        assertEquals("START", startmeldingSlot.captured.aksjonsdata.aksjonsType)
    }

    @Test
    internal fun `Sender avsluttmelding uten oppdrag`() {
        val transaksjonsId = UUID.randomUUID()

        every { oppdragLagerRepository.hentUtbetalingsoppdragForKonsistensavstemming(any(), eq(emptySet())) } returns
            emptyList()
        every { mellomlagringKonsistensavstemmingRepository.hentAggregertTotalBeløp(transaksjonsId) } returns 123L
        every { mellomlagringKonsistensavstemmingRepository.hentAggregertAntallOppdrag(transaksjonsId) } returns 33

        val avstemmingstidspunkt = LocalDateTime.now()
        val request = KonsistensavstemmingRequestV2("BA", emptyList(), avstemmingstidspunkt)

        konsistensavstemmingService.utførKonsistensavstemming(request, false, true, transaksjonsId)

        val totalDataMeldingSlot = slot<Konsistensavstemmingsdata>()
        val avsluttmeldingSlot = slot<Konsistensavstemmingsdata>()

        verify(exactly = 1) { oppdragLagerRepository.hentUtbetalingsoppdragForKonsistensavstemming(any(), eq(emptySet())) }

        verifyOrder {
            avstemmingSender.sendKonsistensAvstemming(capture(totalDataMeldingSlot))
            avstemmingSender.sendKonsistensAvstemming(capture(avsluttmeldingSlot))
        }

        verify(exactly = 0) { mellomlagringKonsistensavstemmingRepository.insert(any()) }
        verify(exactly = 0) { mellomlagringKonsistensavstemmingRepository.findAllByTransaksjonsId(any()) }

        assertEquals(BigInteger.valueOf(33), totalDataMeldingSlot.captured.totaldata.totalAntall)
        assertEquals(BigDecimal.valueOf(123), totalDataMeldingSlot.captured.totaldata.totalBelop)
        assertEquals("AVSL", avsluttmeldingSlot.captured.aksjonsdata.aksjonsType)
    }

    @Test
    internal fun `Sender oppdragsmeldinger uten start eller avslutt melding`() {
        every { oppdragLagerRepository.hentUtbetalingsoppdragForKonsistensavstemming(any(), eq(setOf("1", "3"))) } returns
            listOf(utbetalingsoppdrag1Init, utbetalingsoppdrag2Init)
        every { oppdragLagerRepository.hentUtbetalingsoppdragForKonsistensavstemming(any(), eq(emptySet())) } returns
            emptyList()
        every { mellomlagringKonsistensavstemmingRepository.insert(any()) } returns mockk()

        val avstemmingstidspunkt = LocalDateTime.now()
        val perioder =
            listOf(
                PerioderForBehandling("1", setOf(1), aktiveFødselsnummere[0]),
                PerioderForBehandling("3", setOf(1, 2), aktiveFødselsnummere[1]),
            )

        val request = KonsistensavstemmingRequestV2("BA", perioder, avstemmingstidspunkt)
        val transaksjonsId = UUID.randomUUID()

        konsistensavstemmingService.utførKonsistensavstemming(request, false, false, transaksjonsId)

        val oppdrag1Slot = slot<Konsistensavstemmingsdata>()
        val oppdrag3Slot = slot<Konsistensavstemmingsdata>()
        val mkSlot = slot<MellomlagringKonsistensavstemming>()
        val totalBeløpSlot = slot<Long>()

        verify(exactly = 1) { oppdragLagerRepository.hentUtbetalingsoppdragForKonsistensavstemming("BA", eq(setOf("1", "3"))) }
        verify(exactly = 0) { mellomlagringKonsistensavstemmingRepository.findAllByTransaksjonsId(any()) }

        verifyOrder {
            avstemmingSender.sendKonsistensAvstemming(capture(oppdrag1Slot))
            avstemmingSender.sendKonsistensAvstemming(capture(oppdrag3Slot))
        }

        verify(exactly = 1) { mellomlagringKonsistensavstemmingRepository.insert(capture(mkSlot)) }

        assertEquals(1, oppdrag1Slot.captured.oppdragsdataListe.size)
        assertEquals(1, oppdrag3Slot.captured.oppdragsdataListe.size)
        assertEquals(2, mkSlot.captured.antallOppdrag)
        assertEquals(161, mkSlot.captured.totalBeløp)
    }

    private fun lagUtbetalingsperiode(
        periodeId: Long,
        forrigePeriodeId: Long? = null,
        beløp: Int,
        behandlingsId: Long,
        opphør: Boolean = false,
    ) = Utbetalingsperiode(
        erEndringPåEksisterendePeriode = false,
        opphør = if (opphør) Opphør(LocalDate.now()) else null,
        periodeId = periodeId,
        forrigePeriodeId = forrigePeriodeId,
        datoForVedtak = LocalDate.now(),
        klassifisering = "EF",
        vedtakdatoFom = LocalDate.now().minusYears(1),
        vedtakdatoTom = LocalDate.now().plusYears(1),
        sats = BigDecimal(beløp),
        satsType = Utbetalingsperiode.SatsType.MND,
        utbetalesTil = "meg",
        behandlingId = behandlingsId,
    )

    private fun lagUtbetalingsoppdrag(
        saksnummer: String,
        behandlingId: String,
        vararg utbetalingsperiode: Utbetalingsperiode,
    ) = UtbetalingsoppdragForKonsistensavstemming(
        saksnummer,
        behandlingId,
        Utbetalingsoppdrag(
            kodeEndring = Utbetalingsoppdrag.KodeEndring.NY,
            fagSystem = "BA",
            saksnummer = saksnummer,
            aktoer = "aktoer",
            saksbehandlerId = "saksbehandler",
            utbetalingsperiode = utbetalingsperiode.toList(),
        ),
    )
}
