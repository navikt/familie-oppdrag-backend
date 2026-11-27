package no.nav.familie.oppdrag.iverksetting

import no.nav.familie.oppdrag.kontrakt.Opphør
import no.nav.familie.oppdrag.kontrakt.Utbetalingsoppdrag
import no.nav.familie.oppdrag.kontrakt.Utbetalingsperiode
import no.nav.familie.oppdrag.util.TestUtbetalingsoppdrag.utbetalingsoppdragMedTilfeldigAktoer
import no.trygdeetaten.skjema.oppdrag.Oppdrag110
import no.trygdeetaten.skjema.oppdrag.OppdragsLinje150
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class KontraktTilOppdragTest {
    private val iDag: LocalDate = LocalDate.now()

    @Test
    fun `løpende periode og opphør mappes uten sluttdato`() {
        val mal = utbetalingsoppdragMedTilfeldigAktoer()
        val løpendePeriode = mal.utbetalingsperiode.single().copy(vedtakdatoTom = null, opphør = null)
        val utbetalingsoppdrag =
            mal.copy(
                utbetalingsperiode =
                    listOf(
                        løpendePeriode,
                        løpendePeriode.copy(erEndringPåEksisterendePeriode = true, opphør = Opphør(iDag)),
                    ),
            )

        val oppdrag110 = OppdragMapper().tilOppdrag110(utbetalingsoppdrag)

        Assertions.assertNull(oppdrag110.oppdragsLinje150[0].datoVedtakTom)
        Assertions.assertNull(oppdrag110.oppdragsLinje150[1].datoVedtakTom)
        Assertions.assertEquals(iDag.toXMLDate(), oppdrag110.oppdragsLinje150[1].datoStatusFom)
        val xml = Jaxb.tilXml(OppdragMapper().tilOppdrag(oppdrag110))
        assertThat(xml).doesNotContain("datoVedtakTom")
    }

    @Test
    fun mappeVedtaketTilMariBerg() {
        val utbetalingsperiode1 =
            Utbetalingsperiode(
                erEndringPåEksisterendePeriode = false,
                opphør = null,
                periodeId = 1,
                forrigePeriodeId = null,
                datoForVedtak = iDag,
                klassifisering = "BATR",
                vedtakdatoFom = iDag,
                vedtakdatoTom = iDag.plusYears(6),
                sats = BigDecimal.valueOf(1354L),
                satsType = Utbetalingsperiode.SatsType.MND,
                utbetalesTil = "12345678911",
                behandlingId = 987654321L,
            )

        val utbetalingsperiode2 =
            Utbetalingsperiode(
                erEndringPåEksisterendePeriode = false,
                opphør = null,
                periodeId = 2,
                forrigePeriodeId = 1,
                datoForVedtak = iDag,
                klassifisering = "BATR",
                vedtakdatoFom = iDag.plusYears(6).plusMonths(1),
                vedtakdatoTom = iDag.plusYears(12).plusMonths(1),
                sats = BigDecimal.valueOf(1054L),
                satsType = Utbetalingsperiode.SatsType.MND,
                utbetalesTil = "12345678911",
                behandlingId = 987654321L,
                utbetalingsgrad = 60,
            )

        val utbetalingsoppdrag =
            Utbetalingsoppdrag(
                kodeEndring = Utbetalingsoppdrag.KodeEndring.NY,
                fagSystem = "BA",
                saksnummer = "12345678",
                aktoer = "12345678911",
                saksbehandlerId = "Z992991",
                utbetalingsperiode = listOf(utbetalingsperiode1, utbetalingsperiode2),
            )

        val oppdrag110 = OppdragMapper().tilOppdrag110(utbetalingsoppdrag)

        assertOppdrag110(utbetalingsoppdrag, oppdrag110)
        assertOppdragslinje150(utbetalingsperiode1, utbetalingsoppdrag, oppdrag110.oppdragsLinje150[0])
        assertOppdragslinje150(utbetalingsperiode2, utbetalingsoppdrag, oppdrag110.oppdragsLinje150[1])
    }

    @Test
    fun mappeOpphørPåVedtaketTilMariBerg() {
        val utbetalingsperiode1 =
            Utbetalingsperiode(
                erEndringPåEksisterendePeriode = true,
                opphør = Opphør(iDag.plusMonths(1)),
                periodeId = 3,
                forrigePeriodeId = 2,
                datoForVedtak = iDag,
                klassifisering = "BATR",
                vedtakdatoFom = iDag,
                vedtakdatoTom = iDag.plusYears(2),
                sats = BigDecimal.valueOf(1354L),
                satsType = Utbetalingsperiode.SatsType.MND,
                utbetalesTil = "12345678911",
                behandlingId = 987654321L,
            )
        val utbetalingsoppdrag =
            Utbetalingsoppdrag(
                kodeEndring = Utbetalingsoppdrag.KodeEndring.ENDR,
                fagSystem = "BA",
                saksnummer = "12345678",
                aktoer = "12345678911",
                saksbehandlerId = "Z992991",
                utbetalingsperiode = listOf(utbetalingsperiode1),
            )

        val oppdrag110 = OppdragMapper().tilOppdrag110(utbetalingsoppdrag)

        assertOppdrag110(utbetalingsoppdrag, oppdrag110)
        assertOppdragslinje150(utbetalingsperiode1, utbetalingsoppdrag, oppdrag110.oppdragsLinje150[0])
    }

    private fun assertOppdrag110(
        utbetalingsoppdrag: Utbetalingsoppdrag,
        oppdrag110: Oppdrag110,
    ) {
        Assertions.assertEquals(OppdragSkjemaConstants.KODE_AKSJON, oppdrag110.kodeAksjon)
        Assertions.assertEquals(utbetalingsoppdrag.kodeEndring.name, oppdrag110.kodeEndring.toString())
        Assertions.assertEquals(utbetalingsoppdrag.fagSystem, oppdrag110.kodeFagomraade)
        Assertions.assertEquals(utbetalingsoppdrag.saksnummer, oppdrag110.fagsystemId)
        Assertions.assertEquals(UtbetalingsfrekvensKode.MÅNEDLIG.kode, oppdrag110.utbetFrekvens)
        Assertions.assertEquals(utbetalingsoppdrag.aktoer, oppdrag110.oppdragGjelderId)
        Assertions.assertEquals(OppdragSkjemaConstants.OPPDRAG_GJELDER_DATO_FOM.toXMLDate(), oppdrag110.datoOppdragGjelderFom)
        Assertions.assertEquals(utbetalingsoppdrag.saksbehandlerId, oppdrag110.saksbehId)
        Assertions.assertEquals(utbetalingsoppdrag.fagSystem, oppdrag110.avstemming115.kodeKomponent)
        Assertions.assertEquals(
            utbetalingsoppdrag.avstemmingTidspunkt.format(OppdragMapper().tidspunktFormatter),
            oppdrag110.avstemming115.nokkelAvstemming,
        )
        Assertions.assertEquals(
            utbetalingsoppdrag.avstemmingTidspunkt.format(OppdragMapper().tidspunktFormatter),
            oppdrag110.avstemming115.tidspktMelding,
        )
        Assertions.assertEquals(OppdragSkjemaConstants.ENHET_TYPE, oppdrag110.oppdragsEnhet120[0].typeEnhet)
        Assertions.assertEquals(OppdragSkjemaConstants.ENHET, oppdrag110.oppdragsEnhet120[0].enhet)
        Assertions.assertEquals(OppdragSkjemaConstants.ENHET_DATO_FOM.toXMLDate(), oppdrag110.oppdragsEnhet120[0].datoEnhetFom)
    }

    private fun assertOppdragslinje150(
        utbetalingsperiode: Utbetalingsperiode,
        utbetalingsoppdrag: Utbetalingsoppdrag,
        oppdragsLinje150: OppdragsLinje150,
    ) {
        Assertions.assertEquals(
            if (utbetalingsperiode.erEndringPåEksisterendePeriode) {
                EndringsKode.ENDRING.kode
            } else {
                EndringsKode.NY.kode
            },
            oppdragsLinje150.kodeEndringLinje,
        )
        assertOpphør(utbetalingsperiode, oppdragsLinje150)
        Assertions.assertEquals(utbetalingsperiode.datoForVedtak.toString(), oppdragsLinje150.vedtakId)
        Assertions.assertEquals(
            utbetalingsoppdrag.saksnummer + utbetalingsperiode.periodeId.toString(),
            oppdragsLinje150.delytelseId,
        )
        Assertions.assertEquals(utbetalingsperiode.klassifisering, oppdragsLinje150.kodeKlassifik)
        Assertions.assertEquals(utbetalingsperiode.vedtakdatoFom.toXMLDate(), oppdragsLinje150.datoVedtakFom)
        Assertions.assertEquals(utbetalingsperiode.vedtakdatoTom?.toXMLDate(), oppdragsLinje150.datoVedtakTom)
        Assertions.assertEquals(utbetalingsperiode.sats, oppdragsLinje150.sats)
        Assertions.assertEquals(OppdragSkjemaConstants.FRADRAG_TILLEGG, oppdragsLinje150.fradragTillegg)
        Assertions.assertEquals(utbetalingsperiode.satsType.name, oppdragsLinje150.typeSats)
        Assertions.assertEquals(OppdragSkjemaConstants.BRUK_KJØREPLAN_DEFAULT, oppdragsLinje150.brukKjoreplan)
        Assertions.assertEquals(utbetalingsoppdrag.saksbehandlerId, oppdragsLinje150.saksbehId)
        Assertions.assertEquals(utbetalingsoppdrag.aktoer, oppdragsLinje150.utbetalesTilId)
        Assertions.assertEquals(utbetalingsperiode.behandlingId.toString(), oppdragsLinje150.henvisning)
        Assertions.assertEquals(utbetalingsoppdrag.saksbehandlerId, oppdragsLinje150.attestant180[0].attestantId)
        Assertions.assertEquals(
            utbetalingsperiode.utbetalingsgrad,
            oppdragsLinje150.grad170
                .firstOrNull()
                ?.grad
                ?.toInt(),
        )

        if (utbetalingsperiode.forrigePeriodeId !== null && !utbetalingsperiode.erEndringPåEksisterendePeriode) {
            Assertions.assertEquals(
                utbetalingsoppdrag.saksnummer + utbetalingsperiode.forrigePeriodeId.toString(),
                oppdragsLinje150.refDelytelseId,
            )
        }
    }

    private fun assertOpphør(
        utbetalingsperiode: Utbetalingsperiode,
        oppdragsLinje150: OppdragsLinje150,
    ) {
        if (utbetalingsperiode.opphør == null) {
            Assertions.assertEquals(utbetalingsperiode.opphør, oppdragsLinje150.kodeStatusLinje)
            Assertions.assertEquals(utbetalingsperiode.opphør, oppdragsLinje150.datoStatusFom)
        } else {
            utbetalingsperiode.opphør?.let {
                Assertions.assertEquals("OPPH", oppdragsLinje150.kodeStatusLinje.name)
                Assertions.assertEquals(it.opphørDatoFom.toXMLDate(), oppdragsLinje150.datoStatusFom)
            }
        }
    }
}
