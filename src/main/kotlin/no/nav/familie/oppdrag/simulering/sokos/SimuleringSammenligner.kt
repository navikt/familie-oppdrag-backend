package no.nav.familie.oppdrag.simulering.sokos

import no.nav.familie.kontrakter.felles.simulering.BetalingType
import no.nav.familie.kontrakter.felles.simulering.DetaljertSimuleringResultat
import no.nav.familie.kontrakter.felles.simulering.FagOmrådeKode
import no.nav.familie.kontrakter.felles.simulering.MottakerType
import no.nav.familie.kontrakter.felles.simulering.PosteringType
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Sammenligner simuleringsresultat fra SOAP (fasit) og REST (sokos-oppdrag-proxy).
 *
 * Rekkefølge ignoreres: SOAP grupperer mottakere i et HashMap, så rekkefølgen er ikke stabil.
 * Beløp sammenlignes uten hensyn til skala, slik at 100 og 100.00 regnes som like.
 * Posteringer sammenlignes som multimengder, så like posteringer som forekommer flere ganger telles.
 */
object SimuleringSammenligner {
    fun sammenlign(
        soap: DetaljertSimuleringResultat,
        rest: DetaljertSimuleringResultat,
    ): Sammenligningsresultat {
        val soapPosteringer = tilPosteringsnøkler(soap)
        val restPosteringer = tilPosteringsnøkler(rest)

        return Sammenligningsresultat(
            kunISoap = differanse(soapPosteringer, restPosteringer),
            kunIRest = differanse(restPosteringer, soapPosteringer),
            ulikMottakerType = ulikeMottakertyper(soap, rest),
        )
    }

    private fun tilPosteringsnøkler(resultat: DetaljertSimuleringResultat): List<Posteringsnøkkel> =
        resultat.simuleringMottaker.flatMap { mottaker ->
            mottaker.simulertPostering.map { postering ->
                Posteringsnøkkel(
                    mottakerNummer = mottaker.mottakerNummer,
                    fagOmrådeKode = postering.fagOmrådeKode,
                    erFeilkonto = postering.erFeilkonto,
                    fom = postering.fom,
                    tom = postering.tom,
                    betalingType = postering.betalingType,
                    beløp = postering.beløp.stripTrailingZeros(),
                    posteringType = postering.posteringType,
                    forfallsdato = postering.forfallsdato,
                    utenInntrekk = postering.utenInntrekk,
                    klassekode = postering.klassekode,
                    fagsakId = postering.fagsakId,
                )
            }
        }

    /** Elementer i [a] som ikke finnes i [b], med hensyn til antall forekomster. */
    private fun differanse(
        a: List<Posteringsnøkkel>,
        b: List<Posteringsnøkkel>,
    ): List<Posteringsnøkkel> {
        val gjenstående = b.groupingBy { it }.eachCount().toMutableMap()
        return a.filter { nøkkel ->
            val antall = gjenstående[nøkkel] ?: 0
            if (antall > 0) {
                gjenstående[nøkkel] = antall - 1
                false
            } else {
                true
            }
        }
    }

    private fun ulikeMottakertyper(
        soap: DetaljertSimuleringResultat,
        rest: DetaljertSimuleringResultat,
    ): Map<String?, Pair<MottakerType?, MottakerType?>> {
        val soapTyper = soap.simuleringMottaker.associate { it.mottakerNummer to it.mottakerType }
        val restTyper = rest.simuleringMottaker.associate { it.mottakerNummer to it.mottakerType }
        return (soapTyper.keys + restTyper.keys)
            .filter { soapTyper[it] != restTyper[it] }
            .associateWith { soapTyper[it] to restTyper[it] }
    }
}

data class Sammenligningsresultat(
    val kunISoap: List<Posteringsnøkkel>,
    val kunIRest: List<Posteringsnøkkel>,
    val ulikMottakerType: Map<String?, Pair<MottakerType?, MottakerType?>>,
) {
    val erLik: Boolean
        get() = kunISoap.isEmpty() && kunIRest.isEmpty() && ulikMottakerType.isEmpty()
}

data class Posteringsnøkkel(
    val mottakerNummer: String?,
    val fagOmrådeKode: FagOmrådeKode,
    val erFeilkonto: Boolean?,
    val fom: LocalDate,
    val tom: LocalDate,
    val betalingType: BetalingType,
    val beløp: BigDecimal,
    val posteringType: PosteringType,
    val forfallsdato: LocalDate,
    val utenInntrekk: Boolean,
    val klassekode: String?,
    val fagsakId: String?,
)
