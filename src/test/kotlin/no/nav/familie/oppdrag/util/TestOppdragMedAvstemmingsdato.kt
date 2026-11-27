package no.nav.familie.oppdrag.util

import no.nav.familie.kontrakter.felles.oppdrag.Utbetalingsoppdrag
import no.nav.familie.kontrakter.felles.oppdrag.Utbetalingsperiode
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.random.Random

object TestOppdragMedAvstemmingsdato {
    private val FAGSAKID = "123456789"
    private val AKTOER = "12345678911"

    fun lagTestUtbetalingsoppdrag(
        avstemmingstidspunkt: LocalDateTime,
        fagområde: String,
        fagsak: String = FAGSAKID,
        vararg utbetalingsperiode: Utbetalingsperiode = arrayOf(lagUtbetalingsperiode(fagområde)),
    ) = Utbetalingsoppdrag(
        kodeEndring = Utbetalingsoppdrag.KodeEndring.NY,
        fagSystem = fagområde,
        saksnummer = fagsak,
        aktoer = AKTOER,
        saksbehandlerId = "Z999999",
        avstemmingTidspunkt = avstemmingstidspunkt,
        utbetalingsperiode = utbetalingsperiode.toList(),
    )

    fun lagUtbetalingsperiode(
        fagområde: String = "BA",
        periodeId: Long = 1,
        beløp: Int = 100,
        fom: LocalDate = LocalDate.now().withDayOfMonth(1),
        tom: LocalDate = LocalDate.now().plusYears(6),
        behandlingId: Long? = null,
    ) = Utbetalingsperiode(
        erEndringPåEksisterendePeriode = false,
        opphør = null,
        periodeId = periodeId,
        forrigePeriodeId = null,
        datoForVedtak = LocalDate.now(),
        klassifisering = if (fagområde.equals("BA")) "BATR" else "EF",
        vedtakdatoFom = fom,
        vedtakdatoTom = tom,
        sats = beløp.toBigDecimal(),
        satsType = Utbetalingsperiode.SatsType.MND,
        utbetalesTil = AKTOER,
        behandlingId = behandlingId ?: Random.nextLong(),
        utbetalingsgrad = 50,
    )
}
