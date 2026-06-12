package no.nav.familie.oppdrag.config

import no.nav.common.cxf.STSConfigurationUtil
import no.nav.common.cxf.StsConfig
import no.nav.okonomi.tilbakekrevingservice.TilbakekrevingPortType
import org.apache.cxf.configuration.jsse.TLSClientParameters
import org.apache.cxf.ext.logging.LoggingFeature
import org.apache.cxf.ext.logging.LoggingInInterceptor
import org.apache.cxf.ext.logging.LoggingOutInterceptor
import org.apache.cxf.frontend.ClientProxy
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean
import org.apache.cxf.transport.http.HTTPConduit
import org.apache.cxf.ws.addressing.WSAddressingFeature
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import javax.xml.namespace.QName

private const val WSDL = "wsdl/no/nav/tilbakekreving/tilbakekreving-v1-tjenestespesifikasjon.wsdl"
private const val NAMESPACE = "http://okonomi.nav.no/tilbakekrevingService/"

@Profile("never")
@Configuration
class ØkonomiClientConfig(
    @Value("\${TILBAKEKREVING_V1_URL}") private val tilbakekrevingUrl: String,
) {
    private val service = QName(NAMESPACE, "TilbakekrevingService")
    private val port = QName(NAMESPACE, "TilbakekrevingServicePort")

    @Bean
    fun økonomiService(stsConfig: StsConfig): TilbakekrevingPortType {
        val factoryBean =
            JaxWsProxyFactoryBean().apply {
                wsdlURL = WSDL
                serviceName = service
                endpointName = port
                serviceClass = TilbakekrevingPortType::class.java
                address = tilbakekrevingUrl
                features.add(WSAddressingFeature())
                features.add(loggingFeature())
                outInterceptors.add(LoggingOutInterceptor())
                inInterceptors.add(LoggingInInterceptor())
            }
        return wrapWithSts(factoryBean.create(TilbakekrevingPortType::class.java), stsConfig).apply {
            disableCnCheck()
        }
    }

    private fun loggingFeature(): LoggingFeature {
        val loggingFeature = LoggingFeature()
        loggingFeature.setPrettyLogging(true)
        loggingFeature.setVerbose(true)
        return loggingFeature
    }

    private fun TilbakekrevingPortType.disableCnCheck() {
        val client = ClientProxy.getClient(this)
        val conduit = client.conduit as HTTPConduit
        val tlsParams = TLSClientParameters()
        tlsParams.isDisableCNCheck = true
        conduit.tlsClientParameters = tlsParams
    }

    private fun wrapWithSts(
        port: TilbakekrevingPortType,
        stsConfig: StsConfig,
    ): TilbakekrevingPortType {
        val client = ClientProxy.getClient(port)
        STSConfigurationUtil.configureStsForSystemUserInFSS(client, stsConfig)
        return port
    }
}
