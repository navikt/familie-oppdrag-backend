package no.nav.familie.oppdrag.repository

import no.nav.familie.kontrakter.felles.jsonMapper
import no.nav.familie.kontrakter.felles.oppdrag.OppdragId
import no.nav.familie.kontrakter.felles.oppdrag.OppdragStatus
import no.nav.familie.kontrakter.felles.oppdrag.Utbetalingsoppdrag
import no.trygdeetaten.skjema.oppdrag.Mmel
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import tools.jackson.module.kotlin.readValue
import java.sql.ResultSet
import java.time.LocalDateTime
import java.util.UUID

@Repository
class OppdragLagerRepositoryJdbc(
    val jdbcTemplate: NamedParameterJdbcTemplate,
) : OppdragLagerRepository {
    internal val log = LoggerFactory.getLogger(OppdragLagerRepositoryJdbc::class.java)

    override fun hentOppdrag(
        oppdragId: OppdragId,
        versjon: Int,
    ): OppdragLager {
        val hentStatement =
            "SELECT * FROM oppdrag_lager " +
                "WHERE behandling_id = :behandlingId AND person_ident = :personIdent AND fagsystem = :fagsystem " +
                "AND versjon = :versjon"

        val values =
            MapSqlParameterSource()
                .addValue("behandlingId", oppdragId.behandlingsId)
                .addValue("personIdent", oppdragId.personIdent)
                .addValue("fagsystem", oppdragId.fagsystem)
                .addValue("versjon", versjon)

        val listeAvOppdrag = jdbcTemplate.query(hentStatement, values, OppdragLagerRowMapper())

        return when (listeAvOppdrag.size) {
            0 -> {
                log.error("Feil ved henting av oppdrag. Fant ingen oppdrag med id $oppdragId")
                throw NoSuchElementException("Feil ved henting av oppdrag. Fant ingen oppdrag med id $oppdragId")
            }

            1 -> {
                listeAvOppdrag[0]
            }

            else -> {
                log.error("Feil ved henting av oppdrag. Fant fler oppdrag med id $oppdragId")
                throw Exception("Feil ved henting av oppdrag. Fant fler oppdrag med id $oppdragId")
            }
        }
    }

    override fun opprettOppdrag(
        oppdragLager: OppdragLager,
        versjon: Int,
    ) {
        val insertStatement =
            "INSERT INTO oppdrag_lager " +
                "(id, utgaaende_oppdrag, status, opprettet_tidspunkt, person_ident, fagsak_id, " +
                "behandling_id, fagsystem, avstemming_tidspunkt, utbetalingsoppdrag, versjon)" +
                " VALUES (:id,:utgåendeOppdrag,:status,:opprettetTid,:personIdent,:fagsakId," +
                ":behandlingId,:fagsystem,:avstemmingTid,:utbetalingsoppdrag,:versjon)"

        val values =
            MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("utgåendeOppdrag", oppdragLager.utgåendeOppdrag)
                .addValue("status", oppdragLager.status.name)
                .addValue("opprettetTid", oppdragLager.opprettetTidspunkt)
                .addValue("personIdent", oppdragLager.personIdent)
                .addValue("fagsakId", oppdragLager.fagsakId)
                .addValue("behandlingId", oppdragLager.behandlingId)
                .addValue("fagsystem", oppdragLager.fagsystem)
                .addValue("avstemmingTid", oppdragLager.avstemmingTidspunkt)
                .addValue("utbetalingsoppdrag", jsonMapper.writeValueAsString(oppdragLager.utbetalingsoppdrag))
                .addValue("versjon", versjon)

        jdbcTemplate.update(insertStatement, values)
    }

    override fun oppdaterStatus(
        oppdragId: OppdragId,
        oppdragStatus: OppdragStatus,
        versjon: Int,
    ) {
        val update =
            "UPDATE oppdrag_lager SET status = :status " +
                "WHERE person_ident = :personIdent " +
                "AND fagsystem = :fagsystem " +
                "AND behandling_id = :behandlingId " +
                "AND versjon = :versjon"

        val values =
            MapSqlParameterSource()
                .addValue("status", oppdragStatus.name)
                .addValue("personIdent", oppdragId.personIdent)
                .addValue("fagsystem", oppdragId.fagsystem)
                .addValue("behandlingId", oppdragId.behandlingsId)
                .addValue("versjon", versjon)

        jdbcTemplate.update(update, values)
    }

    override fun oppdaterKvitteringsmelding(
        oppdragId: OppdragId,
        oppdragStatus: OppdragStatus,
        kvittering: Mmel?,
        versjon: Int,
    ) {
        val updateStatement =
            "UPDATE oppdrag_lager SET status = :status, kvitteringsmelding = :kvitteringsmelding" +
                " WHERE person_ident = :personIdent AND fagsystem = :fagsystem AND behandling_id = :behandlingId AND versjon = :versjon"

        val values =
            MapSqlParameterSource()
                .addValue("status", oppdragStatus.name)
                .addValue("kvitteringsmelding", jsonMapper.writeValueAsString(kvittering))
                .addValue("personIdent", oppdragId.personIdent)
                .addValue("fagsystem", oppdragId.fagsystem)
                .addValue("behandlingId", oppdragId.behandlingsId)
                .addValue("versjon", versjon)

        jdbcTemplate.update(updateStatement, values)
    }

    override fun hentIverksettingerForGrensesnittavstemming(
        fomTidspunkt: LocalDateTime,
        tomTidspunkt: LocalDateTime,
        fagOmråde: String,
        antall: Int,
        page: Int,
    ): List<OppdragTilAvstemming> {
        val hentStatement = """
            SELECT 
            status, opprettet_tidspunkt, person_ident, fagsak_id, behandling_id, fagsystem, avstemming_tidspunkt, utbetalingsoppdrag, kvitteringsmelding
            FROM oppdrag_lager 
            WHERE avstemming_tidspunkt >= :fomTidspunkt AND avstemming_tidspunkt < :tomTidspunkt AND fagsystem = :fagsystem 
            ORDER BY behandling_id ASC OFFSET :offset LIMIT :limit
            """
        val values =
            MapSqlParameterSource()
                .addValue("fomTidspunkt", fomTidspunkt)
                .addValue("tomTidspunkt", tomTidspunkt)
                .addValue("fagsystem", fagOmråde)
                .addValue("offset", page * antall)
                .addValue("limit", antall)

        return jdbcTemplate.query(hentStatement, values, OppdragTilAvstemmingRowMapper)
    }

    override fun hentUtbetalingsoppdrag(
        oppdragId: OppdragId,
        versjon: Int,
    ): Utbetalingsoppdrag {
        val hentStatement =
            "SELECT utbetalingsoppdrag FROM oppdrag_lager " +
                "WHERE behandling_id = :behandlingId " +
                "AND person_ident = :personIdent " +
                "AND fagsystem = :fagsystem " +
                "AND versjon = :versjon"

        val values =
            MapSqlParameterSource()
                .addValue("behandlingId", oppdragId.behandlingsId)
                .addValue("personIdent", oppdragId.personIdent)
                .addValue("fagsystem", oppdragId.fagsystem)
                .addValue("versjon", versjon)

        val jsonUtbetalingsoppdrag =
            jdbcTemplate.queryForObject(hentStatement, values, String::class.java)
                ?: error("Fant ikke utbetalingsoppdrag for $oppdragId versjon=$versjon")

        return jsonMapper.readValue(jsonUtbetalingsoppdrag)
    }

    override fun hentKvitteringsinformasjon(oppdragId: OppdragId): List<Kvitteringsinformasjon> {
        val hentStatement = """
            SELECT 
            fagsystem, person_ident, fagsak_id, behandling_id, status, avstemming_tidspunkt, opprettet_tidspunkt, kvitteringsmelding, versjon 
            FROM oppdrag_lager WHERE behandling_id = :behandlingId AND person_ident = :personIdent AND fagsystem = :fagsystem"""

        val values =
            MapSqlParameterSource()
                .addValue("behandlingId", oppdragId.behandlingsId)
                .addValue("personIdent", oppdragId.personIdent)
                .addValue("fagsystem", oppdragId.fagsystem)

        return jdbcTemplate.query(hentStatement, values, KvitteringsinformasjonRowMapper)
    }

    override fun hentUtbetalingsoppdragForKonsistensavstemming(
        fagsystem: String,
        behandlingIder: Set<String>,
    ): List<UtbetalingsoppdragForKonsistensavstemming> {
        val query = """SELECT fagsak_id, behandling_id, utbetalingsoppdrag FROM (
                        SELECT fagsak_id, behandling_id, utbetalingsoppdrag, 
                          ROW_NUMBER() OVER (PARTITION BY fagsak_id, behandling_id ORDER BY versjon DESC) rn
                          FROM oppdrag_lager WHERE fagsystem=:fagsystem AND behandling_id IN (:behandlingIder)
                          AND status IN (:status)) q 
                        WHERE rn = 1"""

        val status = setOf(OppdragStatus.KVITTERT_OK, OppdragStatus.KVITTERT_MED_MANGLER).map { it.name }

        return behandlingIder
            .chunked(3000)
            .map { behandlingIderChunked ->
                val values =
                    MapSqlParameterSource()
                        .addValue("fagsystem", fagsystem)
                        .addValue("behandlingIder", behandlingIderChunked)
                        .addValue("status", status)

                jdbcTemplate.query(query, values) { resultSet, _ ->
                    UtbetalingsoppdragForKonsistensavstemming(
                        resultSet.getString("fagsak_id"),
                        resultSet.getString("behandling_id"),
                        jsonMapper.readValue(resultSet.getString("utbetalingsoppdrag")),
                    )
                }
            }.flatten()
    }

    override fun hentSisteUtbetalingsoppdragForFagsaker(
        fagsystem: String,
        fagsakIder: Set<String>,
    ): List<UtbetalingsoppdragForKonsistensavstemming> {
        val sqlSpørring =
            """
            SELECT fagsak_id, behandling_id, utbetalingsoppdrag
            FROM oppdrag_lager
            WHERE (fagsak_id, opprettet_tidspunkt) IN (
                SELECT fagsak_id, MAX(opprettet_tidspunkt)
                FROM oppdrag_lager
                WHERE fagsystem=:fagsystem and fagsak_id IN (:fagsakIder)
                GROUP BY fagsak_id
            )
            """.trimIndent()

        return fagsakIder
            .chunked(3000)
            .flatMap { fagsakIderChunked ->
                val parametere = mapOf("fagsakIder" to fagsakIderChunked, "fagsystem" to fagsystem)

                jdbcTemplate
                    .query(sqlSpørring, parametere) { resultSet, _ ->
                        UtbetalingsoppdragForKonsistensavstemming(
                            resultSet.getString("fagsak_id"),
                            resultSet.getString("behandling_id"),
                            jsonMapper.readValue(resultSet.getString("utbetalingsoppdrag")),
                        )
                    }
            }
    }
}

class OppdragLagerRowMapper : RowMapper<OppdragLager> {
    override fun mapRow(
        resultSet: ResultSet,
        rowNumbers: Int,
    ): OppdragLager =
        OppdragLager(
            uuid = UUID.fromString(resultSet.getString("id") ?: UUID.randomUUID().toString()),
            fagsystem = resultSet.getString("fagsystem"),
            personIdent = resultSet.getString("person_ident"),
            fagsakId = resultSet.getString("fagsak_id"),
            behandlingId = resultSet.getString("behandling_id"),
            utbetalingsoppdrag = jsonMapper.readValue(resultSet.getString("utbetalingsoppdrag")),
            utgåendeOppdrag = resultSet.getString("utgaaende_oppdrag"),
            status = OppdragStatus.valueOf(resultSet.getString("status")),
            avstemmingTidspunkt = resultSet.getTimestamp("avstemming_tidspunkt").toLocalDateTime(),
            opprettetTidspunkt = resultSet.getTimestamp("opprettet_tidspunkt").toLocalDateTime(),
            kvitteringsmelding = resultSet.getString("kvitteringsmelding")?.let { jsonMapper.readValue(it) },
            versjon = resultSet.getInt("versjon"),
        )
}
