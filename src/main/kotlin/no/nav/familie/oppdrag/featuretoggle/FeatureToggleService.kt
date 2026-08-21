package no.nav.familie.oppdrag.featuretoggle

import no.nav.familie.unleash.UnleashContextFields
import no.nav.familie.unleash.UnleashService
import org.springframework.stereotype.Service

@Service
class FeatureToggleService(
    private val unleashService: UnleashService,
) {
    fun isEnabled(
        toggle: FeatureToggle,
        defaultValue: Boolean = false,
    ): Boolean = unleashService.isEnabled(toggle.navn, defaultValue)

    fun isEnabled(toggle: FeatureToggle, defaultValue: Boolean = false, fagsakId: String): Boolean =
        unleashService.isEnabled(toggle.navn,properties =
            mapOf(
                UnleashContextFields.FAGSAK_ID to fagsakId,
            ),)
}
