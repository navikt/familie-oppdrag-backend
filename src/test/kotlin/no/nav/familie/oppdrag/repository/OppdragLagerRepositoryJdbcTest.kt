package no.nav.familie.oppdrag.repository

import no.nav.familie.kontrakter.felles.oppdrag.OppdragStatus
import no.nav.familie.oppdrag.iverksetting.Jaxb
import no.nav.familie.oppdrag.util.TestConfig
import no.nav.familie.oppdrag.util.TestOppdragMedAvstemmingsdato.lagTestUtbetalingsoppdrag
import no.nav.familie.oppdrag.util.TestOppdragMedAvstemmingsdato.lagUtbetalingsperiode
import no.nav.familie.oppdrag.util.TestUtbetalingsoppdrag.utbetalingsoppdragMedTilfeldigAktoer
import no.trygdeetaten.skjema.oppdrag.Mmel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.test.assertFailsWith

@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@SpringBootTest(classes = [TestConfig::class])
@DisabledIfEnvironmentVariable(named = "CIRCLECI", matches = "true")
@Testcontainers
internal class OppdragLagerRepositoryJdbcTest {
    @Autowired lateinit var oppdragLagerRepository: OppdragLagerRepository

    @Autowired lateinit var jdbcTemplate: JdbcTemplate

