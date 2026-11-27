package no.nav.familie.oppdrag.simulering

import no.nav.familie.kontrakter.felles.simulering.DetaljertSimuleringResultat
import no.nav.familie.kontrakter.felles.simulering.FeilutbetalingerFraSimulering
import no.nav.familie.kontrakter.felles.simulering.HentFeilutbetalingerFraSimuleringRequest
import no.nav.familie.oppdrag.kontrakt.Utbetalingsoppdrag

interface SimuleringTjeneste {
    fun utførSimuleringOghentDetaljertSimuleringResultat(utbetalingsoppdrag: Utbetalingsoppdrag): DetaljertSimuleringResultat

    fun hentFeilutbetalinger(request: HentFeilutbetalingerFraSimuleringRequest): FeilutbetalingerFraSimulering
}
