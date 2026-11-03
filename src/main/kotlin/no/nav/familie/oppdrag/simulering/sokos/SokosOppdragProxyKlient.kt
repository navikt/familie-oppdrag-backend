package no.nav.familie.oppdrag.simulering.sokos

import no.nav.familie.oppdrag.config.IntegrasjonException
import no.nav.familie.oppdrag.config.Integrasjonssystem
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Service
class SokosOppdragProxyKlient(
    @Value("\${SOKOS_OPPDRAG_PROXY_URL}") private val sokosOppdragProxyUrl: String,
    @Qualifier("sokosOppdragProxyRestClient") private val restClient: RestClient,
) {
    fun simulerBeregning(request: SokosSimulerBeregningRequest): SokosSimulerBeregningResponse {
        val respons =
            try {
                restClient
                    .post()
                    .uri("$sokosOppdragProxyUrl/api/osSimulerBeregningApi/simulerBeregning")
                    .body(request)
                    .retrieve()
                    .body<SokosSimulerBeregningResponse>()
            } catch (e: Exception) {
                throw IntegrasjonException(Integrasjonssystem.SIMULERING, "Feil ved kall mot sokos-oppdrag-proxy", e)
            }
        return respons ?: throw IntegrasjonException(Integrasjonssystem.SIMULERING, "Tomt svar fra sokos-oppdrag-proxy")
    }
}
