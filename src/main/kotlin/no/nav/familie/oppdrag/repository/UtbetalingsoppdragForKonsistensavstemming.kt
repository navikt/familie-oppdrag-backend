package no.nav.familie.oppdrag.repository

import no.nav.familie.oppdrag.kontrakt.Utbetalingsoppdrag

data class UtbetalingsoppdragForKonsistensavstemming(
    val fagsakId: String,
    val behandlingId: String,
    val utbetalingsoppdrag: Utbetalingsoppdrag,
)
