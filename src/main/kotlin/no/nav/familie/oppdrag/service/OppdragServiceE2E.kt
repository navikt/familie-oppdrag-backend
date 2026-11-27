package no.nav.familie.oppdrag.service

import no.nav.familie.kontrakter.felles.oppdrag.OppdragId
import no.nav.familie.kontrakter.felles.oppdrag.OppdragStatus
import no.nav.familie.kontrakter.felles.oppdrag.Utbetalingsoppdrag
import no.nav.familie.oppdrag.domene.id
import no.nav.familie.oppdrag.repository.OppdragLager
import no.nav.familie.oppdrag.repository.OppdragLagerRepository
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("e2e")
class OppdragServiceE2E(
    @Autowired private val oppdragLagerRepository: OppdragLagerRepository,
) : OppdragService {
    @Transactional(rollbackFor = [Throwable::class])
    override fun opprettOppdrag(
        utbetalingsoppdrag: Utbetalingsoppdrag,
        oppdrag: Oppdrag,
        versjon: Int,
    ) {
        LOG.debug("Lagrer oppdrag i databasen " + oppdrag.id)
        oppdragLagerRepository.opprettOppdrag(OppdragLager.lagFraOppdrag(utbetalingsoppdrag, oppdrag), versjon)

        LOG.debug("Kvittering mottat ok " + oppdrag.id)
        oppdragLagerRepository.oppdaterStatus(oppdrag.id, OppdragStatus.KVITTERT_OK)
    }

    override fun hentStatusForOppdrag(oppdragId: OppdragId): OppdragLager = oppdragLagerRepository.hentOppdrag(oppdragId)

    override fun resendOppdrag(oppdragId: OppdragId): Unit = throw NotImplementedError("Ikke implementert")

    override fun opprettManuellKvitteringPåOppdrag(oppdragId: OppdragId): OppdragLager = throw NotImplementedError("Ikke implementert")

    companion object {
        val LOG = LoggerFactory.getLogger(OppdragServiceE2E::class.java)
    }
}
