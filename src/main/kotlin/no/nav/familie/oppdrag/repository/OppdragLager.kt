package no.nav.familie.oppdrag.repository

import no.nav.familie.kontrakter.felles.oppdrag.OppdragId
import no.nav.familie.kontrakter.felles.oppdrag.OppdragStatus
import no.nav.familie.kontrakter.felles.oppdrag.Utbetalingsoppdrag
import no.nav.familie.kontrakter.felles.oppdrag.behandlingsIdForFørsteUtbetalingsperiode
import no.nav.familie.oppdrag.iverksetting.Jaxb
import no.trygdeetaten.skjema.oppdrag.Mmel
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import java.time.LocalDateTime
import java.util.UUID

data class OppdragLager(
    @Id
    @Column("id")
    val uuid: UUID = UUID.randomUUID(),
    val fagsystem: String,
    @Column("person_ident") val personIdent: String,
    @Column("fagsak_id") val fagsakId: String,
    @Column("behandling_id") val behandlingId: String,
    val utbetalingsoppdrag: Utbetalingsoppdrag,
    @Column("utgaaende_oppdrag") val utgåendeOppdrag: String,
    var status: OppdragStatus = OppdragStatus.LAGT_PÅ_KØ,
    @Column("avstemming_tidspunkt") val avstemmingTidspunkt: LocalDateTime,
    @Column("opprettet_tidspunkt") val opprettetTidspunkt: LocalDateTime = LocalDateTime.now(),
    val kvitteringsmelding: Mmel?,
    val versjon: Int = 0,
) {
    companion object {
        fun lagFraOppdrag(
            utbetalingsoppdrag: Utbetalingsoppdrag,
            oppdrag: Oppdrag,
            versjon: Int = 0,
        ): OppdragLager =
            OppdragLager(
                personIdent = utbetalingsoppdrag.aktoer,
                fagsystem = utbetalingsoppdrag.fagSystem,
                fagsakId = utbetalingsoppdrag.saksnummer,
                behandlingId = utbetalingsoppdrag.behandlingsIdForFørsteUtbetalingsperiode(),
                avstemmingTidspunkt = utbetalingsoppdrag.avstemmingTidspunkt,
                utbetalingsoppdrag = utbetalingsoppdrag,
                utgåendeOppdrag = Jaxb.tilXml(oppdrag),
                kvitteringsmelding = null,
                versjon = versjon,
            )
    }
}

val OppdragLager.id: OppdragId
    get() {
        return OppdragId(this.fagsystem, this.personIdent, this.behandlingId)
    }
