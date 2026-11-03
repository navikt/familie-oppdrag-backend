package no.nav.familie.oppdrag.simulering.sokos

import no.nav.familie.kontrakter.felles.simulering.BetalingType
import no.nav.familie.kontrakter.felles.simulering.DetaljertSimuleringResultat
import no.nav.familie.kontrakter.felles.simulering.FagOmrådeKode
import no.nav.familie.kontrakter.felles.simulering.MottakerType
import no.nav.familie.kontrakter.felles.simulering.PosteringType
import no.nav.familie.kontrakter.felles.simulering.SimuleringMottaker
import no.nav.familie.kontrakter.felles.simulering.SimulertPostering
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimuleringSammenlignerTest {
    @Test
    fun `rekkefølge på mottakere og posteringer og skala på beløp ignoreres`() {
        // Arrange
        val soap = resultat(mottaker("111", postering("100"), postering("200")), mottaker("222", postering("100")))
        val rest = resultat(mottaker("222", postering("100.00")), mottaker("111", postering("200"), postering("100.00")))

        // Act
        val sammenligning = SimuleringSammenligner.sammenlign(soap, rest)

        // Assert
        assertTrue(sammenligning.erLik)
    }

    @Test
    fun `ulikt beløp gir avvik i begge retninger`() {
        // Arrange
        val soap = resultat(mottaker("111", postering("100")))
        val rest = resultat(mottaker("111", postering("101")))

        // Act
        val sammenligning = SimuleringSammenligner.sammenlign(soap, rest)

        // Assert
        assertEquals(listOf(BigDecimal("100")), sammenligning.kunISoap.map { it.postering.beløp })
        assertEquals(listOf(BigDecimal("101")), sammenligning.kunIRest.map { it.postering.beløp })
    }

    @Test
    fun `duplikate posteringer telles`() {
        // Arrange
        val soap = resultat(mottaker("111", postering("100"), postering("100")))
        val rest = resultat(mottaker("111", postering("100")))

        // Act
        val sammenligning = SimuleringSammenligner.sammenlign(soap, rest)

        // Assert
        assertEquals(1, sammenligning.kunISoap.size)
        assertTrue(sammenligning.kunIRest.isEmpty())
    }

    private fun resultat(vararg mottakere: SimuleringMottaker) = DetaljertSimuleringResultat(mottakere.toList())

    private fun mottaker(
        nummer: String,
        vararg posteringer: SimulertPostering,
    ) = SimuleringMottaker(simulertPostering = posteringer.toList(), mottakerNummer = nummer, mottakerType = MottakerType.BRUKER)

    private fun postering(beløp: String) =
        SimulertPostering(
            fagOmrådeKode = FagOmrådeKode.BARNETRYGD,
            fom = LocalDate.of(2024, 1, 1),
            tom = LocalDate.of(2024, 1, 31),
            betalingType = BetalingType.DEBIT,
            beløp = BigDecimal(beløp),
            posteringType = PosteringType.YTELSE,
            forfallsdato = LocalDate.of(2024, 1, 20),
        )
}
