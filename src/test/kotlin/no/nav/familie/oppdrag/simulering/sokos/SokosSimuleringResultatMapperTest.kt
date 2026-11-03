package no.nav.familie.oppdrag.simulering.sokos

import no.nav.familie.kontrakter.felles.simulering.BetalingType
import no.nav.familie.kontrakter.felles.simulering.FagOmrådeKode
import no.nav.familie.kontrakter.felles.simulering.MottakerType
import no.nav.familie.kontrakter.felles.simulering.PosteringType
import no.nav.familie.oppdrag.config.Integrasjonssystem
import no.nav.familie.oppdrag.simulering.sokos.SokosSimulerBeregningResponse.Beregning
import no.nav.familie.oppdrag.simulering.sokos.SokosSimulerBeregningResponse.BeregningStoppnivaa
import no.nav.familie.oppdrag.simulering.sokos.SokosSimulerBeregningResponse.BeregningsPeriode
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
    private val mapper = SokosSimuleringResultatMapper()
    private val utbetalingsoppdrag = lagTestUtbetalingsoppdragForFGBMedEttBarn()
    private val bruker = utbetalingsoppdrag.aktoer

    @Test
    fun `mapper postering med alle felt`() {
        // Arrange
        val respons = respons(stoppnivaa(bruker, detaljer(belop = "1054.00")))

        // Act
        val resultat = mapper.tilDetaljertSimuleringResultat(respons, utbetalingsoppdrag)

        // Assert
        val mottaker = resultat.simuleringMottaker.single()
        assertEquals(bruker, mottaker.mottakerNummer)
        assertEquals(MottakerType.BRUKER, mottaker.mottakerType)

        val postering = mottaker.simulertPostering.single()
        assertEquals(FagOmrådeKode.BARNETRYGD, postering.fagOmrådeKode)
        assertEquals(false, postering.erFeilkonto)
        assertEquals(LocalDate.of(2024, 1, 1), postering.fom)
        assertEquals(LocalDate.of(2024, 1, 31), postering.tom)
        assertEquals(LocalDate.of(2024, 1, 20), postering.forfallsdato)
        assertEquals(BetalingType.DEBIT, postering.betalingType)
        assertEquals(BigDecimal("1054.00"), postering.beløp)
        assertEquals(PosteringType.YTELSE, postering.posteringType)
        assertEquals("BATR", postering.klassekode)
        assertEquals("5566", postering.fagsakId)
        assertFalse(postering.utenInntrekk)
    }

    @Test
    fun `trimmer fagområde, klassekode og fagsakId`() {
        // Arrange
        val respons =
            respons(
                stoppnivaa(bruker, detaljer(belop = "1", klassekode = "BATR  "))
                    .copy(kodeFagomraade = "BA      ", fagsystemId = "5566   "),
            )

        // Act
        val postering =
            mapper
                .tilDetaljertSimuleringResultat(
                    respons,
                    utbetalingsoppdrag,
                ).simuleringMottaker
                .single()
                .simulertPostering
                .single()

        // Assert
        assertEquals(FagOmrådeKode.BARNETRYGD, postering.fagOmrådeKode)
        assertEquals("BATR", postering.klassekode)
        assertEquals("5566", postering.fagsakId)
    }

    @Test
    fun `negativt eller null beløp gir kredit`() {
        // Arrange
        val respons = respons(stoppnivaa(bruker, detaljer(belop = "-100"), detaljer(belop = "0")))

        // Act
        val posteringer =
            mapper
                .tilDetaljertSimuleringResultat(respons, utbetalingsoppdrag)
                .simuleringMottaker
                .single()
                .simulertPostering

        // Assert
        assertTrue(posteringer.all { it.betalingType == BetalingType.KREDIT })
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
        val mottakere =
            mapper
                .tilDetaljertSimuleringResultat(
                    respons,
                    utbetalingsoppdrag,
                ).simuleringMottaker
                .associateBy { it.mottakerNummer }

        // Assert
        assertEquals(3, mottakere.size)
        assertEquals(MottakerType.BRUKER, mottakere.getValue(bruker).mottakerType)
        assertEquals(2, mottakere.getValue(bruker).simulertPostering.size)
        assertEquals(MottakerType.ARBG_ORG, mottakere.getValue(orgnr).mottakerType)
        assertEquals(MottakerType.ARBG_PRIV, mottakere.getValue(annenPerson).mottakerType)
    }

    @Test
    fun `feilkonto som mangler tolkes som false`() {
        // Arrange
        val respons = respons(stoppnivaa(bruker, detaljer(belop = "1")).copy(feilkonto = null))

        // Act
        val postering =
            mapper
                .tilDetaljertSimuleringResultat(
                    respons,
                    utbetalingsoppdrag,
                ).simuleringMottaker
                .single()
                .simulertPostering
                .single()

        // Assert
        assertEquals(false, postering.erFeilkonto)
    }

    @Test
    fun `respons uten beregning gir tomt resultat`() {
        // Arrange
        val respons =
            SokosSimulerBeregningResponse(
                OperationResponse(Container(ResponsSimulering(statusKode = 0, beregning = null))),
            )

        // Act
        val resultat = mapper.tilDetaljertSimuleringResultat(respons, utbetalingsoppdrag)

        // Assert
        assertTrue(resultat.simuleringMottaker.isEmpty())
    }

    @Test
    fun `respons uten responsSimulering gir feil i stedet for tomt resultat`() {
        // Arrange
        val respons = SokosSimulerBeregningResponse(OperationResponse(Container(responsSimulering = null)))

        // Act
        val feil = assertThrows<IllegalStateException> { mapper.tilDetaljertSimuleringResultat(respons, utbetalingsoppdrag) }

        // Assert
        assertTrue(feil.message!!.contains("responsSimulering"))
    }

    @Test
    fun `respons uten statusKode gir feil i stedet for tomt resultat`() {
        // Arrange
        val respons =
            SokosSimulerBeregningResponse(
                OperationResponse(Container(ResponsSimulering(statusKode = null, beregning = null))),
            )

        // Act
        val feil = assertThrows<IllegalStateException> { mapper.tilDetaljertSimuleringResultat(respons, utbetalingsoppdrag) }

        // Assert
        assertTrue(feil.message!!.contains("statusKode"))
    }

    @Test
    fun `statuskode 8 eller høyere gir integrasjonsfeil med statuskode og melding`() {
        // Arrange
        val respons =
            SokosSimulerBeregningResponse(
                OperationResponse(Container(ResponsSimulering(statusKode = 8, statusMelding = "Ugyldig oppdrag"))),
            )

        // Act
        val feil = assertThrows<SokosSimuleringStatusFeil> { mapper.tilDetaljertSimuleringResultat(respons, utbetalingsoppdrag) }

        // Assert
        assertTrue(feil.message!!.contains("statusKode=8"))
        assertTrue(feil.message!!.contains("Ugyldig oppdrag"))
        assertEquals(Integrasjonssystem.SIMULERING, feil.system)
    }

    @Test
    fun `statuskode 4 (varsel) mappes som vanlig`() {
        // Arrange
        val respons = respons(stoppnivaa(bruker, detaljer(belop = "1")), statusKode = 4)

        // Act
        val resultat = mapper.tilDetaljertSimuleringResultat(respons, utbetalingsoppdrag)

        // Assert
        assertEquals(
            1,
            resultat.simuleringMottaker
                .single()
                .simulertPostering.size,
        )
    }

    @Test
    fun `påkrevd felt som mangler gir feil med feltnavn og uten verdi`() {
        // Arrange
        val respons = respons(stoppnivaa(bruker, detaljer(belop = "1")).copy(datoForfall = null))

        // Act
        val feil = assertThrows<IllegalStateException> { mapper.tilDetaljertSimuleringResultat(respons, utbetalingsoppdrag) }

        // Assert
        assertTrue(feil.message!!.contains("datoForfall"))
        assertFalse(feil.message!!.contains(bruker))
    }

    private fun respons(
        vararg stoppnivaaer: BeregningStoppnivaa,
        statusKode: Int = 0,
    ) = SokosSimulerBeregningResponse(
        OperationResponse(
            Container(
                ResponsSimulering(
                    statusKode = statusKode,
                    beregning =
                        Beregning(
                            gjelderId = bruker,
                            periodeStoppnivaa =
                                stoppnivaaer.map {
                                    PeriodeStoppnivaa(BeregningsPeriode("2024-01-01", "2024-01-31"), it)
                                },
                        ),
                ),
            ),
        ),
    )

    private fun stoppnivaa(
        utbetalesTilId: String,
        vararg detaljer: StoppnivaaDetaljer,
    ) = BeregningStoppnivaa(
        kodeFagomraade = "BA",
        fagsystemId = "5566",
        utbetalesTilId = utbetalesTilId,
        datoForfall = "2024-01-20",
        feilkonto = false,
        stoppnivaaDetaljer = detaljer.toList(),
    )

    private fun detaljer(
        belop: String,
        klassekode: String = "BATR",
    ) = StoppnivaaDetaljer(
        faktiskFom = "2024-01-01",
        faktiskTom = "2024-01-31",
        belop = BigDecimal(belop),
        klassekode = klassekode,
        typeKlasse = "YTEL",
    )
}
