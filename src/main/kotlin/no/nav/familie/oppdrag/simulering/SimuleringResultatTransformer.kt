package no.nav.familie.oppdrag.simulering

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
import no.nav.system.os.entiteter.beregningskjema.Beregning
import no.nav.system.os.entiteter.beregningskjema.BeregningStoppnivaa
import no.nav.system.os.entiteter.beregningskjema.BeregningStoppnivaaDetaljer

class SimuleringResultatTransformer {
    fun mapSimulering(
        beregning: Beregning,
        utbetalingsoppdrag: Utbetalingsoppdrag,
    ): DetaljertSimuleringResultat {
        val mottakerMap = hashMapOf<String, MutableList<SimulertPostering>>()
        for (periode in beregning.beregningsPeriode) {
            for (stoppnivaa in periode.beregningStoppnivaa) {
                val posteringer = arrayListOf<SimulertPostering>()
                for (detaljer in stoppnivaa.beregningStoppnivaaDetaljer) {
                    val postering: SimulertPostering = mapPostering(false, stoppnivaa, detaljer)
                    posteringer.add(postering)
                }
                val utbetalesTilId = stoppnivaa.utbetalesTilId
                val posteringListe = mottakerMap.getOrPut(utbetalesTilId) { mutableListOf() }
                posteringListe.addAll(posteringer)
            }
        }

        val requestMottakerId = hentOrgNrEllerFnr(utbetalingsoppdrag.aktoer)
        val simuleringMottakerListe =
            mottakerMap.map { (utbetalesTilId, simulertPostering) ->
                SimuleringMottaker(
                    mottakerNummer = utbetalesTilId,
                    simulertPostering = simulertPostering,
                    mottakerType = utledMottakerType(utbetalesTilId, hentOrgNrEllerFnr(utbetalesTilId) == requestMottakerId),
                )
            }
        return DetaljertSimuleringResultat(simuleringMottakerListe)
    }

    private fun mapPostering(
        utenInntrekk: Boolean,
        stoppnivaa: BeregningStoppnivaa,
        detaljer: BeregningStoppnivaaDetaljer,
    ): SimulertPostering =
        SimulertPostering(
            betalingType = utledBetalingType(detaljer.belop),
            erFeilkonto = stoppnivaa.isFeilkonto,
            beløp = detaljer.belop,
            fagOmrådeKode =
                FagOmrådeKode.fraKode(
                    stoppnivaa.kodeFagomraade.trim(),
                ),
            // Todo: fjerne.trim() når TØB har rettet trailing spaces-feilen (jira: TOB-1509)
            fom = parseDato(detaljer.faktiskFom),
            tom = parseDato(detaljer.faktiskTom),
            forfallsdato = parseDato(stoppnivaa.forfall),
            posteringType = PosteringType.fraKode(detaljer.typeKlasse),
            utenInntrekk = utenInntrekk,
            klassekode = detaljer.klassekode?.trim(),
            fagsakId = stoppnivaa.fagsystemId.trim(),
        )
}
