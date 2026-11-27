package no.nav.familie.oppdrag.repository

import no.nav.familie.kontrakter.felles.oppdrag.OppdragStatus
import no.nav.familie.kontrakter.felles.oppdrag.Utbetalingsoppdrag
import no.nav.familie.kontrakter.felles.oppdrag.behandlingsIdForFørsteUtbetalingsperiode
import no.nav.familie.oppdrag.iverksetting.OppdragMapper
import java.time.LocalDateTime

fun Utbetalingsoppdrag.somOppdragLagerMedVersjon(versjon: Int): OppdragLager {
    val tilOppdrag110 = OppdragMapper().tilOppdrag110(this)
    val oppdrag = OppdragMapper().tilOppdrag(tilOppdrag110)
    return OppdragLager.lagFraOppdrag(this, oppdrag, versjon)
}

val Utbetalingsoppdrag.somOppdragLager: OppdragLager
    get() {
        val tilOppdrag110 = OppdragMapper().tilOppdrag110(this)
        val oppdrag = OppdragMapper().tilOppdrag(tilOppdrag110)
        return OppdragLager.lagFraOppdrag(this, oppdrag)
    }

val Utbetalingsoppdrag.somAvstemming: OppdragTilAvstemming
    get() {
        return OppdragTilAvstemming(
            personIdent = this.aktoer,
            fagsystem = this.fagSystem,
            fagsakId = this.saksnummer,
            behandlingId = this.behandlingsIdForFørsteUtbetalingsperiode(),
            avstemmingTidspunkt = this.avstemmingTidspunkt,
            utbetalingsoppdrag = this,
            kvitteringsmelding = null,
            opprettetTidspunkt = LocalDateTime.now(),
        )
    }

val Utbetalingsoppdrag.somKvitteringsinformasjon: Kvitteringsinformasjon
    get() {
        return Kvitteringsinformasjon(
            fagsystem = this.fagSystem,
            personIdent = this.aktoer,
            fagsakId = this.saksnummer,
            behandlingId = this.behandlingsIdForFørsteUtbetalingsperiode(),
            avstemmingTidspunkt = this.avstemmingTidspunkt,
            kvitteringsmelding = null,
            opprettetTidspunkt = LocalDateTime.now(),
            status = OppdragStatus.LAGT_PÅ_KØ,
            versjon = 0,
        )
    }
