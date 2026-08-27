package no.nav.familie.oppdrag.service

import no.nav.familie.kontrakter.felles.oppdrag.OppdragId
import no.nav.familie.kontrakter.felles.oppdrag.OppdragStatus
import no.nav.familie.kontrakter.felles.oppdrag.Utbetalingsoppdrag
import no.nav.familie.oppdrag.domene.id
import no.nav.familie.oppdrag.iverksetting.Jaxb
import no.nav.familie.oppdrag.iverksetting.OppdragSender
import no.nav.familie.oppdrag.repository.OppdragLager
import no.nav.familie.oppdrag.repository.OppdragLagerRepository
import no.trygdeetaten.skjema.oppdrag.Mmel
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!e2e")
class OppdragServiceImpl(
    @Autowired private val oppdragSender: OppdragSender,
    @Autowired private val oppdragLagerRepository: OppdragLagerRepository,
) : OppdragService {
    @Transactional(rollbackFor = [Throwable::class])
    override fun opprettOppdrag(
        utbetalingsoppdrag: Utbetalingsoppdrag,
        oppdrag: Oppdrag,
        versjon: Int,
    ) {
        LOG.debug("Lagrer oppdrag i databasen " + oppdrag.id)
        try {
            oppdragLagerRepository.opprettOppdrag(OppdragLager.lagFraOppdrag(utbetalingsoppdrag, oppdrag), versjon)
        } catch (e: org.springframework.dao.DuplicateKeyException) {
            LOG.info("Oppdrag ${oppdrag.id} er allerede sendt.")
            throw OppdragAlleredeSendtException()
        }

        LOG.debug("Legger oppdrag på kø " + oppdrag.id)
        oppdragSender.sendOppdrag(oppdrag)
    }

    override fun hentStatusForOppdrag(oppdragId: OppdragId): OppdragLager = oppdragLagerRepository.hentOppdrag(oppdragId)

    @Transactional(rollbackFor = [Throwable::class])
    override fun resendOppdrag(oppdragId: OppdragId) {
        val oppdrag = oppdragLagerRepository.hentOppdrag(oppdragId)
        if (oppdrag.status != OppdragStatus.KVITTERT_FUNKSJONELL_FEIL) {
            throw UnsupportedOperationException("Kan ikke resende $oppdragId då status=${oppdrag.status}")
        }
        LOG.info("Resender $oppdragId")
        val oppdragXml = Jaxb.tilOppdrag(oppdrag.utgåendeOppdrag)
        oppdragLagerRepository.oppdaterStatus(oppdragId, OppdragStatus.LAGT_PÅ_KØ)
        oppdragSender.sendOppdrag(oppdragXml)
    }

    @Transactional
    override fun opprettManuellKvitteringPåOppdrag(oppdragId: OppdragId): OppdragLager {
        val oppdrag = oppdragLagerRepository.hentOppdrag(oppdragId)

        if (oppdrag.status != OppdragStatus.LAGT_PÅ_KØ) {
            throw OppdragHarAlleredeKvitteringException("Oppdrag med id $oppdragId er allerede kvittert ut.")
        }

        val manuellKvittering = Mmel().apply { beskrMelding = "Manuelt kvittert ut" }

        oppdragLagerRepository.oppdaterKvitteringsmelding(
            oppdragId = oppdragId,
            oppdragStatus = OppdragStatus.KVITTERT_OK,
            kvittering = manuellKvittering,
            versjon = oppdrag.versjon + 1,
        )

        return oppdrag
    }

    companion object {
        val LOG = LoggerFactory.getLogger(OppdragServiceImpl::class.java)
    }
}

class OppdragAlleredeSendtException : RuntimeException()

class OppdragHarAlleredeKvitteringException(
    melding: String,
) : RuntimeException(melding)
