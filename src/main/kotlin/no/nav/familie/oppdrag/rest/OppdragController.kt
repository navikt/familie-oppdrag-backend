package no.nav.familie.oppdrag.rest

import jakarta.validation.Valid
import no.nav.familie.kontrakter.felles.Ressurs
import no.nav.familie.kontrakter.felles.jsonMapper
import no.nav.familie.kontrakter.felles.oppdrag.OppdragId
import no.nav.familie.kontrakter.felles.oppdrag.OppdragStatus
import no.nav.familie.kontrakter.felles.oppdrag.Utbetalingsoppdrag
import no.nav.familie.oppdrag.common.RessursUtils.conflict
import no.nav.familie.oppdrag.common.RessursUtils.illegalState
import no.nav.familie.oppdrag.common.RessursUtils.notFound
import no.nav.familie.oppdrag.common.RessursUtils.ok
import no.nav.familie.oppdrag.common.RessursUtils.secureLogger
import no.nav.familie.oppdrag.iverksetting.OppdragMapper
import no.nav.familie.oppdrag.service.OppdragAlleredeSendtException
import no.nav.familie.oppdrag.service.OppdragHarAlleredeKvitteringException
import no.nav.familie.oppdrag.service.OppdragService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Profile("never")
@RestController
@RequestMapping("/api")
class OppdragController(
    @Autowired val oppdragService: OppdragService,
    @Autowired val oppdragMapper: OppdragMapper,
) {
    private val logger = LoggerFactory.getLogger(OppdragController::class.java)

    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE], path = ["/oppdrag"])
    fun sendOppdrag(
        @Valid @RequestBody
        utbetalingsoppdrag: Utbetalingsoppdrag,
    ): ResponseEntity<Ressurs<String>> =
        Result
            .runCatching {
                val oppdrag110 = oppdragMapper.tilOppdrag110(utbetalingsoppdrag)
                val oppdrag = oppdragMapper.tilOppdrag(oppdrag110)

                oppdragService.opprettOppdrag(utbetalingsoppdrag, oppdrag, 0)
            }.fold(
                onFailure = {
                    if (it is OppdragAlleredeSendtException) {
                        conflict("Oppdrag er allerede sendt for saksnr ${utbetalingsoppdrag.saksnummer}.")
                    } else {
                        illegalState("Klarte ikke sende oppdrag for saksnr ${utbetalingsoppdrag.saksnummer}", it)
                    }
                },
                onSuccess = {
                    ok("Oppdrag sendt OK")
                },
            )

    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE], path = ["/oppdragPaaNytt/{versjon}"])
    fun sendOppdragPåNytt(
        @Valid @RequestBody
        utbetalingsoppdrag: Utbetalingsoppdrag,
        @PathVariable versjon: Int,
    ): ResponseEntity<Ressurs<String>> =
        Result
            .runCatching {
                val oppdrag110 = oppdragMapper.tilOppdrag110(utbetalingsoppdrag)
                val oppdrag = oppdragMapper.tilOppdrag(oppdrag110)

                oppdragService.opprettOppdrag(utbetalingsoppdrag, oppdrag, versjon)
            }.fold(
                onFailure = {
                    illegalState("Klarte ikke sende oppdrag for saksnr ${utbetalingsoppdrag.saksnummer}", it)
                },
                onSuccess = {
                    ok("Oppdrag sendt OK")
                },
            )

    @PostMapping("resend")
    fun resendOppdrag(
        @Valid @RequestBody
        oppdragId: OppdragId,
    ) {
        oppdragService.resendOppdrag(oppdragId)
    }

    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE], path = ["/status"])
    fun hentStatus(
        @Valid @RequestBody
        oppdragId: OppdragId,
    ): ResponseEntity<Ressurs<OppdragStatus>> =
        Result
            .runCatching { oppdragService.hentStatusForOppdrag(oppdragId) }
            .fold(
                onFailure = {
                    notFound("Fant ikke oppdrag med id $oppdragId")
                },
                onSuccess = {
                    if (!listOf(OppdragStatus.KVITTERT_OK, OppdragStatus.LAGT_PÅ_KØ).contains(it.status)) {
                        secureLogger.warn(
                            "Oppdrag $oppdragId har status ${it.status} og kvitteringsmelding: ${jsonMapper.writeValueAsString(
                                it.kvitteringsmelding,
                            )}",
                        )
                    }

                    ok(it.status, it.kvitteringsmelding?.beskrMelding ?: "")
                },
            )

    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE], path = ["/oppdrag/manuell-kvittering"])
    fun opprettManuellKvitteringPåOppdrag(
        @Valid @RequestBody
        oppdragId: OppdragId,
    ): ResponseEntity<Ressurs<OppdragStatus>> =
        Result
            .runCatching { oppdragService.opprettManuellKvitteringPåOppdrag(oppdragId) }
            .fold(
                onFailure = {
                    if (it is OppdragHarAlleredeKvitteringException) {
                        conflict("Oppdrag med id $oppdragId er allerede kvittert ut.")
                    } else {
                        illegalState("Klarte ikke opprette manuell kvittering for oppdrag med id $oppdragId", it)
                    }
                },
                onSuccess = {
                    ok(it.status, it.kvitteringsmelding?.beskrMelding ?: "")
                },
            )

    @GetMapping("timeout-test")
    fun testTimeout(
        @RequestParam(name = "sekunder") sekunder: Long,
    ): ResponseEntity<Ressurs<String>> {
        logger.info("Venter i $sekunder")
        Thread.sleep(sekunder * 1000L)
        logger.info("Ferdig med å vente i $sekunder")
        return ok("Ventet i $sekunder sekunder")
    }
}
