package no.nav.familie.oppdrag.simulering

import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerFpService
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Profile("never")
@Service
class SimuleringSenderImpl(
    private val port: SimulerFpService,
) : SimuleringSender {
    @Override
    override fun hentSimulerBeregningResponse(simulerBeregningRequest: SimulerBeregningRequest?): SimulerBeregningResponse =
        port.simulerBeregning(simulerBeregningRequest)
}
