package no.nav.familie.oppdrag.simulering

import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertSame

class SimuleringMetrikkerTest {
    private val registry = SimpleMeterRegistry()

    @BeforeEach
    fun leggTilRegistry() {
        Metrics.addRegistry(registry)
    }

    @AfterEach
    fun fjernRegistry() {
        Metrics.removeRegistry(registry)
    }

    @Test
    fun `vellykket kall tidtas med utfall ok og gir svaret videre`() {
        // Act
        val svar = SimuleringMetrikker.tidta("test.tid", "BA") { "svar" }

        // Assert
        assertEquals("svar", svar)
        assertEquals(1, antallTidtakinger(utfall = "ok"))
        assertEquals(0, antallTidtakinger(utfall = "feil"))
    }

    @Test
    fun `feilet kall tidtas med utfall feil og kaster samme exception videre`() {
        // Arrange
        val feil = IllegalStateException("feil fra OS")

        // Act
        val kastet = assertThrows<IllegalStateException> { SimuleringMetrikker.tidta("test.tid", "BA") { throw feil } }

        // Assert
        assertSame(feil, kastet)
        assertEquals(1, antallTidtakinger(utfall = "feil"))
        assertEquals(0, antallTidtakinger(utfall = "ok"))
    }

    private fun antallTidtakinger(utfall: String): Long =
        registry
            .find("test.tid")
            .tags("fagsystem", "BA", "utfall", utfall)
            .timer()
            ?.count() ?: 0
}
