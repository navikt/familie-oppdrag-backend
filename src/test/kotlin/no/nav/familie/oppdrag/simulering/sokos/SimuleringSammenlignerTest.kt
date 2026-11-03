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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SimuleringSammenlignerTest {
    @Test
    fun `like resultater er like`() {
        // Arrange
        val resultat = resultat(mottaker("12345678901", postering(beløp = "1054")))

        // Act
        val sammenligning = SimuleringSammenligner.sammenlign(resultat, resultat)

        // Assert
        assertTrue(sammenligning.erLik)
    }

    @Test
    fun `rekkefølge på mottakere og posteringer ignoreres`() {
        // Arrange
        val a = postering(fom = LocalDate.of(2024, 1, 1), beløp = "100")
        val b = postering(fom = LocalDate.of(2024, 2, 1), beløp = "200")
        val soap = resultat(mottaker("111", a, b), mottaker("222", a))
        val rest = resultat(mottaker("222", a), mottaker("111", b, a))

        // Act
        val sammenligning = SimuleringSammenligner.sammenlign(soap, rest)

        // Assert
        assertTrue(sammenligning.erLik)
    }

    @Test
    fun `beløp med ulik skala regnes som like`() {
        // Arrange
        val soap = resultat(mottaker("111", postering(beløp = "1054.00")))
        val rest = resultat(mottaker("111", postering(beløp = "1054")))

        // Act
        val sammenligning = SimuleringSammenligner.sammenlign(soap, rest)

        // Assert
        assertTrue(sammenligning.erLik)
    }

    @Test
    fun `ulikt beløp gir avvik i begge retninger`() {
        // Arrange
        val soap = resultat(mottaker("111", postering(beløp = "100")))
        val rest = resultat(mottaker("111", postering(beløp = "101")))

        // Act
        val sammenligning = SimuleringSammenligner.sammenlign(soap, rest)

        // Assert
        assertFalse(sammenligning.erLik)
        assertEquals(BigDecimal("100"), sammenligning.kunISoap.single().beløp)
        assertEquals(BigDecimal("101"), sammenligning.kunIRest.single().beløp)
    }

    @Test
    fun `duplikate posteringer telles`() {
        // Arrange
        val p = postering(beløp = "100")
        val soap = resultat(mottaker("111", p, p))
        val rest = resultat(mottaker("111", p))

        // Act
        val sammenligning = SimuleringSammenligner.sammenlign(soap, rest)

        // Assert
        assertEquals(1, sammenligning.kunISoap.size)
        assertTrue(sammenligning.kunIRest.isEmpty())
    }

    @Test
    fun `postering på annen mottaker er avvik`() {
        // Arrange
        val p = postering(beløp = "100")
        val soap = resultat(mottaker("111", p))
        val rest = resultat(mottaker("222", p))

        // Act
        val sammenligning = SimuleringSammenligner.sammenlign(soap, rest)

        // Assert
        assertEquals("111", sammenligning.kunISoap.single().mottakerNummer)
        assertEquals("222", sammenligning.kunIRest.single().mottakerNummer)
    }

    @Test
    fun `ulik mottakertype er avvik`() {
        // Arrange
        val p = postering(beløp = "100")
        val soap = resultat(mottaker("111", p, type = MottakerType.BRUKER))
        val rest = resultat(mottaker("111", p, type = MottakerType.ARBG_PRIV))

        // Act
        val sammenligning = SimuleringSammenligner.sammenlign(soap, rest)

        // Assert
        assertFalse(sammenligning.erLik)
        assertEquals(MottakerType.BRUKER to MottakerType.ARBG_PRIV, sammenligning.ulikMottakerType["111"])
    }

    @Test
    fun `tomt mot ikke-tomt resultat er avvik`() {
        // Arrange
        val soap = resultat()
        val rest = resultat(mottaker("111", postering(beløp = "100")))

        // Act
        val sammenligning = SimuleringSammenligner.sammenlign(soap, rest)

        // Assert
        assertFalse(sammenligning.erLik)
        assertEquals(1, sammenligning.kunIRest.size)
    }

    @Test
    fun `to tomme resultater er like`() {
        // Arrange
        val soap = resultat()
        val rest = resultat()

        // Act
        val sammenligning = SimuleringSammenligner.sammenlign(soap, rest)

        // Assert
        assertTrue(sammenligning.erLik)
    }

    private fun resultat(vararg mottakere: SimuleringMottaker) = DetaljertSimuleringResultat(mottakere.toList())

    private fun mottaker(
        nummer: String,
        vararg posteringer: SimulertPostering,
        type: MottakerType = MottakerType.BRUKER,
    ) = SimuleringMottaker(simulertPostering = posteringer.toList(), mottakerNummer = nummer, mottakerType = type)

    private fun postering(
        fom: LocalDate = LocalDate.of(2024, 1, 1),
        beløp: String,
    ) = SimulertPostering(
        fagOmrådeKode = FagOmrådeKode.BARNETRYGD,
        erFeilkonto = false,
        fom = fom,
        tom = fom.plusMonths(1).minusDays(1),
        betalingType = BetalingType.DEBIT,
        beløp = BigDecimal(beløp),
        posteringType = PosteringType.YTELSE,
        forfallsdato = fom.plusDays(19),
        utenInntrekk = false,
        klassekode = "BATR",
        fagsakId = "5566",
    )
}
