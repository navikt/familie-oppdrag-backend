package no.nav.familie.oppdrag.config

import no.nav.familie.felles.tokenklient.entraid.EntraIDRestClientFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

@Configuration
class RestClientConfig {
    @Bean("sokosOppdragProxyRestClient")
    fun sokosOppdragProxyRestClient(
        entraIDRestClientFactory: EntraIDRestClientFactory,
        @Value("\${SOKOS_OPPDRAG_PROXY_SCOPE}") scope: String,
    ): RestClient =
        entraIDRestClientFactory
            .lagMaskinTilMaskinRestKlient(scope)
            .mutate()
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(Duration.ofSeconds(5))
                    setReadTimeout(Duration.ofSeconds(60))
                },
            ).build()
}
