package no.nav.familie.oppdrag.iverksetting

import jakarta.jms.TextMessage
import no.nav.familie.kontrakter.felles.oppdrag.OppdragStatus
import no.nav.familie.oppdrag.config.ApplicationConfig.Companion.lokaleProfiler
import no.nav.familie.oppdrag.domene.id
import no.nav.familie.oppdrag.repository.OppdragLagerRepository
import no.nav.familie.oppdrag.repository.oppdragStatus
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.core.env.Environment
import org.springframework.jms.annotation.JmsListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!e2e")
class OppdragMottaker(
    val oppdragLagerRepository: OppdragLagerRepository,
    val env: Environment,
) {
    internal var log = LoggerFactory.getLogger(OppdragMottaker::class.java)
    private val secureLogger = LoggerFactory.getLogger("secureLogger")

    @Transactional
    @JmsListener(destination = "\${oppdrag.mq.mottak}", containerFactory = "jmsListenerContainerFactory")
    fun mottaKvitteringFraOppdrag(melding: TextMessage) {
        try {
            behandleMelding(melding)
        } catch (e: Exception) {
            secureLogger.warn("Feilet lesing av melding=${melding.jmsMessageID} meldingInnhold=$melding", e)
            secureLogger.info("Meldingsinnhold: =${melding.text}")
            throw e
        }
    }

    private fun behandleMelding(melding: TextMessage) {
        val svarFraOppdrag = håndterSvarFraOppdragSomGyldigXml(melding)

        val kvittering = lesKvittering(svarFraOppdrag)
        val oppdragId = kvittering.id
        log.info(
            "Mottatt melding på kvitteringskø for fagsak $oppdragId: Status ${kvittering.status}, se securelogg for beskrivende melding",
        )
        secureLogger.info(
            "Mottatt melding på kvitteringskø for fagsak $oppdragId: Status ${kvittering.status}, " +
                "svar ${kvittering.mmel?.beskrMelding ?: "Beskrivende melding ikke satt fra OS"}",
        )

        log.debug("Henter oppdrag $oppdragId fra databasen")

        val førsteOppdragUtenKvittering =
            oppdragLagerRepository
                .hentKvitteringsinformasjon(oppdragId)
                .find { oppdrag -> oppdrag.status == OppdragStatus.LAGT_PÅ_KØ }
        if (førsteOppdragUtenKvittering == null) {
            log.warn("Oppdraget tilknyttet mottatt kvittering har uventet status i databasen. Oppdraget er: $oppdragId")
            return
        }

        val oppdatertkvitteringsmelding = kvittering.mmel ?: førsteOppdragUtenKvittering.kvitteringsmelding
        val status = hentStatus(kvittering)
        log.debug("Lagrer oppdatert oppdrag $oppdragId i databasen med ny status $status")
        oppdragLagerRepository.oppdaterKvitteringsmelding(
            oppdragId = oppdragId,
            oppdragStatus = status,
            kvittering = oppdatertkvitteringsmelding,
            versjon = førsteOppdragUtenKvittering.versjon,
        )
    }

    private fun håndterSvarFraOppdragSomGyldigXml(melding: TextMessage): String {
        var svarFraOppdrag = melding.text as String
        if (!env.activeProfiles.any { it in lokaleProfiler }) {
            // TODO: Denne skal fjernes etter at oppdrag har prodsatt sin fiks som håndteres nedenfor.
            // TODO: Sjekk logger at denne if-branchen aldri treffes før den fjernes
            if (svarFraOppdrag.contains("ns2:oppdrag")) {
                log.info("Bytter <oppdrag xmlns med <ns2:oppdrag xmlns:ns2")
                svarFraOppdrag = svarFraOppdrag.replace("oppdrag xmlns", "ns2:oppdrag xmlns:ns2")
            } else if (svarFraOppdrag.contains("ns6:oppdrag")) {
                log.info("Bytter <oppdrag xmlns med <ns6:oppdrag xmlns:ns6")
                svarFraOppdrag = svarFraOppdrag.replace("oppdrag xmlns", "ns6:oppdrag xmlns:ns6")
            }
        }
        if (svarFraOppdrag.contains("<oppdrag xmlns=\"http://www.trygdeetaten.no/skjema/oppdrag\">")) {
            log.info("Bytter <oppdrag xmlns med <oppdrag uten xmlns")
            svarFraOppdrag =
                svarFraOppdrag.replace("<oppdrag xmlns=\"http://www.trygdeetaten.no/skjema/oppdrag\">", "<oppdrag>")
        }
        return svarFraOppdrag
    }

    /**
     * I dev og e2e settes status alltid til KVITTER_OK
     */
    private fun hentStatus(kvittering: Oppdrag) =
        if (!env.activeProfiles.contains("dev") && !env.activeProfiles.contains("e2e")) {
            kvittering.oppdragStatus
        } else {
            OppdragStatus.KVITTERT_OK
        }

    fun lesKvittering(svarFraOppdrag: String): Oppdrag = Jaxb.tilOppdrag(svarFraOppdrag)
}
