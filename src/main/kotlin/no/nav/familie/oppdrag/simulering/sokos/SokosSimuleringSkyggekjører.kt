package no.nav.familie.oppdrag.simulering.sokos

import io.micrometer.core.instrument.Metrics
import jakarta.annotation.PreDestroy
import no.nav.familie.kontrakter.felles.jsonMapper
import no.nav.familie.kontrakter.felles.oppdrag.Utbetalingsoppdrag
import no.nav.familie.kontrakter.felles.simulering.DetaljertSimuleringResultat
import no.nav.familie.oppdrag.featuretoggle.FeatureToggle
import no.nav.familie.oppdrag.featuretoggle.FeatureToggleService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Service
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
            1,
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
            tell(UTFALL_FORKASTET)
            logger.warn("Skyggekjøring av simulering forkastet, køen er full. saksnummer=${utbetalingsoppdrag.saksnummer}")
        } catch (e: Exception) {
            tell(UTFALL_FEIL)
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
                Metrics.timer(METRIKK_TID).recordCallable {
                    sokosOppdragProxyKlient.simulerBeregning(request)
                }!!
            val restResultat = resultatMapper.tilDetaljertSimuleringResultat(respons, utbetalingsoppdrag)

            val sammenligning = SimuleringSammenligner.sammenlign(soap = soapResultat, rest = restResultat)
            if (sammenligning.erLik) {
                tell(UTFALL_LIK)
                logger.info("Skyggesimulering lik SOAP. saksnummer=$saksnummer fagsystem=$fagsystem")
            } else {
                tell(UTFALL_ULIK)
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
            tell(UTFALL_FEIL)
            logger.warn("Skyggesimulering mot sokos-oppdrag-proxy feilet. saksnummer=$saksnummer fagsystem=$fagsystem")
            secureLogger.warn("Skyggesimulering mot sokos-oppdrag-proxy feilet. saksnummer=$saksnummer", e)
        }
    }

    private fun tell(utfall: String) = Metrics.counter(METRIKK_UTFALL, "utfall", utfall).increment()

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

        private val logger: Logger = LoggerFactory.getLogger(SokosSimuleringSkyggekjører::class.java)
        private val secureLogger: Logger = LoggerFactory.getLogger("secureLogger")
    }
}
