package no.nav.familie.oppdrag.repository

import no.nav.familie.kontrakter.felles.jsonMapper
import no.nav.familie.kontrakter.felles.oppdrag.OppdragStatus
import no.nav.familie.kontrakter.felles.oppdrag.Utbetalingsoppdrag
import no.trygdeetaten.skjema.oppdrag.Mmel
import org.springframework.jdbc.core.RowMapper
import tools.jackson.module.kotlin.readValue
import java.sql.ResultSet
import java.time.LocalDateTime

data class OppdragTilAvstemming(
    val fagsystem: String,
    val personIdent: String,
    val fagsakId: String,
    val behandlingId: String,
    val utbetalingsoppdrag: Utbetalingsoppdrag,
    var status: OppdragStatus = OppdragStatus.LAGT_PÅ_KØ,
    val avstemmingTidspunkt: LocalDateTime,
    val opprettetTidspunkt: LocalDateTime,
    val kvitteringsmelding: Mmel?,
)

object OppdragTilAvstemmingRowMapper : RowMapper<OppdragTilAvstemming> {
    override fun mapRow(
        resultSet: ResultSet,
        rowNumbers: Int,
    ): OppdragTilAvstemming =
        OppdragTilAvstemming(
            fagsystem = resultSet.getString("fagsystem"),
            personIdent = resultSet.getString("person_ident"),
            fagsakId = resultSet.getString("fagsak_id"),
            behandlingId = resultSet.getString("behandling_id"),
            utbetalingsoppdrag = jsonMapper.readValue(resultSet.getString("utbetalingsoppdrag")),
            status = OppdragStatus.valueOf(resultSet.getString("status")),
            avstemmingTidspunkt = resultSet.getTimestamp("avstemming_tidspunkt").toLocalDateTime(),
            opprettetTidspunkt = resultSet.getTimestamp("opprettet_tidspunkt").toLocalDateTime(),
            kvitteringsmelding = resultSet.getString("kvitteringsmelding")?.let { jsonMapper.readValue(it) },
        )
}
