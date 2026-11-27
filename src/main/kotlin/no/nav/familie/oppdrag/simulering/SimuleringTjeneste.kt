package no.nav.familie.oppdrag.simulering

import no.nav.familie.kontrakter.felles.oppdrag.Utbetalingsoppdrag
import no.nav.familie.kontrakter.felles.simulering.DetaljertSimuleringResultat
import no.nav.familie.kontrakter.felles.simulering.FeilutbetalingerFraSimulering
import no.nav.familie.kontrakter.felles.simulering.HentFeilutbetalingerFraSimuleringRequest

interface SimuleringTjeneste {
    fun utførSimuleringOghentDetaljertSimuleringResultat(utbetalingsoppdrag: Utbetalingsoppdrag): DetaljertSimuleringResultat

    fun hentFeilutbetalinger(request: HentFeilutbetalingerFraSimuleringRequest): FeilutbetalingerFraSimulering
}
