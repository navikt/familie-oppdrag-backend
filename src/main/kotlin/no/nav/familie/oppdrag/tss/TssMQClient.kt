package no.nav.familie.oppdrag.tss

import jakarta.jms.Message
import jakarta.jms.Session
import no.nav.familie.oppdrag.iverksetting.Jaxb
import no.rtv.namespacetss.ObjectFactory
import no.rtv.namespacetss.TssSamhandlerData
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.jms.core.JmsTemplate
import org.springframework.stereotype.Service
import java.util.UUID

@Profile("dev")
@Service
class TssMQClient(
    @Qualifier("jmsTemplateTss") private val jmsTemplateTss: JmsTemplate,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private val secureLogger: Logger = LoggerFactory.getLogger("secureLogger")

    private fun kallTss(rawRequest: String): String {
        val uuid = UUID.randomUUID().toString()
        try {
            val response: Message? =
                jmsTemplateTss.sendAndReceive { session: Session ->
                    val requestMessage = session.createTextMessage(rawRequest)
                    requestMessage.jmsCorrelationID = uuid
                    requestMessage
                }

            return if (response == null) {
                throw TssConnectionException("En feil oppsto i kallet til TSS. Response var null (timeout?)")
            } else {
                val responseAsString = response.getBody(String::class.java)
                secureLogger.info("Response fra tss=$responseAsString")
                responseAsString
            }
        } catch (e: Exception) {
            "Feil mot TSS med uuid=$uuid".apply {
                logger.info(this)
                secureLogger.info("$this request=$rawRequest")
            }
            when (e) {
                is TssException -> throw e
                else -> throw TssConnectionException("En feil oppsto i kallet til TSS", e)
            }
        }
    }

    fun getOrgInfo(tssSamhandlerIdent: TssSamhandlerIdent): TssSamhandlerData {
        val objectFactory = ObjectFactory()

        val samhandlerIDataB910Data =
            when (tssSamhandlerIdent.type) {
                TssSamhandlerIdentType.ORGNR -> {
                    val offIdData =
                        objectFactory.createTidOFF1().apply {
                            idOff = tssSamhandlerIdent.ident
                            kodeIdType = "ORG"
                            kodeSamhType = "INST"
                        }
                    val samhandlerIDataB910Data =
                        objectFactory.createSamhandlerIDataB910Type().apply {
                            brukerID = BRUKER_ID
                            historikk = "N"
                            ofFid = offIdData
                        }
                    samhandlerIDataB910Data
                }

                TssSamhandlerIdentType.TSS -> {
                    val samhandlerIDataB910Data =
                        objectFactory.createSamhandlerIDataB910Type().apply {
                            idOffTSS = tssSamhandlerIdent.ident
                            brukerID = BRUKER_ID
                            historikk = "N"
                        }
                    samhandlerIDataB910Data
                }
            }

        val servicerutiner =
            objectFactory.createTServicerutiner().apply {
                samhandlerIDataB910 = samhandlerIDataB910Data
            }
        val tssSamhandlerDataTssInputData =
            objectFactory.createTssSamhandlerDataTssInputData().apply {
                tssServiceRutine = servicerutiner
            }
        val tssSamhandlerData =
            objectFactory.createTssSamhandlerData().apply {
                tssInputData = tssSamhandlerDataTssInputData
            }
        val xml = Jaxb.tilXml(tssSamhandlerData)
        val rawResponse = kallTss(xml)
        return Jaxb.tilTssSamhandlerData(rawResponse)
    }

    fun søkOrgInfo(
        navn: String?,
        postNummer: String?,
        område: String?,
        side: Int,
    ): TssSamhandlerData {
        val objectFactory = ObjectFactory()
        val samhandlerIDataB940Data =
            objectFactory.createSamhandlerIDataB940Type().apply {
                brukerID = BRUKER_ID
                navnSamh = navn
                kodeSamhType = "INST"
                postNr = postNummer
                omrade = område
                buffnr = side.toString().padStart(3, '0')
            }

        val servicerutiner =
            objectFactory.createTServicerutiner().apply {
                samhandlerIDataB940 = samhandlerIDataB940Data
            }

        val tssSamhandlerDataTssInputData =
            objectFactory.createTssSamhandlerDataTssInputData().apply {
                tssServiceRutine = servicerutiner
            }
        val tssSamhandlerData =
            objectFactory.createTssSamhandlerData().apply {
                tssInputData = tssSamhandlerDataTssInputData
            }

        val rawResponse = kallTss(Jaxb.tilXml(tssSamhandlerData))
        return Jaxb.tilTssSamhandlerData(rawResponse)
    }

    companion object {
        const val BRUKER_ID = "familie-oppdrag"
    }
}
