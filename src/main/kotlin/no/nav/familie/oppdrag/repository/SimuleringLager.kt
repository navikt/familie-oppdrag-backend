package no.nav.familie.oppdrag.repository

import no.nav.familie.kontrakter.felles.jsonMapper
import no.nav.familie.kontrakter.felles.oppdrag.Utbetalingsoppdrag
import no.nav.familie.kontrakter.felles.oppdrag.behandlingsIdForFørsteUtbetalingsperiode
import no.nav.familie.oppdrag.iverksetting.Jaxb
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime
import java.util.UUID

@Table("simulering_lager")
data class SimuleringLager(
    @Id val id: UUID = UUID.randomUUID(),
    val fagsystem: String,
    @Column("fagsak_id") val fagsakId: String,
    @Column("behandling_id") val behandlingId: String,
    val utbetalingsoppdrag: String,
    @Column("request_xml") val requestXml: String,
    @Column("response_xml") var responseXml: String? = null,
    @Column("opprettet_tidspunkt") val opprettetTidspunkt: LocalDateTime = LocalDateTime.now(),
) {
    companion object {
        fun lagFraOppdrag(
            utbetalingsoppdrag: Utbetalingsoppdrag,
            request: SimulerBeregningRequest,
        ): SimuleringLager =
            SimuleringLager(
                fagsystem = utbetalingsoppdrag.fagSystem,
                fagsakId = utbetalingsoppdrag.saksnummer,
                behandlingId = utbetalingsoppdrag.behandlingsIdForFørsteUtbetalingsperiode(),
                utbetalingsoppdrag = jsonMapper.writeValueAsString(utbetalingsoppdrag),
                requestXml = Jaxb.tilXml(request = request),
            )
    }
}
