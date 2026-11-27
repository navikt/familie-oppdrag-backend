package no.nav.familie.oppdrag.rest

import jakarta.validation.Valid
import no.nav.familie.kontrakter.felles.Ressurs
import no.nav.familie.kontrakter.felles.oppdrag.Utbetalingsoppdrag
import no.nav.familie.kontrakter.felles.simulering.DetaljertSimuleringResultat
import no.nav.familie.kontrakter.felles.simulering.FeilutbetalingerFraSimulering
import no.nav.familie.kontrakter.felles.simulering.HentFeilutbetalingerFraSimuleringRequest
import no.nav.familie.oppdrag.common.RessursUtils.ok
import no.nav.familie.oppdrag.simulering.SimuleringTjeneste
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(
    "/api/simulering",
    consumes = [MediaType.APPLICATION_JSON_VALUE],
    produces = [MediaType.APPLICATION_JSON_VALUE],
)
class SimuleringController(
    @Autowired val simuleringTjeneste: SimuleringTjeneste,
) {
    val logger: Logger = LoggerFactory.getLogger(SimuleringController::class.java)

    @PostMapping(path = ["/v1"])
    fun utførSimuleringOgHentResultat(
        @Valid @RequestBody
        utbetalingsoppdrag: Utbetalingsoppdrag,
    ): ResponseEntity<Ressurs<DetaljertSimuleringResultat>> =
        ok(simuleringTjeneste.utførSimuleringOghentDetaljertSimuleringResultat(utbetalingsoppdrag))

    @PostMapping(path = ["/feilutbetalinger"])
    fun hentFeilutbetalinger(
        @Valid @RequestBody
        request: HentFeilutbetalingerFraSimuleringRequest,
    ): ResponseEntity<Ressurs<FeilutbetalingerFraSimulering>> {
        logger.info(
            "Henter feilutbetalinger for ytelsestype=${request.ytelsestype}, " +
                "fagsak=${request.eksternFagsakId}," +
                " behandlingId=${request.eksternFagsakId}",
        )
        return ok(simuleringTjeneste.hentFeilutbetalinger(request))
    }
}