    companion object {
        @Container
        private val postgreSQLContainer = PostgreSQLContainer<Nothing>("postgres:latest")

        @DynamicPropertySource
        @JvmStatic
        fun registerDynamicProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgreSQLContainer::getJdbcUrl)
            registry.add("spring.datasource.username", postgreSQLContainer::getUsername)
            registry.add("spring.datasource.password", postgreSQLContainer::getPassword)
        }
    }

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE oppdrag_lager")
    }

    @Test
    fun skal_ikke_lagre_duplikat() {
        val oppdragLager = utbetalingsoppdragMedTilfeldigAktoer().somOppdragLager

        oppdragLagerRepository.opprettOppdrag(oppdragLager)

        assertFailsWith<DuplicateKeyException> {
            oppdragLagerRepository.opprettOppdrag(oppdragLager)
        }
    }

    @Test
    fun skal_lagre_status() {
        val oppdragLager =
            utbetalingsoppdragMedTilfeldigAktoer()
                .somOppdragLager
                .copy(status = OppdragStatus.LAGT_PÅ_KØ)

        oppdragLagerRepository.opprettOppdrag(oppdragLager)

        val hentetOppdrag = oppdragLagerRepository.hentOppdrag(oppdragLager.id)
        assertEquals(OppdragStatus.LAGT_PÅ_KØ, hentetOppdrag.status)

        oppdragLagerRepository.oppdaterStatus(hentetOppdrag.id, OppdragStatus.KVITTERT_OK)

        val hentetOppdatertOppdrag = oppdragLagerRepository.hentOppdrag(hentetOppdrag.id)
        assertEquals(OppdragStatus.KVITTERT_OK, hentetOppdatertOppdrag.status)
    }

    @Test
    fun skal_lagre_kvitteringsmelding() {
        val oppdragLager =
            utbetalingsoppdragMedTilfeldigAktoer()
                .somOppdragLager
                .copy(status = OppdragStatus.LAGT_PÅ_KØ)

        oppdragLagerRepository.opprettOppdrag(oppdragLager)
        val hentetOppdrag = oppdragLagerRepository.hentKvitteringsinformasjon(oppdragLager.id).single()
        val kvitteringsmelding = kvitteringsmelding()

        oppdragLagerRepository.oppdaterKvitteringsmelding(hentetOppdrag.id, OppdragStatus.KVITTERT_OK, kvitteringsmelding, 0)

        val hentetOppdatertOppdrag = oppdragLagerRepository.hentOppdrag(oppdragLager.id)
        assertThat(hentetOppdatertOppdrag.status).isEqualTo(OppdragStatus.KVITTERT_OK)
        assertThat(hentetOppdatertOppdrag.kvitteringsmelding)
            .usingRecursiveComparison()
            .isEqualTo(kvitteringsmelding)
    }

    @Test
    fun `skal kun sette kvitteringsmeldingen til null`() {
        val oppdragLager =
            utbetalingsoppdragMedTilfeldigAktoer()
                .somOppdragLager
                .copy(status = OppdragStatus.LAGT_PÅ_KØ, kvitteringsmelding = kvitteringsmelding())

        oppdragLagerRepository.opprettOppdrag(oppdragLager)
        val hentetOppdrag = oppdragLagerRepository.hentKvitteringsinformasjon(oppdragLager.id).single()

        oppdragLagerRepository.oppdaterKvitteringsmelding(hentetOppdrag.id, OppdragStatus.KVITTERT_UKJENT, null, 0)

        val hentetOppdatertOppdrag = oppdragLagerRepository.hentOppdrag(oppdragLager.id)
        assertThat(hentetOppdatertOppdrag.status).isEqualTo(OppdragStatus.KVITTERT_UKJENT)
        assertThat(hentetOppdatertOppdrag.kvitteringsmelding).isNull()
    }

    private fun kvitteringsmelding(): Mmel {
        val kvitteringsmelding =
            Jaxb.tilOppdrag(
                this::class.java
                    .getResourceAsStream("/kvittering-avvist.xml")
                    .bufferedReader()
                    .use { it.readText() },
            )
        return kvitteringsmelding.mmel
    }

    @Test
    fun skal_kun_hente_ut_ett_BA_oppdrag_for_grensesnittavstemming() {
        val dag = LocalDateTime.now()
        val startenPåDagen = dag.withHour(0).withMinute(0)
        val sluttenAvDagen = dag.withHour(23).withMinute(59)

        val avstemmingsTidspunktetSomSkalKjøres = dag

        val baOppdragLager =
            lagTestUtbetalingsoppdrag(avstemmingsTidspunktetSomSkalKjøres, "BA").somOppdragLager
        val baOppdragLager2 = lagTestUtbetalingsoppdrag(dag.minusDays(1), "BA").somOppdragLager
        val efOppdragLager = lagTestUtbetalingsoppdrag(dag, "EFOG").somOppdragLager

        oppdragLagerRepository.opprettOppdrag(baOppdragLager)
        oppdragLagerRepository.opprettOppdrag(baOppdragLager2)
        oppdragLagerRepository.opprettOppdrag(efOppdragLager)

        val oppdrageneTilGrensesnittavstemming =
            oppdragLagerRepository.hentIverksettingerForGrensesnittavstemming(startenPåDagen, sluttenAvDagen, "BA", 1, 0)

        assertEquals(1, oppdrageneTilGrensesnittavstemming.size)
        assertEquals("BA", oppdrageneTilGrensesnittavstemming.first().fagsystem)
        assertEquals(
            avstemmingsTidspunktetSomSkalKjøres.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss")),
            oppdrageneTilGrensesnittavstemming.first().avstemmingTidspunkt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss")),
        )
    }

    @Test
    fun `skal kunne hente ut deler av grensesnittsavstemminger`() {
        val dag = LocalDate.now()

        fun hentOppdragForGrensesnittsavstemming(page: Int) =
            oppdragLagerRepository
                .hentIverksettingerForGrensesnittavstemming(
                    dag.atStartOfDay(),
                    dag.atTime(23, 59),
                    "BA",
                    2,
                    page,
                ).map { it.behandlingId.toInt() }

        val oppdrag1 =
            lagTestUtbetalingsoppdrag(
                dag.atTime(4, 0),
                "BA",
                utbetalingsperiode = arrayOf(lagUtbetalingsperiode(behandlingId = 3)),
            )
        val oppdrag2 =
            lagTestUtbetalingsoppdrag(
                dag.atTime(12, 0),
                "BA",
                utbetalingsperiode = arrayOf(lagUtbetalingsperiode(behandlingId = 1)),
            )
        val oppdrag3 =
            lagTestUtbetalingsoppdrag(
                dag.atTime(16, 0),
                "BA",
                utbetalingsperiode = arrayOf(lagUtbetalingsperiode(behandlingId = 2)),
            )
        listOf(oppdrag1, oppdrag2, oppdrag3).forEach { oppdragLagerRepository.opprettOppdrag(it.somOppdragLager) }

        assertThat(hentOppdragForGrensesnittsavstemming(page = 0)).containsExactly(1, 2)
        assertThat(hentOppdragForGrensesnittsavstemming(page = 1)).containsOnly(3)
        assertThat(hentOppdragForGrensesnittsavstemming(page = 2)).isEmpty()
        assertThat(hentOppdragForGrensesnittsavstemming(page = 4)).isEmpty()

        assertThat(
            oppdragLagerRepository.hentIverksettingerForGrensesnittavstemming(
                dag.minusDays(1).atStartOfDay(),
                dag.minusDays(1).atTime(23, 59),
                "BA",
                2,
                0,
            ),
        ).isEmpty()
    }

    @Test
    fun skal_hente_ut_oppdrag_for_konsistensavstemming() {
        val forrigeMåned = LocalDateTime.now().minusMonths(1)
        val baOppdragLager = lagTestUtbetalingsoppdrag(forrigeMåned, "BA").somOppdragLager
        val baOppdragLager2 =
            lagTestUtbetalingsoppdrag(forrigeMåned.minusDays(1), "BA").somOppdragLager
        oppdragLagerRepository.opprettOppdrag(baOppdragLager)
        oppdragLagerRepository.opprettOppdrag(baOppdragLager2)

        val utbetalingsoppdrag = oppdragLagerRepository.hentUtbetalingsoppdrag(baOppdragLager.id)
        val utbetalingsoppdrag2 = oppdragLagerRepository.hentUtbetalingsoppdrag(baOppdragLager2.id)

        assertEquals(baOppdragLager.utbetalingsoppdrag, utbetalingsoppdrag)
        assertEquals(baOppdragLager2.utbetalingsoppdrag, utbetalingsoppdrag2)
    }

    @Test
    fun `hentUtbetalingsoppdragForKonsistensavstemming går fint`() {
        val forrigeMåned = LocalDateTime.now().minusMonths(1)
        val utbetalingsoppdrag = lagTestUtbetalingsoppdrag(forrigeMåned, "BA")
        val baOppdragLager = utbetalingsoppdrag.somOppdragLager.copy(status = OppdragStatus.KVITTERT_OK)
        oppdragLagerRepository.opprettOppdrag(baOppdragLager)
        oppdragLagerRepository.opprettOppdrag(baOppdragLager, 1)
        oppdragLagerRepository.opprettOppdrag(baOppdragLager, 2)
        val behandlingB = baOppdragLager.copy(behandlingId = UUID.randomUUID().toString())
        oppdragLagerRepository.opprettOppdrag(behandlingB)

        oppdragLagerRepository.opprettOppdrag(
            baOppdragLager.copy(
                fagsakId = UUID.randomUUID().toString(),
                behandlingId = UUID.randomUUID().toString(),
            ),
        )
        assertThat(
            oppdragLagerRepository.hentUtbetalingsoppdragForKonsistensavstemming(
                baOppdragLager.fagsystem,
                setOf("finnes ikke"),
            ),
        ).isEmpty()

        assertThat(
            oppdragLagerRepository.hentUtbetalingsoppdragForKonsistensavstemming(
                baOppdragLager.fagsystem,
                setOf(baOppdragLager.behandlingId),
            ),
        ).hasSize(1)

        assertThat(
            oppdragLagerRepository.hentUtbetalingsoppdragForKonsistensavstemming(
                baOppdragLager.fagsystem,
                setOf(
                    baOppdragLager.behandlingId,
                    behandlingB.behandlingId,
                ),
            ),
        ).hasSize(2)
    }

    @Test
    fun `hentUtbetalingsoppdragForKonsistensavstemming test at oppdeling av spørring går fint`() {
        val forrigeMåned = LocalDateTime.now().minusMonths(1)
        val utbetalingsoppdrag = lagTestUtbetalingsoppdrag(forrigeMåned, "BA")
        val baOppdragLager = utbetalingsoppdrag.somOppdragLager.copy(status = OppdragStatus.KVITTERT_OK)

        oppdragLagerRepository.opprettOppdrag(baOppdragLager)
        val behandlingIder = mutableSetOf<String>()
        for (i in 1..5000) {
            val behandlingB = baOppdragLager.copy(behandlingId = baOppdragLager.behandlingId + i)
            behandlingIder.add(behandlingB.behandlingId)
            oppdragLagerRepository.opprettOppdrag(behandlingB)
        }

        assertThat(oppdragLagerRepository.hentUtbetalingsoppdragForKonsistensavstemming(baOppdragLager.fagsystem, behandlingIder))
            .hasSize(5000)
    }

    @Test
    fun `hentSisteUtbetalingsoppdragForFagsaker test spørring går fint`() {
        val forrigeMåned = LocalDateTime.now().minusMonths(1)
        val utbetalingsoppdrag1 = lagTestUtbetalingsoppdrag(forrigeMåned, "BA", fagsak = "1")
        val utbetalingsoppdrag2 =
            lagTestUtbetalingsoppdrag(forrigeMåned.minusDays(1), "BA", fagsak = "2")

        val oppdragLager1 = utbetalingsoppdrag1.somOppdragLager
        val oppdragLager2 = utbetalingsoppdrag2.somOppdragLager
        oppdragLagerRepository.opprettOppdrag(oppdragLager1)
        oppdragLagerRepository.opprettOppdrag(oppdragLager2)

        val hentedeOppdrag =
            oppdragLagerRepository.hentSisteUtbetalingsoppdragForFagsaker(
                fagsystem = oppdragLager1.fagsystem,
                fagsakIder = setOf(oppdragLager1.fagsakId, oppdragLager2.fagsakId),
            )

        assertThat(hentedeOppdrag.map { it.utbetalingsoppdrag }).containsAll(listOf(utbetalingsoppdrag1, utbetalingsoppdrag2))
    }
}
