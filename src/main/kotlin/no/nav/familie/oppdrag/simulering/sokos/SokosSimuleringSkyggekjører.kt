package no.nav.familie.oppdrag.simulering.sokos

import io.micrometer.core.instrument.Metrics
import no.nav.familie.kontrakter.felles.jsonMapper
import no.nav.familie.kontrakter.felles.oppdrag.Utbetalingsoppdrag
import no.nav.familie.kontrakter.felles.simulering.DetaljertSimuleringResultat
import no.nav.familie.oppdrag.featuretoggle.FeatureToggle
import no.nav.familie.oppdrag.featuretoggle.FeatureToggleService
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.core.NestedExceptionUtils
import org.springframework.stereotype.Service
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Kjører simuleringen også mot sokos-oppdrag-proxy (REST) og sammenligner med SOAP, som er fasit og eneste svar.
 * Egne tråder med begrenset kø, så feil eller treghet i REST aldri påvirker SOAP-flyten.
 */
@Service
class SokosSimuleringSkyggekjører(
    private val sokosOppdragProxyKlient: SokosOppdragProxyKlient,
    private val featureToggleService: FeatureToggleService,
) {
    private val executor =
        ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, ArrayBlockingQueue(50)) { runnable ->
            Thread(runnable, "sokos-simulering-skygge").apply { isDaemon = true }
        }

    fun skyggekjør(
        utbetalingsoppdrag: Utbetalingsoppdrag,
        soapResultat: DetaljertSimuleringResultat,
    ) {
        try {
            if (!featureToggleService.isEnabled(FeatureToggle.SKYGGEKJØR_SOKOS_SIMULERING, utbetalingsoppdrag.saksnummer)) return
            val mdc = MDC.getCopyOfContextMap()
            executor.execute {
                MDC.setContextMap(mdc ?: emptyMap())
                simulerOgSammenlign(utbetalingsoppdrag, soapResultat)
            }
        } catch (e: RejectedExecutionException) {
            tell("forkastet", utbetalingsoppdrag)
            logger.warn("Skyggesimulering forkastet, køen er full. ${logginfo(utbetalingsoppdrag)}")
        } catch (e: Exception) {
            tell("feil", utbetalingsoppdrag)
            logger.warn("Skyggesimulering ikke startet. ${logginfo(utbetalingsoppdrag)}", e)
        }
    }

    private fun simulerOgSammenlign(
        utbetalingsoppdrag: Utbetalingsoppdrag,
        soapResultat: DetaljertSimuleringResultat,
    ) {
        try {
            val request = SokosSimulerBeregningRequestMapper.tilSokosSimulerBeregningRequest(utbetalingsoppdrag)
            val respons =
                Metrics
                    .timer("simulering.skyggekjoring.tid", "fagsystem", utbetalingsoppdrag.fagSystem)
                    .record<SokosSimulerBeregningResponse> { sokosOppdragProxyKlient.simulerBeregning(request) }
            val restResultat = SokosSimuleringResultatMapper.tilDetaljertSimuleringResultat(respons, utbetalingsoppdrag)

            val sammenligning = SimuleringSammenligner.sammenlign(soap = soapResultat, rest = restResultat)
            if (sammenligning.erLik) {
                tell("lik", utbetalingsoppdrag)
                logger.info("Skyggesimulering lik SOAP. ${logginfo(utbetalingsoppdrag)}")
            } else {
                tell("ulik", utbetalingsoppdrag)
                logger.warn(
                    "Skyggesimulering ulik SOAP. ${logginfo(utbetalingsoppdrag)} " +
                        "kunISoap=${sammenligning.kunISoap.size} kunIRest=${sammenligning.kunIRest.size}",
                )
                secureLogger.warn(
                    "Skyggesimulering ulik SOAP. ${logginfo(utbetalingsoppdrag)}\n" +
                        "Avvik: ${jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(sammenligning)}\n" +
                        "REST-respons: ${jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(respons)}",
                )
            }
        } catch (e: Exception) {
            tell("feil", utbetalingsoppdrag)
            // Bare klassenavnet på rotårsaken, fordi meldingen kan inneholde personopplysninger.
            val årsak = NestedExceptionUtils.getMostSpecificCause(e).javaClass.simpleName
            logger.warn("Skyggesimulering mot sokos-oppdrag-proxy feilet. ${logginfo(utbetalingsoppdrag)} årsak=$årsak")
            secureLogger.warn("Skyggesimulering mot sokos-oppdrag-proxy feilet. ${logginfo(utbetalingsoppdrag)}", e)
        }
    }

    private fun logginfo(utbetalingsoppdrag: Utbetalingsoppdrag) =
        "saksnummer=${utbetalingsoppdrag.saksnummer} fagsystem=${utbetalingsoppdrag.fagSystem}"

    private fun tell(
        utfall: String,
        utbetalingsoppdrag: Utbetalingsoppdrag,
    ) = Metrics.counter("simulering.skyggekjoring.utfall", "utfall", utfall, "fagsystem", utbetalingsoppdrag.fagSystem).increment()

    companion object {
        private val logger = LoggerFactory.getLogger(SokosSimuleringSkyggekjører::class.java)
        private val secureLogger = LoggerFactory.getLogger("secureLogger")
    }
}
