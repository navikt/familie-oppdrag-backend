package no.nav.familie.oppdrag.kontrakt

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import no.nav.familie.kontrakter.felles.oppdrag.OppdragId
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Midlertidig lokal kopi av oppdragsmodellene fra familie-kontrakter, commit 93b44e2f (PR #1188).
 * Brukes for å teste åpne perioder i preprod uten å publisere kontrakter.
 * Fjern pakken og bytt tilbake til bibliotekets modeller når null-støtten er publisert.
 */
data class Utbetalingsoppdrag(
    val kodeEndring: KodeEndring,
    val fagSystem: String,
    val saksnummer: String,
    val aktoer: String,
    val saksbehandlerId: String,
    val avstemmingTidspunkt: LocalDateTime = LocalDateTime.now(),
    val utbetalingsperiode: List<Utbetalingsperiode>,
    val gOmregning: Boolean = false,
) {
    enum class KodeEndring {
        NY,
        ENDR,
        UEND,
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class Utbetalingsperiode(
    val erEndringPåEksisterendePeriode: Boolean,
    val opphør: Opphør? = null,
    val periodeId: Long,
    val forrigePeriodeId: Long? = null,
    val datoForVedtak: LocalDate,
    val klassifisering: String,
    val vedtakdatoFom: LocalDate,
    val vedtakdatoTom: LocalDate?,
    val sats: BigDecimal,
    val satsType: SatsType,
    val utbetalesTil: String,
    val behandlingId: Long,
    val utbetalingsgrad: Int? = null,
) {
    enum class SatsType {
        DAG,
        MND,
        ENG,
    }
}

data class Opphør(
    val opphørDatoFom: LocalDate,
)

fun Utbetalingsoppdrag.behandlingsIdForFørsteUtbetalingsperiode(): String = utbetalingsperiode[0].behandlingId.toString()

val Utbetalingsoppdrag.oppdragId
    get() = OppdragId(fagSystem, aktoer, behandlingsIdForFørsteUtbetalingsperiode())

data class KonsistensavstemmingUtbetalingsoppdrag(
    val fagsystem: String,
    val utbetalingsoppdrag: List<Utbetalingsoppdrag>,
    val avstemmingstidspunkt: LocalDateTime,
)
