package no.nav.familie.oppdrag.simulering.sokos

import no.nav.familie.kontrakter.felles.simulering.BetalingType
import no.nav.familie.kontrakter.felles.simulering.FagOmrådeKode
import no.nav.familie.kontrakter.felles.simulering.MottakerType
import no.nav.familie.kontrakter.felles.simulering.PosteringType
import no.nav.familie.kontrakter.felles.simulering.SimulertPostering
import no.nav.familie.oppdrag.config.IntegrasjonException
import no.nav.familie.oppdrag.simulering.sokos.SokosSimulerBeregningResponse.Beregning
import no.nav.familie.oppdrag.simulering.sokos.SokosSimulerBeregningResponse.BeregningStoppnivaa
import no.nav.familie.oppdrag.simulering.sokos.SokosSimulerBeregningResponse.Container
import no.nav.familie.oppdrag.simulering.sokos.SokosSimulerBeregningResponse.OperationResponse
import no.nav.familie.oppdrag.simulering.sokos.SokosSimulerBeregningResponse.PeriodeStoppnivaa
import no.nav.familie.oppdrag.simulering.sokos.SokosSimulerBeregningResponse.ResponsSimulering
import no.nav.familie.oppdrag.simulering.sokos.SokosSimulerBeregningResponse.StoppnivaaDetaljer
import no.nav.familie.oppdrag.simulering.util.lagTestUtbetalingsoppdragForFGBMedEttBarn
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SokosSimuleringResultatMapperTest {
    private val utbetalingsoppdrag = lagTestUtbetalingsoppdragForFGBMedEttBarn()
    private val bruker = utbetalingsoppdrag.aktoer

    @Test
    fun `mapper alle felt ved varsel, trimmer etterstilte mellomrom og tolker manglende feilkonto som false`() {
        // Arrange
        val respons = respons(stoppnivaa(bruker, detaljer(belop = "1054.00")), statusKode = 4)

        // Act
        val mottaker = map(respons).simuleringMottaker.single()

        // Assert
        assertEquals(
            SimulertPostering(
                betalingType = BetalingType.DEBIT,
                erFeilkonto = false,
                beløp = BigDecimal("1054.00"),
                fagOmrådeKode = FagOmrådeKode.BARNETRYGD,
                fom = LocalDate.of(2024, 1, 1),
                tom = LocalDate.of(2024, 1, 31),
                forfallsdato = LocalDate.of(2024, 1, 20),
                posteringType = PosteringType.YTELSE,
                utenInntrekk = false,
                klassekode = "BATR",
                fagsakId = "5566",
            ),
            mottaker.simulertPostering.single(),
        )
    }

    @Test
    fun `grupperer posteringer per mottaker og utleder mottakertype`() {
        // Arrange
        val orgnr = "00123456789"
        val annenPerson = "98765432109"
        val respons =
            respons(
                stoppnivaa(bruker, detaljer(belop = "1")),
                stoppnivaa(orgnr, detaljer(belop = "2")),
                stoppnivaa(bruker, detaljer(belop = "3")),
                stoppnivaa(annenPerson, detaljer(belop = "4")),
            )

        // Act
        val mottakere = map(respons).simuleringMottaker

        // Assert
        assertEquals(
            listOf(
                Triple(bruker, MottakerType.BRUKER, 2),
                Triple(orgnr, MottakerType.ARBG_ORG, 1),
                Triple(annenPerson, MottakerType.ARBG_PRIV, 1),
            ),
            mottakere.map { Triple(it.mottakerNummer, it.mottakerType, it.simulertPostering.size) },
        )
    }

    @Test
    fun `respons uten beregning gir tomt resultat`() {
        // Arrange
        val respons = responsSimulering(ResponsSimulering(statusKode = 0, beregning = null))

        // Act
        val resultat = map(respons)

        // Assert
        assertTrue(resultat.simuleringMottaker.isEmpty())
    }

    @Test
    fun `respons uten responsSimulering gir feil i stedet for tomt resultat`() {
        // Arrange
        val respons = responsSimulering(null)

        // Act
        val feil = assertThrows<IllegalStateException> { map(respons) }

        // Assert
        assertTrue(feil.message!!.contains("responsSimulering"))
    }

    @Test
    fun `respons uten statusKode gir feil i stedet for tomt resultat`() {
        // Arrange
        val respons = responsSimulering(ResponsSimulering(statusKode = null))

        // Act
        val feil = assertThrows<IllegalStateException> { map(respons) }

        // Assert
        assertTrue(feil.message!!.contains("statusKode"))
    }

    @Test
    fun `statuskode 8 eller høyere gir integrasjonsfeil med statuskode og melding`() {
        // Arrange
        val respons = responsSimulering(ResponsSimulering(statusKode = 8, statusMelding = "Ugyldig oppdrag"))

        // Act
        val feil = assertThrows<IntegrasjonException> { map(respons) }

        // Assert
        assertTrue(feil.message!!.contains("statusKode=8"))
        assertTrue(feil.message!!.contains("Ugyldig oppdrag"))
    }

    @Test
    fun `påkrevd felt som mangler gir feil med feltnavn og uten verdi`() {
        // Arrange
        val respons = respons(stoppnivaa(bruker, detaljer(belop = "1")).copy(datoForfall = null))

        // Act
        val feil = assertThrows<IllegalStateException> { map(respons) }

        // Assert
        assertTrue(feil.message!!.contains("datoForfall"))
        assertFalse(feil.message!!.contains(bruker))
    }

    private fun map(respons: SokosSimulerBeregningResponse) =
        SokosSimuleringResultatMapper.tilDetaljertSimuleringResultat(respons, utbetalingsoppdrag)

    private fun responsSimulering(responsSimulering: ResponsSimulering?) =
        SokosSimulerBeregningResponse(OperationResponse(Container(responsSimulering)))

    private fun respons(
        vararg stoppnivaaer: BeregningStoppnivaa,
        statusKode: Int = 0,
    ) = responsSimulering(ResponsSimulering(statusKode, beregning = Beregning(stoppnivaaer.map { PeriodeStoppnivaa(it) })))

    // Etterstilte mellomrom og manglende feilkonto, slik Oppdrag kan svare.
    private fun stoppnivaa(
        utbetalesTilId: String,
        vararg detaljer: StoppnivaaDetaljer,
    ) = BeregningStoppnivaa(
        kodeFagomraade = "BA      ",
        fagsystemId = "5566   ",
        utbetalesTilId = utbetalesTilId,
        datoForfall = "2024-01-20",
        feilkonto = null,
        stoppnivaaDetaljer = detaljer.toList(),
    )

    private fun detaljer(belop: String) =
        StoppnivaaDetaljer(
            faktiskFom = "2024-01-01",
            faktiskTom = "2024-01-31",
            belop = BigDecimal(belop),
            klassekode = "BATR  ",
            typeKlasse = "YTEL",
        )
}
