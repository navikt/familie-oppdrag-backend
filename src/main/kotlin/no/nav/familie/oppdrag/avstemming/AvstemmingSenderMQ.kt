package no.nav.familie.oppdrag.avstemming

import no.nav.familie.oppdrag.grensesnittavstemming.JaxbGrensesnittAvstemmingsdata
import no.nav.familie.oppdrag.konsistensavstemming.JaxbKonsistensavstemming
import no.nav.virksomhet.tjenester.avstemming.informasjon.konsistensavstemmingsdata.v1.Konsistensavstemmingsdata
import no.nav.virksomhet.tjenester.avstemming.informasjon.konsistensavstemmingsdata.v1.SendAsynkronKonsistensavstemmingsdataRequest
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.Avstemmingsdata
import no.nav.virksomhet.tjenester.avstemming.v1.SendAsynkronKonsistensavstemmingsdata
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.jms.JmsException
import org.springframework.jms.core.JmsTemplate
import org.springframework.stereotype.Service

@Service
class AvstemmingSenderMQ(
    val jmsTemplateAvstemming: JmsTemplate,
    @Value("\${oppdrag.mq.enabled}") val erEnabled: String,
) : AvstemmingSender {
    override fun sendGrensesnittAvstemming(avstemmingsdata: Avstemmingsdata) {
        val avstemmingXml = JaxbGrensesnittAvstemmingsdata.tilXml(avstemmingsdata)
        leggPåKø(avstemmingXml)
    }

    override fun sendKonsistensAvstemming(avstemmingsdata: Konsistensavstemmingsdata) {
        val konsistensavstemmingRequest =
            SendAsynkronKonsistensavstemmingsdata().apply {
                request = SendAsynkronKonsistensavstemmingsdataRequest().apply { konsistensavstemmingsdata = avstemmingsdata }
            }

        val requestXml = JaxbKonsistensavstemming.tilXml(konsistensavstemmingRequest)
        leggPåKø(requestXml)
    }

    private fun leggPåKø(melding: String) {
        if (!erEnabled.toBoolean()) {
            LOG.info("MQ-integrasjon mot oppdrag er skrudd av. Kan ikke sende avstemming")
            throw UnsupportedOperationException("Kan ikke sende avstemming til oppdrag. Integrasjonen er skrudd av.")
        }

        try {
            jmsTemplateAvstemming.convertAndSend(
                "queue:///${jmsTemplateAvstemming.defaultDestinationName}?targetClient=1",
                melding,
            )
        } catch (e: JmsException) {
            LOG.error("Klarte ikke sende avstemming til OS. Feil: ", e)
            throw e
        }
    }

    companion object {
        val LOG = LoggerFactory.getLogger(AvstemmingSenderMQ::class.java)
    }
}
