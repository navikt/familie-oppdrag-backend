package no.nav.familie.oppdrag.simulering

import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.Timer

object SimuleringMetrikker {
    /** Utfall-taggen holder feilede kall og tidsavbrudd adskilt fra vellykkede kall. */
    fun <T> tidta(
        metrikk: String,
        fagsystem: String,
        kall: () -> T,
    ): T {
        val tidtaking = Timer.start()
        var utfall = "feil"
        try {
            return kall().also { utfall = "ok" }
        } finally {
            tidtaking.stop(Metrics.timer(metrikk, "fagsystem", fagsystem, "utfall", utfall))
        }
    }
}
