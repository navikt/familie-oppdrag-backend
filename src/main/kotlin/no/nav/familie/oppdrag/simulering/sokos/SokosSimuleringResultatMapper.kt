package no.nav.familie.oppdrag.simulering.sokos

import no.nav.familie.kontrakter.felles.oppdrag.Utbetalingsoppdrag
import no.nav.familie.kontrakter.felles.simulering.DetaljertSimuleringResultat
import no.nav.familie.kontrakter.felles.simulering.FagOmrådeKode
import no.nav.familie.kontrakter.felles.simulering.PosteringType
import no.nav.familie.kontrakter.felles.simulering.SimuleringMottaker
import no.nav.familie.kontrakter.felles.simulering.SimulertPostering
import no.nav.familie.oppdrag.simulering.SimuleringUtil.hentOrgNrEllerFnr
import no.nav.familie.oppdrag.simulering.SimuleringUtil.parseDato
import no.nav.familie.oppdrag.simulering.SimuleringUtil.utledBetalingType
import no.nav.familie.oppdrag.simulering.SimuleringUtil.utledMottakerType
import no.nav.familie.oppdrag.simulering.sokos.SokosSimulerBeregningResponse.BeregningStoppnivaa
import no.nav.familie.oppdrag.simulering.sokos.SokosSimulerBeregningResponse.StoppnivaaDetaljer
import org.springframework.stereotype.Component

@Component
class SokosSimuleringResultatMapper {
    fun tilDetaljertSimuleringResultat(
        respons: SokosSimulerBeregningResponse,
        utbetalingsoppdrag: Utbetalingsoppdrag,
    ): DetaljertSimuleringResultat {
        val beregning =
            respons.operationResponse
                ?.container
                ?.responsSimulering
                ?.beregning
                ?: return DetaljertSimuleringResultat(emptyList())

        val posteringerPerMottaker = linkedMapOf<String, MutableList<SimulertPostering>>()
        beregning.periodeStoppnivaa
            .mapNotNull { it.beregningStoppnivaa }
            .forEach { stoppnivaa ->
                val utbetalesTilId = påkrevd(stoppnivaa.utbetalesTilId, "utbetalesTilId")
                posteringerPerMottaker
                    .getOrPut(utbetalesTilId) { mutableListOf() }
                    .addAll(stoppnivaa.stoppnivaaDetaljer.map { tilPostering(stoppnivaa, it) })
            }

        val brukerId = hentOrgNrEllerFnr(utbetalingsoppdrag.aktoer)
        return DetaljertSimuleringResultat(
            posteringerPerMottaker.map { (utbetalesTilId, posteringer) ->
                SimuleringMottaker(
                    mottakerNummer = utbetalesTilId,
                    simulertPostering = posteringer,
                    mottakerType =
                        utledMottakerType(
                            utbetalesTilId,
                            hentOrgNrEllerFnr(utbetalesTilId) == brukerId,
                        ),
                )
            },
        )
    }

    private fun tilPostering(
        stoppnivaa: BeregningStoppnivaa,
        detaljer: StoppnivaaDetaljer,
    ): SimulertPostering {
        val beløp = påkrevd(detaljer.belop, "belop")
        return SimulertPostering(
            betalingType = utledBetalingType(beløp),
            erFeilkonto = stoppnivaa.feilkonto ?: false,
            beløp = beløp,
            fagOmrådeKode = FagOmrådeKode.fraKode(påkrevd(stoppnivaa.kodeFagomraade, "kodeFagomraade").trim()),
            fom = parseDato(påkrevd(detaljer.faktiskFom, "faktiskFom")),
            tom = parseDato(påkrevd(detaljer.faktiskTom, "faktiskTom")),
            forfallsdato = parseDato(påkrevd(stoppnivaa.datoForfall, "datoForfall")),
            posteringType = PosteringType.fraKode(påkrevd(detaljer.typeKlasse, "typeKlasse")),
            utenInntrekk = false,
            klassekode = detaljer.klassekode?.trim(),
            fagsakId = påkrevd(stoppnivaa.fagsystemId, "fagsystemId").trim(),
        )
    }

    private fun <T> påkrevd(
        verdi: T?,
        felt: String,
    ): T = verdi ?: throw IllegalStateException("Mangler $felt i simuleringsrespons fra sokos-oppdrag-proxy")
}
