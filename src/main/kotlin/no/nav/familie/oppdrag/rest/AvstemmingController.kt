package no.nav.familie.oppdrag.rest

import org.springframework.context.annotation.Profile
import no.nav.familie.kontrakter.felles.Ressurs
import no.nav.familie.kontrakter.felles.oppdrag.GrensesnittavstemmingRequest
import no.nav.familie.kontrakter.felles.oppdrag.KonsistensavstemmingRequestV2
import no.nav.familie.kontrakter.felles.oppdrag.KonsistensavstemmingUtbetalingsoppdrag
import no.nav.familie.oppdrag.common.RessursUtils.illegalState
import no.nav.familie.oppdrag.common.RessursUtils.ok
import no.nav.familie.oppdrag.repository.UtbetalingsoppdragForKonsistensavstemming
import no.nav.familie.oppdrag.service.Fagsystem
import no.nav.familie.oppdrag.service.GrensesnittavstemmingService
import no.nav.familie.oppdrag.service.KonsistensavstemmingService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Profile("never")
@RestController
@RequestMapping("/api")
@Validated
class AvstemmingController(
    @Autowired val grensesnittavstemmingService: GrensesnittavstemmingService,
    @Autowired val konsistensavstemmingService: KonsistensavstemmingService,
) {
    @PostMapping(path = ["/grensesnittavstemming"])
    fun grensesnittavstemming(
        @RequestBody request: GrensesnittavstemmingRequest,
    ): ResponseEntity<Ressurs<String>> {
        LOG.info("Grensesnittavstemming: Kjører for ${request.fagsystem}-oppdrag fra ${request.fra} til ${request.til}")

        return Result
            .runCatching { grensesnittavstemmingService.utførGrensesnittavstemming(request) }
            .fold(
                onFailure = { illegalState("Grensesnittavstemming feilet", it) },
                onSuccess = { ok("Grensesnittavstemming sendt ok") },
            )
    }

    /**
     * Konsistensavstemmingen virker i to moduser; en hvor avstemmingen sendes i en batch og en hvor batchen er splittet opp i flere batcher.
     * Første modusen gjør et kall til denne funksjonen og blir trigger hvis både sendStartmelding og sendAvsluttmelding er satt til true.
     * Andre modusen gjør flere kalle (en per delbranch) til denne funksjonen hvor sendStartmelding og sendAvsluttmelding skal settes som følger:
     * Første kallet: sendStartmelding=true og sendAvsluttmelding = false
     * Siste kallet: sendStartmelding=true og sendAvsluttmelding = false
     * Resterende kall: sendStartmelding=false og sendAvsluttmelding = false
     *
     * transaksjonsId må være satt hvis det er en splittet batch.
     */
    @PostMapping(path = ["/v2/konsistensavstemming"], consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun konsistensavstemming(
        @RequestBody request: KonsistensavstemmingRequestV2,
        @RequestParam(name = "sendStartmelding") sendStartmelding: Boolean = true,
        @RequestParam(name = "sendAvsluttmelding") sendAvsluttmelding: Boolean = true,
        @RequestParam(name = "transaksjonsId") transaksjonsId: UUID? = null,
    ): ResponseEntity<Ressurs<String>> {
        LOG.info(
            "Konsistensavstemming: Kjører for ${request.fagsystem}-oppdrag for ${request.avstemmingstidspunkt} " +
                "med ${request.perioderForBehandlinger.sumOf { it.perioder.size }} antall periodeIder",
        )

        return Result
            .runCatching {
                konsistensavstemmingService.utførKonsistensavstemming(request, sendStartmelding, sendAvsluttmelding, transaksjonsId)
            }.fold(
                onFailure = { illegalState("Konsistensavstemming feilet", it) },
                onSuccess = { ok("Konsistensavstemming sendt ok") },
            )
    }

    @PostMapping(path = ["/konsistensavstemming"], consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun konsistensavstemming(
        @RequestBody request: KonsistensavstemmingUtbetalingsoppdrag,
        @RequestParam(name = "sendStartmelding") sendStartmelding: Boolean = true,
        @RequestParam(name = "sendAvsluttmelding") sendAvsluttmelding: Boolean = true,
        @RequestParam(name = "transaksjonId") transaksjonId: UUID? = null,
    ): ResponseEntity<Ressurs<String>> {
        LOG.info(
            "Konsistensavstemming: Kjører for ${request.fagsystem}-oppdrag for ${request.avstemmingstidspunkt} " +
                "med ${request.utbetalingsoppdrag.size} antall oppdrag",
        )

        return Result
            .runCatching {
                konsistensavstemmingService.utførKonsistensavstemming(
                    request,
                    sendStartmelding = sendStartmelding,
                    sendAvsluttmelding = sendAvsluttmelding,
                    transaksjonsId = transaksjonId,
                )
            }.fold(
                onFailure = { illegalState("Konsistensavstemming feilet", it) },
                onSuccess = { ok("Konsistensavstemming sendt ok") },
            )
    }

    @PostMapping("/{fagsystem}/fagsaker/siste-utbetalingsoppdrag")
    fun hentSisteUtbetalingsoppdragForFagsaker(
        @PathVariable fagsystem: Fagsystem,
        @RequestBody fagsakIder: Set<String>,
    ): ResponseEntity<Ressurs<List<UtbetalingsoppdragForKonsistensavstemming>>> =
        ok(konsistensavstemmingService.hentSisteUtbetalingsoppdragForFagsaker(fagsystem.name, fagsakIder))

    companion object {
        val LOG: Logger = LoggerFactory.getLogger(AvstemmingController::class.java)
    }
}
