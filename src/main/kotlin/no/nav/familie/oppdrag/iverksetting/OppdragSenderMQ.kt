package no.nav.familie.oppdrag.iverksetting

import com.ibm.mq.jakarta.jms.MQQueue
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.jms.JmsException
import org.springframework.jms.core.JmsTemplate
import org.springframework.stereotype.Service

@Service
@Profile("!e2e")
class OppdragSenderMQ(
    val jmsTemplateUtgående: JmsTemplate,
    @Value("\${oppdrag.mq.enabled}") val erEnabled: String,
    @Value("\${oppdrag.mq.mottak}") val kvitteringsKø: String,
) : OppdragSender {
    override fun sendOppdrag(oppdrag: Oppdrag): String {
        if (!erEnabled.toBoolean()) {
            LOG.info("MQ-integrasjon mot oppdrag er skrudd av")
            throw UnsupportedOperationException("Kan ikke sende melding til oppdrag. Integrasjonen er skrudd av.")
        }

        val oppdragId =
            oppdrag.oppdrag110
                ?.oppdragsLinje150
                ?.lastOrNull()
                ?.henvisning
        val oppdragXml = Jaxb.tilXml(oppdrag)
        LOG.info(
            "Sender oppdrag for fagsystem=${oppdrag.oppdrag110.kodeFagomraade} og " +
                "fagsak=${oppdrag.oppdrag110.fagsystemId} behandling=$oppdragId til Oppdragsystemet",
        )
        try {
            jmsTemplateUtgående.send { session ->
                val msg = session.createTextMessage(oppdragXml)
                msg.jmsReplyTo = MQQueue(kvitteringsKø)
                msg
            }
        } catch (e: JmsException) {
            LOG.error("Klarte ikke sende Oppdrag til OS. Feil: ", e)
            throw e
        }
        return oppdrag.oppdrag110.fagsystemId
    }

    companion object {
        val LOG = LoggerFactory.getLogger(OppdragSenderMQ::class.java)
    }
}
