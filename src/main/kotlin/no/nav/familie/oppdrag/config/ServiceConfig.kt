package no.nav.familie.oppdrag.config

import no.nav.common.cxf.LoggingFeatureUtenBinaryOgUtenSamlTokenLogging
import no.nav.common.cxf.STSConfigurationUtil
import no.nav.common.cxf.StsConfig
import no.nav.familie.felles.tokenklient.entraid.EntraIDClient
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerFpService
import org.apache.cxf.frontend.ClientProxy
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean
import org.apache.cxf.ws.addressing.WSAddressingFeature
import org.apache.cxf.ws.security.SecurityConstants
import org.apache.cxf.ws.security.trust.STSClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
class ServiceConfig(
    @Value("\${SECURITYTOKENSERVICE_URL}") private val stsUrl: String,
    @Value("\${OPPDRAG_SERVICE_URL}") private val simulerFpServiceUrl: String,
) {
    @Bean
    fun stsConfig(
        @Value("\${SERVICEUSER_USERNAME}") systemuserUsername: String,
        @Value("\${SERVICEUSER_PASSWORD}") systemuserPassword: String,
    ): StsConfig =
        StsConfig
            .builder()
            .url(stsUrl)
            .username(systemuserUsername)
            .password(systemuserPassword)
            .build()

    /**
     * Simulering går fra GCP til Oppdrag i FSS via familie-ws-proxy. Både SOAP-kallet mot CICS og
     * det underliggende WS-Trust-kallet mot STS må gjennom proxyen, og begge må derfor ha et
     * Entra ID-token i `X-Proxy-Authorization`.
     *
     * Vi bygger porten med [JaxWsProxyFactoryBean] i stedet for `no.nav.common.cxf.CXFClient`, fordi
     * `CXFClient.build()` returnerer en dynamisk proxy som skjuler den underliggende CXF-klienten.
     * Uten tilgang til klienten får vi ikke lagt interceptoren på STS-klienten, som opprettes inne i
     * [STSConfigurationUtil].
     */
    @Profile("!e2e")
    @Bean
    fun simulerFpServicePort(
        stsConfig: StsConfig,
        entraIDClient: EntraIDClient,
        @Value("\${WS_PROXY_SCOPE}") wsProxyScope: String,
    ): SimulerFpService {
        val wsProxyAuthorizationInterceptor = WsProxyAuthorizationOutInterceptor(entraIDClient, wsProxyScope)

        val factoryBean =
            JaxWsProxyFactoryBean().apply {
                serviceClass = SimulerFpService::class.java
                address = simulerFpServiceUrl
                features.add(WSAddressingFeature())
                features.add(LoggingFeatureUtenBinaryOgUtenSamlTokenLogging(true)) // Maskerer SAML-tokenet i loggen.
                outInterceptors.add(wsProxyAuthorizationInterceptor)
            }

        val port = factoryBean.create(SimulerFpService::class.java) as SimulerFpService
        val client = ClientProxy.getClient(port)

        // Setter opp henting av SAML-token for systembrukeren. STS-klienten opprettes her inne.
        STSConfigurationUtil.configureStsForSystemUserInFSS(client, stsConfig)

        val stsClient = client.requestContext[SecurityConstants.STS_CLIENT] as STSClient
        stsClient.outInterceptors.add(wsProxyAuthorizationInterceptor)

        return port
    }
}
