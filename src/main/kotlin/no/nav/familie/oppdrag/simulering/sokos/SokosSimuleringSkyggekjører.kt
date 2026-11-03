package no.nav.familie.oppdrag.simulering.sokos

import io.micrometer.core.instrument.Metrics
import jakarta.annotation.PreDestroy
import no.nav.familie.kontrakter.felles.jsonMapper
import no.nav.familie.kontrakter.felles.oppdrag.Utbetalingsoppdrag
import no.nav.familie.kontrakter.felles.simulering.DetaljertSimuleringResultat
import no.nav.familie.oppdrag.config.IntegrasjonException
import no.nav.familie.oppdrag.featuretoggle.FeatureToggle
import no.nav.familie.oppdrag.featuretoggle.FeatureToggleService
import no.nav.familie.oppdrag.simulering.SimuleringMetrikker
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientResponseException
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Skyggekjører simulering mot sokos-oppdrag-proxy (REST) og sammenligner med SOAP-resultatet.
 *
 * SOAP er fasit og det eneste resultatet som returneres. Skyggekjøringen går på egne tråder med
 * begrenset kø, slik at feil eller treghet i REST-kallet aldri påvirker SOAP-flyten.
 */
@Service
class SokosSimuleringSkyggekjører(
    private val sokosOppdragProxyKlient: SokosOppdragProxyKlient,
    private val requestMapper: SokosSimulerBeregningRequestMapper,
    private val resultatMapper: SokosSimuleringResultatMapper,
    private val featureToggleService: FeatureToggleService,
) {
    private val executor =
        ThreadPoolExecutor(
            2,
            2,
            60,
            TimeUnit.SECONDS,
            ArrayBlockingQueue(KØSTØRRELSE),
            { runnable -> Thread(runnable, "sokos-simulering-skygge").apply { isDaemon = true } },
            ThreadPoolExecutor.AbortPolicy(),
        )

    fun skyggekjør(
        utbetalingsoppdrag: Utbetalingsoppdrag,
        soapResultat: DetaljertSimuleringResultat,
    ) {
        val fagsystem = utbetalingsoppdrag.fagSystem
        try {
            if (!featureToggleService.isEnabled(FeatureToggle.SKYGGEKJØR_SOKOS_SIMULERING, utbetalingsoppdrag.saksnummer)) {
                return
            }
            val mdc = MDC.getCopyOfContextMap()
            executor.execute {
                mdc?.let { MDC.setContextMap(it) }
                try {
                    simulerOgSammenlign(utbetalingsoppdrag, soapResultat)
                } finally {
                    MDC.clear()
                }
            }
        } catch (e: RejectedExecutionException) {
            tell(UTFALL_FORKASTET, fagsystem)
            logger.warn("Skyggekjøring av simulering forkastet, køen er full. saksnummer=${utbetalingsoppdrag.saksnummer}")
        } catch (e: Exception) {
            tell(UTFALL_FEIL, fagsystem, ÅRSAK_OPPSTART)
            logger.warn("Kunne ikke starte skyggekjøring av simulering. saksnummer=${utbetalingsoppdrag.saksnummer}", e)
        }
    }

    private fun simulerOgSammenlign(
        utbetalingsoppdrag: Utbetalingsoppdrag,
        soapResultat: DetaljertSimuleringResultat,
    ) {
        val saksnummer = utbetalingsoppdrag.saksnummer
        val fagsystem = utbetalingsoppdrag.fagSystem
        try {
            val request = requestMapper.tilSokosSimulerBeregningRequest(utbetalingsoppdrag)
            val respons =
                SimuleringMetrikker.tidta(METRIKK_TID, fagsystem) {
                    sokosOppdragProxyKlient.simulerBeregning(request)
                }
            val restResultat = resultatMapper.tilDetaljertSimuleringResultat(respons, utbetalingsoppdrag)

            val sammenligning = SimuleringSammenligner.sammenlign(soap = soapResultat, rest = restResultat)
            if (sammenligning.erLik) {
                tell(UTFALL_LIK, fagsystem)
                logger.info("Skyggesimulering lik SOAP. saksnummer=$saksnummer fagsystem=$fagsystem")
            } else {
                tell(UTFALL_ULIK, fagsystem)
                logger.warn(
                    "Skyggesimulering ulik SOAP. saksnummer=$saksnummer fagsystem=$fagsystem " +
                        "kunISoap=${sammenligning.kunISoap.size} kunIRest=${sammenligning.kunIRest.size} " +
                        "ulikMottakerType=${sammenligning.ulikMottakerType.size}",
                )
                secureLogger.warn(
                    "Skyggesimulering ulik SOAP. saksnummer=$saksnummer fagsystem=$fagsystem\n" +
                        "Avvik: ${jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(sammenligning)}\n" +
                        "REST-respons: ${jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(respons)}",
                )
            }
        } catch (e: Exception) {
            val årsak = feilårsak(e)
            tell(UTFALL_FEIL, fagsystem, årsak)
            logger.warn(
                "Skyggesimulering mot sokos-oppdrag-proxy feilet. saksnummer=$saksnummer fagsystem=$fagsystem årsak=$årsak " +
                    "soapTom=${soapResultat.simuleringMottaker.isEmpty()}",
            )
            secureLogger.warn("Skyggesimulering mot sokos-oppdrag-proxy feilet. saksnummer=$saksnummer", e)
        }
    }

    private fun tell(
        utfall: String,
        fagsystem: String,
        årsak: String = ÅRSAK_INGEN,
    ) = Metrics.counter(METRIKK_UTFALL, "utfall", utfall, "fagsystem", fagsystem, "aarsak", årsak).increment()

    @PreDestroy
    fun stopp() {
        executor.shutdown()
        executor.awaitTermination(10, TimeUnit.SECONDS)
    }

    companion object {
        private const val KØSTØRRELSE = 50
        private const val METRIKK_UTFALL = "simulering.skyggekjoring.utfall"
        private const val METRIKK_TID = "simulering.skyggekjoring.tid"
        private const val UTFALL_LIK = "lik"
        private const val UTFALL_ULIK = "ulik"
        private const val UTFALL_FEIL = "feil"
        private const val UTFALL_FORKASTET = "forkastet"
        private const val ÅRSAK_INGEN = "ingen"
        private const val ÅRSAK_OPPSTART = "oppstart"
        private const val MAKS_DYBDE_ÅRSAKSKJEDE = 10

        private val logger: Logger = LoggerFactory.getLogger(SokosSimuleringSkyggekjører::class.java)
        private val secureLogger: Logger = LoggerFactory.getLogger("secureLogger")

        /** Grov årsak til at skyggekjøringen feilet, uten personopplysninger, til metrikk og vanlig logg. */
        internal fun feilårsak(e: Exception): String =
            when {
                e is SokosSimuleringStatusFeil -> {
                    "statuskode"
                }

                e !is IntegrasjonException -> {
                    "mapping"
                }

                else -> {
                    // Lesefeil på svaret kommer som RestClientException, ikke ResourceAccessException, så hele kjeden sjekkes.
                    val årsak =
                        generateSequence(e.cause) { it.cause }
                            .take(MAKS_DYBDE_ÅRSAKSKJEDE)
                            .firstOrNull { it is RestClientResponseException || it is IOException }
                    when (årsak) {
                        is RestClientResponseException -> "http_${årsak.statusCode.value()}"
                        is SocketTimeoutException -> "timeout"
                        is IOException -> "nettverk"
                        else -> "respons"
                    }
                }
            }
    }
}
