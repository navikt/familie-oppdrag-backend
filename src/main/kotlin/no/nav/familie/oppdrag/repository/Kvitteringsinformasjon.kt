package no.nav.familie.oppdrag.repository

import no.nav.familie.kontrakter.felles.jsonMapper
import no.nav.familie.kontrakter.felles.oppdrag.OppdragId
import no.nav.familie.kontrakter.felles.oppdrag.OppdragStatus
import no.trygdeetaten.skjema.oppdrag.Mmel
import org.springframework.jdbc.core.RowMapper
import tools.jackson.module.kotlin.readValue
import java.sql.ResultSet
import java.time.LocalDateTime

data class Kvitteringsinformasjon(
    val fagsystem: String,
    val personIdent: String,
    val fagsakId: String,
    val behandlingId: String,
    var status: OppdragStatus = OppdragStatus.LAGT_PÅ_KØ,
    val avstemmingTidspunkt: LocalDateTime,
    val opprettetTidspunkt: LocalDateTime,
    val kvitteringsmelding: Mmel?,
    val versjon: Int,
)

val Kvitteringsinformasjon.id: OppdragId
    get() {
        return OppdragId(this.fagsystem, this.personIdent, this.behandlingId)
    }

object KvitteringsinformasjonRowMapper : RowMapper<Kvitteringsinformasjon> {
    override fun mapRow(
        resultSet: ResultSet,
        rowNumbers: Int,
    ): Kvitteringsinformasjon =
        Kvitteringsinformasjon(
            fagsystem = resultSet.getString("fagsystem"),
            personIdent = resultSet.getString("person_ident"),
            fagsakId = resultSet.getString("fagsak_id"),
            behandlingId = resultSet.getString("behandling_id"),
            status = OppdragStatus.valueOf(resultSet.getString("status")),
            avstemmingTidspunkt = resultSet.getTimestamp("avstemming_tidspunkt").toLocalDateTime(),
            opprettetTidspunkt = resultSet.getTimestamp("opprettet_tidspunkt").toLocalDateTime(),
            kvitteringsmelding = resultSet.getString("kvitteringsmelding")?.let { jsonMapper.readValue(it) },
            versjon = resultSet.getInt("versjon"),
        )
}
