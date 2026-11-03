package no.nav.familie.oppdrag.simulering.sokos

import no.nav.familie.kontrakter.felles.simulering.DetaljertSimuleringResultat
import no.nav.familie.kontrakter.felles.simulering.MottakerType
import no.nav.familie.kontrakter.felles.simulering.SimulertPostering
import java.math.BigDecimal

/**
 * Sammenligner SOAP (fasit) og REST som multimengder av posteringer per mottaker. Rekkefølgen ignoreres, fordi
 * SOAP grupperer mottakere i et HashMap, og beløp sammenlignes uten skala, slik at 100 og 100.00 er like.
 */
object SimuleringSammenligner {
    fun sammenlign(
        soap: DetaljertSimuleringResultat,
        rest: DetaljertSimuleringResultat,
    ): Sammenligningsresultat {
        val soapPosteringer = posteringer(soap)
        val restPosteringer = posteringer(rest)
        return Sammenligningsresultat(
            kunISoap = differanse(soapPosteringer, restPosteringer),
            kunIRest = differanse(restPosteringer, soapPosteringer),
        )
    }

    private fun posteringer(resultat: DetaljertSimuleringResultat): List<MottakersPostering> =
        resultat.simuleringMottaker.flatMap { mottaker ->
            mottaker.simulertPostering.map {
                MottakersPostering(mottaker.mottakerNummer, mottaker.mottakerType, it.copy(beløp = utenSkala(it.beløp)))
            }
        }

    // stripTrailingZeros alene gir 1E+2 for 100, som er vanskelig å lese i securelog.
    private fun utenSkala(beløp: BigDecimal): BigDecimal = beløp.stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }

    /** Elementer i [a] som ikke finnes i [b], med hensyn til antall forekomster. */
    private fun differanse(
        a: List<MottakersPostering>,
        b: List<MottakersPostering>,
    ): List<MottakersPostering> {
        val gjenstående = b.groupingBy { it }.eachCount().toMutableMap()
        return a.filter { postering ->
            val antall = gjenstående[postering] ?: 0
            gjenstående[postering] = antall - 1
            antall <= 0
        }
    }
}

data class Sammenligningsresultat(
    val kunISoap: List<MottakersPostering>,
    val kunIRest: List<MottakersPostering>,
) {
    val erLik: Boolean
        get() = kunISoap.isEmpty() && kunIRest.isEmpty()
}

data class MottakersPostering(
    val mottakerNummer: String?,
    val mottakerType: MottakerType?,
    val postering: SimulertPostering,
)
