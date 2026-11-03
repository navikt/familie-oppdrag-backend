package no.nav.familie.oppdrag.simulering.sokos

import no.nav.familie.oppdrag.config.IntegrasjonException
import no.nav.familie.oppdrag.config.Integrasjonssystem
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import java.net.URI

@Service
class SokosOppdragProxyKlient(
    @Value("\${SOKOS_OPPDRAG_PROXY_URL}")
    private val sokosOppdragProxyUri: String,
    @Qualifier("sokosOppdragProxyRestClient") private val restClient: RestClient,
) {
    fun simulerBeregning(request: SokosSimulerBeregningRequest): SokosSimulerBeregningResponse {
        val uri = URI.create("$sokosOppdragProxyUri/api/osSimulerBeregningApi/simulerBeregning")
        return try {
            restClient
                .post()
                .uri(uri)
                .body(request)
                .retrieve()
                .body<SokosSimulerBeregningResponse>()
                ?: throw IntegrasjonException(Integrasjonssystem.SIMULERING, "Tomt svar fra sokos-oppdrag-proxy")
        } catch (e: IntegrasjonException) {
            throw e
        } catch (e: Exception) {
            throw IntegrasjonException(Integrasjonssystem.SIMULERING, "Feil ved kall mot sokos-oppdrag-proxy", e)
        }
    }
}
