package no.nav.familie.oppdrag.config

import no.nav.familie.felles.tokenklient.entraid.EntraIDClient
import org.apache.cxf.message.Message
import org.apache.cxf.phase.AbstractPhaseInterceptor
import org.apache.cxf.phase.Phase

/**
 * Legger på `X-Proxy-Authorization` slik at familie-ws-proxy slipper kallet gjennom til FSS.
 *
 * Vi bruker `X-Proxy-Authorization` og ikke `Proxy-Authorization` fordi sistnevnte fjernes
 * automatisk av Java sin HttpClient på HTTPS-tilkoblinger.
 *
 * Token-klienten sin [no.nav.familie.felles.tokenklient.entraid.MaskinTilMaskinTokenInterceptor]
 * kan ikke brukes her, siden den er en Spring `ClientHttpRequestInterceptor` og
 * simulering-kallene går via CXF og ikke `RestClient`.
 */
class WsProxyAuthorizationOutInterceptor(
    private val entraIDClient: EntraIDClient,
    private val scope: String,
) : AbstractPhaseInterceptor<Message>(Phase.PRE_PROTOCOL) {
    override fun handleMessage(message: Message) {
        // Texas cacher tokenet, så det er ikke nødvendig med egen caching her.
        val token = entraIDClient.hentMaskinTilMaskinToken(scope)

        @Suppress("UNCHECKED_CAST")
        val eksisterendeHeadere = message[Message.PROTOCOL_HEADERS] as? MutableMap<String, List<String>>
        val headere = eksisterendeHeadere ?: mutableMapOf<String, List<String>>().also { message[Message.PROTOCOL_HEADERS] = it }

        headere[PROXY_AUTHORIZATION_HEADER] = listOf("Bearer $token")
    }

    companion object {
        private const val PROXY_AUTHORIZATION_HEADER = "X-Proxy-Authorization"
    }
}
