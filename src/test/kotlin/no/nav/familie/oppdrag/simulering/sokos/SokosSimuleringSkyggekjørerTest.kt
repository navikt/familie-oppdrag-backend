package no.nav.familie.oppdrag.simulering.sokos

import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import no.nav.familie.kontrakter.felles.simulering.DetaljertSimuleringResultat
import no.nav.familie.oppdrag.featuretoggle.FeatureToggleService
import no.nav.familie.oppdrag.simulering.sokos.SokosSimuleringSkyggekjører.Companion.ANTALL_TRÅDER
import no.nav.familie.oppdrag.simulering.sokos.SokosSimuleringSkyggekjører.Companion.KØSTØRRELSE
import no.nav.familie.oppdrag.simulering.util.lagTestUtbetalingsoppdragForFGBMedEttBarn
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertTimeoutPreemptively
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SokosSimuleringSkyggekjørerTest {
    private val registry = SimpleMeterRegistry()
    private val klient = mockk<SokosOppdragProxyKlient>()
    private val featureToggleService = mockk<FeatureToggleService>()
    private val skyggekjører = SokosSimuleringSkyggekjører(klient, featureToggleService)
    private val utbetalingsoppdrag = lagTestUtbetalingsoppdragForFGBMedEttBarn()
    private val soapResultat = DetaljertSimuleringResultat(emptyList())

    @BeforeEach
    fun leggTilRegistry() = Metrics.addRegistry(registry)

    @AfterEach
    fun fjernRegistry() = Metrics.removeRegistry(registry)

    @Test
    fun `kaller ikke REST når togglen er av`() {
        // Arrange
        every { featureToggleService.isEnabled(any(), any<String>()) } returns false
        val kalt = CountDownLatch(1)
        every { klient.simulerBeregning(any()) } answers {
            kalt.countDown()
            error("Skal ikke kalles")
        }

        // Act
        skyggekjører.skyggekjør(utbetalingsoppdrag, soapResultat)

        // Assert
        assertFalse(kalt.await(500, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `venter ikke på REST-kall som henger, og forkaster når køen er full`() {
        // Arrange
        every { featureToggleService.isEnabled(any(), any<String>()) } returns true
        // Kallene slippes aldri løs, så de ikke teller i metrikkene til andre tester.
        every { klient.simulerBeregning(any()) } answers {
            CountDownLatch(1).await()
            error("Nås aldri")
        }

        // Act: de første kallene opptar trådene, de neste fyller køen og det siste forkastes.
        assertTimeoutPreemptively(Duration.ofSeconds(5)) {
            repeat(ANTALL_TRÅDER + KØSTØRRELSE + 1) { skyggekjører.skyggekjør(utbetalingsoppdrag, soapResultat) }
        }

        // Assert
        assertEquals(1.0, antall("forkastet"))
    }

    @Test
    fun `feil i toggle-sjekken kastes ikke videre til SOAP-flyten`() {
        // Arrange
        every { featureToggleService.isEnabled(any(), any<String>()) } throws IllegalStateException("Unleash er nede")

        // Act
        skyggekjører.skyggekjør(utbetalingsoppdrag, soapResultat)

        // Assert
        assertEquals(1.0, antall("feil"))
    }

    private fun antall(utfall: String) =
        registry
            .find("simulering.skyggekjoring.utfall")
            .tag("utfall", utfall)
            .counter()
            ?.count()
}
