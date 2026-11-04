package no.nav.familie.oppdrag.simulering.sokos

import no.nav.familie.kontrakter.felles.simulering.DetaljertSimuleringResultat
import no.nav.familie.kontrakter.felles.simulering.MottakerType
import no.nav.familie.kontrakter.felles.simulering.SimulertPostering
import java.math.BigDecimal

/**
 * Sammenligner SOAP (fasit) og REST som multimengder av posteringer per mottaker. Rekkefølgen ignoreres, fordi
 * SOAP grupperer mottakere i et HashMap. Beløp sammenlignes med minst to desimaler, slik at 100 og 100.00 er like.
 */
object SimuleringSammenligner {
    fun sammenlign(
        soap: DetaljertSimuleringResultat,
        rest: DetaljertSimuleringResultat,
    ): Sammenligningsresultat {
        val soapPosteringer = posteringer(soap)
        val restPosteringer = posteringer(rest)
        return Sammenligningsresultat(
            kunISoap = posteringerUtenMatch(soapPosteringer, restPosteringer),
            kunIRest = posteringerUtenMatch(restPosteringer, soapPosteringer),
        )
    }

    private fun posteringer(resultat: DetaljertSimuleringResultat): List<MottakersPostering> =
        resultat.simuleringMottaker.flatMap { mottaker ->
            mottaker.simulertPostering.map {
                MottakersPostering(mottaker.mottakerNummer, mottaker.mottakerType, it.copy(beløp = medMinstToDesimaler(it.beløp)))
            }
        }

    /** 100, 100.0 og 100.000 blir 100.00. Beløp med flere desimaler beholder dem, så de gir avvik i stedet for feil. */
    private fun medMinstToDesimaler(beløp: BigDecimal): BigDecimal = beløp.setScale(maxOf(2, beløp.stripTrailingZeros().scale()))

    /** Posteringene som ikke har en lik postering i [andre]. Hver postering i [andre] matcher bare én gang. */
    private fun posteringerUtenMatch(
        posteringer: List<MottakersPostering>,
        andre: List<MottakersPostering>,
    ): List<MottakersPostering> {
        val antallIAndre = andre.groupingBy { it }.eachCount()
        return posteringer
            .groupBy { it }
            .flatMap { (postering, likePosteringer) -> likePosteringer.drop(antallIAndre[postering] ?: 0) }
    }
}

data class Sammenligningsresultat(
    val kunISoap: List<MottakersPostering>,
    val kunIRest: List<MottakersPostering>,
) {
    val erLik = kunISoap.isEmpty() && kunIRest.isEmpty()
}

data class MottakersPostering(
    val mottakerNummer: String?,
    val mottakerType: MottakerType?,
    val postering: SimulertPostering,
)
