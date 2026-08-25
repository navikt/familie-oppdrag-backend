package no.nav.familie.oppdrag.rest

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import no.nav.familie.kontrakter.felles.Ressurs
import no.nav.familie.kontrakter.felles.oppdrag.OppdragId
import no.nav.familie.kontrakter.felles.oppdrag.OppdragStatus
import no.nav.familie.kontrakter.felles.oppdrag.Opphør
import no.nav.familie.kontrakter.felles.oppdrag.Utbetalingsoppdrag
import no.nav.familie.kontrakter.felles.oppdrag.Utbetalingsperiode
import no.nav.familie.oppdrag.featuretoggle.FeatureToggle
import no.nav.familie.oppdrag.featuretoggle.FeatureToggleService
import no.nav.familie.oppdrag.iverksetting.OppdragMapper
import no.nav.familie.oppdrag.iverksetting.OppdragSender
import no.nav.familie.oppdrag.repository.OppdragLager
import no.nav.familie.oppdrag.repository.OppdragLagerRepository
import no.nav.familie.oppdrag.service.OppdragServiceImpl
import no.trygdeetaten.skjema.oppdrag.Mmel
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals

internal class OppdragControllerTest {
    val localDateTimeNow = LocalDateTime.now()
    val localDateNow = LocalDate.now()

    private val featureToggleService = mockk<FeatureToggleService>()

    val utbetalingsoppdrag =
        Utbetalingsoppdrag(
            Utbetalingsoppdrag.KodeEndring.NY,
            "BA",
            "SAKSNR",
            "PERSONID",
            "SAKSBEHANDLERID",
            localDateTimeNow,
            listOf(
                Utbetalingsperiode(
                    true,
                    Opphør(localDateNow),
                    2,
                    1,
                    localDateNow,
                    "BATR",
                    localDateNow,
                    localDateNow,
                    BigDecimal.ONE,
                    Utbetalingsperiode.SatsType.MND,
                    "UTEBETALES_TIL",
                    1,
                ),
            ),
        )

    @BeforeEach
    fun setUp() {
        every { featureToggleService.isEnabled(FeatureToggle.SKRU_PÅ_IVERKSETTELSE, utbetalingsoppdrag.saksnummer) } returns true
    }

    @Test
    fun `Skal lagre oppdrag for utbetalingoppdrag`() {
        val (oppdragLagerRepository, oppdragController) = mockkOppdragController(false)

        oppdragController.sendOppdrag(utbetalingsoppdrag)

        verify {
            oppdragLagerRepository.opprettOppdrag(
                match<OppdragLager> {
                    it.utgåendeOppdrag.contains("BA") &&
                        it.status == OppdragStatus.LAGT_PÅ_KØ &&
                        it.opprettetTidspunkt > localDateTimeNow
                },
            )
        }
    }

    @Test
    fun `Skal kaste feil om oppdrag er lagret fra før`() {
        val (oppdragLagerRepository, oppdragController) = mockkOppdragController(true)

        val response = oppdragController.sendOppdrag(utbetalingsoppdrag)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(Ressurs.Status.FEILET, response.body?.status)

        verify(exactly = 1) { oppdragLagerRepository.opprettOppdrag(any()) }
    }

    @Test
    fun `Skal kaste 409 feil om oppdrag allerede er kvittert ut`() {
        val (oppdragLagerRepository, oppdragController) = mockkOppdragController(false)
        val oppdragId = OppdragId(fagsystem = "BA", personIdent = "test", behandlingsId = "0")
        val mocketOppdragLager = mockk<OppdragLager>()

        every { mocketOppdragLager.status } returns OppdragStatus.KVITTERT_OK
        every { oppdragLagerRepository.hentOppdrag(oppdragId) } returns mocketOppdragLager

        val response = oppdragController.opprettManuellKvitteringPåOppdrag(oppdragId)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(Ressurs.Status.FEILET, response.body?.status)

        verify(exactly = 1) { oppdragLagerRepository.hentOppdrag(any()) }
    }

    @Test
    fun `Skal returnere 200 OK om oppdrag ble manuelt kvittert ut`() {
        val (oppdragLagerRepository, oppdragController) = mockkOppdragController(false)
        val oppdragId = OppdragId(fagsystem = "BA", personIdent = "test", behandlingsId = "0")
        val mocketOppdragLager = mockk<OppdragLager>()

        every { mocketOppdragLager.status } returns OppdragStatus.LAGT_PÅ_KØ
        every { mocketOppdragLager.versjon } returns 0
        every { mocketOppdragLager.kvitteringsmelding } returns Mmel().apply { beskrMelding = "Manuelt kvittert ut" }

        every { oppdragLagerRepository.hentOppdrag(oppdragId) } returns mocketOppdragLager
        every { oppdragLagerRepository.oppdaterKvitteringsmelding(oppdragId, OppdragStatus.KVITTERT_OK, any(), 1) } just runs

        val response = oppdragController.opprettManuellKvitteringPåOppdrag(oppdragId)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("Manuelt kvittert ut", response.body?.melding)

        verify(exactly = 1) { oppdragLagerRepository.hentOppdrag(any()) }
        verify(exactly = 1) { oppdragLagerRepository.oppdaterKvitteringsmelding(oppdragId, OppdragStatus.KVITTERT_OK, any(), 1) }
    }

    private fun mockkOppdragController(alleredeOpprettet: Boolean = false): Pair<OppdragLagerRepository, OppdragController> {
        val mapper = OppdragMapper()
        val oppdragSender = mockk<OppdragSender>(relaxed = true)

        val oppdragLagerRepository = mockk<OppdragLagerRepository>()
        if (alleredeOpprettet) {
            every {
                oppdragLagerRepository.opprettOppdrag(
                    any(),
                )
            } throws org.springframework.dao.DuplicateKeyException("Duplicate key exception")
        } else {
            every { oppdragLagerRepository.opprettOppdrag(any()) } just Runs
        }

        val oppdragService = OppdragServiceImpl(oppdragSender, oppdragLagerRepository)

        val oppdragController = OppdragController(oppdragService, mapper, featureToggleService)
        return Pair(oppdragLagerRepository, oppdragController)
    }
}
