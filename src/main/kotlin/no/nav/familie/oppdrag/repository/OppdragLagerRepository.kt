package no.nav.familie.oppdrag.repository

import no.nav.familie.kontrakter.felles.oppdrag.OppdragId
import no.nav.familie.kontrakter.felles.oppdrag.OppdragStatus
import no.nav.familie.kontrakter.felles.oppdrag.Utbetalingsoppdrag
import no.trygdeetaten.skjema.oppdrag.Mmel
import java.time.LocalDateTime

interface OppdragLagerRepository {
    fun hentOppdrag(
        oppdragId: OppdragId,
        versjon: Int = 0,
    ): OppdragLager

    fun hentUtbetalingsoppdrag(
        oppdragId: OppdragId,
        versjon: Int = 0,
    ): Utbetalingsoppdrag

    fun opprettOppdrag(
        oppdragLager: OppdragLager,
        versjon: Int = 0,
    )

    fun oppdaterStatus(
        oppdragId: OppdragId,
        oppdragStatus: OppdragStatus,
        versjon: Int = 0,
    )

    fun hentKvitteringsinformasjon(oppdragId: OppdragId): List<Kvitteringsinformasjon>

    fun oppdaterKvitteringsmelding(
        oppdragId: OppdragId,
        oppdragStatus: OppdragStatus,
        kvittering: Mmel?,
        versjon: Int = 0,
    )

    fun hentIverksettingerForGrensesnittavstemming(
        fomTidspunkt: LocalDateTime,
        tomTidspunkt: LocalDateTime,
        fagOmråde: String,
        antall: Int,
        page: Int,
    ): List<OppdragTilAvstemming>

    fun hentUtbetalingsoppdragForKonsistensavstemming(
        fagsystem: String,
        behandlingIder: Set<String>,
    ): List<UtbetalingsoppdragForKonsistensavstemming>

    fun hentSisteUtbetalingsoppdragForFagsaker(
        fagsystem: String,
        fagsakIder: Set<String>,
    ): List<UtbetalingsoppdragForKonsistensavstemming>
}
