package no.nav.familie.oppdrag.simulering

import no.nav.familie.kontrakter.felles.simulering.DetaljertSimuleringResultat
import no.nav.familie.kontrakter.felles.simulering.FeilutbetalingerFraSimulering
import no.nav.familie.kontrakter.felles.simulering.HentFeilutbetalingerFraSimuleringRequest
import no.nav.familie.oppdrag.kontrakt.Utbetalingsoppdrag
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.web.context.annotation.ApplicationScope

@Service
@ApplicationScope
@Profile("e2e")
class SimuleringTjenesteE2E : SimuleringTjeneste {
    override fun utførSimuleringOghentDetaljertSimuleringResultat(utbetalingsoppdrag: Utbetalingsoppdrag): DetaljertSimuleringResultat =
        DetaljertSimuleringResultat(simuleringMottaker = emptyList())

    override fun hentFeilutbetalinger(request: HentFeilutbetalingerFraSimuleringRequest): FeilutbetalingerFraSimulering =
        FeilutbetalingerFraSimulering(feilutbetaltePerioder = emptyList())
}
