package no.nav.familie.oppdrag.simulering

import no.nav.familie.kontrakter.felles.simulering.HentFeilutbetalingerFraSimuleringRequest
import no.nav.familie.kontrakter.felles.tilbakekreving.Ytelsestype
import no.nav.familie.oppdrag.repository.SimuleringLager
import no.nav.familie.oppdrag.repository.SimuleringLagerTjeneste
import no.nav.familie.oppdrag.simulering.util.lagTestUtbetalingsoppdragForFGBMedEttBarn
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.FilterType
import org.springframework.data.jdbc.core.JdbcAggregateOperations
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@SpringBootTest(classes = [SimuleringTjenesteImplTest.TestConfig::class])
@DisabledIfEnvironmentVariable(named = "CIRCLECI", matches = "true")
@Testcontainers
internal class SimuleringTjenesteImplTest {
    @Autowired lateinit var simuleringLagerTjeneste: SimuleringLagerTjeneste

    @Autowired lateinit var simuleringTjeneste: SimuleringTjeneste

    @Autowired
    private lateinit var jdbcAggregateOperations: JdbcAggregateOperations

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
    fun setup() {
        listOf(SimuleringLager::class).forEach { jdbcAggregateOperations.deleteAll(it.java) }
    }

    @Test
    fun `utførSimuleringOghentDetaljertSimuleringResultat skal lagre request og respons`() {
        val utbetalingsoppdrag = lagTestUtbetalingsoppdragForFGBMedEttBarn()

        val simuleringResultat = simuleringTjeneste.utførSimuleringOghentDetaljertSimuleringResultat(utbetalingsoppdrag)

        assertNotNull(simuleringResultat)

        val alleLagretSimuleringsLager = simuleringLagerTjeneste.finnAlleSimuleringsLager()
        assertEquals(1, alleLagretSimuleringsLager.size)
        val simuleringsLager = alleLagretSimuleringsLager[0]
        assertNotNull(simuleringsLager.requestXml)
        assertNotNull(simuleringsLager.responseXml)
    }

    @Test
    fun `hentFeilutbetalinger skal hente feilutbetalinger`() {
        val eksternFagsakId = "10001"
        val fagsystemsbehandlingId = "2054"
        val utbetalingsoppdrag = lesFil("/simulering/testdata/utbetalingsoppdrag_fagsak_10001_EFOG.txt")
        val requestXml = lesFil("/simulering/testdata/requestXML_fagsak_10001_EFOG.xml")
        val responsXml = lesFil("/simulering/testdata/responsXML_fagsak_10001_EFOG.xml")

        simuleringLagerTjeneste.lagreINyTransaksjon(
            SimuleringLager(
                fagsystem = "EFOG",
                fagsakId = eksternFagsakId,
                behandlingId = fagsystemsbehandlingId,
                utbetalingsoppdrag = utbetalingsoppdrag,
                requestXml = requestXml,
                responseXml = responsXml,
            ),
        )

        val feilutbetalingerFraSimulering =
            simuleringTjeneste
                .hentFeilutbetalinger(
                    HentFeilutbetalingerFraSimuleringRequest(
                        ytelsestype = Ytelsestype.OVERGANGSSTØNAD,
                        eksternFagsakId = eksternFagsakId,
                        fagsystemsbehandlingId = fagsystemsbehandlingId,
                    ),
                )
        assertTrue {
            feilutbetalingerFraSimulering.feilutbetaltePerioder.isNotEmpty() &&
                feilutbetalingerFraSimulering.feilutbetaltePerioder.size == 1
        }

        val feilutbetaltPeriode = feilutbetalingerFraSimulering.feilutbetaltePerioder[0]
        assertEquals(LocalDate.of(2022, 3, 1), feilutbetaltPeriode.fom)
        assertEquals(LocalDate.of(2022, 3, 31), feilutbetaltPeriode.tom)
        assertEquals(BigDecimal("10120.00"), feilutbetaltPeriode.feilutbetaltBeløp)
        assertEquals(BigDecimal("12570.00"), feilutbetaltPeriode.tidligereUtbetaltBeløp)
        assertEquals(BigDecimal("2450.00"), feilutbetaltPeriode.nyttBeløp)
    }

    @Test
    fun `hentFeilutbetalinger skal hente flere feilutbetalinger`() {
        val eksternFagsakId = "10002"
        val fagsystemsbehandlingId = "100067052"
        val utbetalingsoppdrag = lesFil("/simulering/testdata/utbetalingsoppdrag_fagsak_10002_BA.txt")
        val requestXml = lesFil("/simulering/testdata/requestXML_fagsak_10002_BA.xml")
        val responsXml = lesFil("/simulering/testdata/responsXML_fagsak_10002_BA.xml")

        simuleringLagerTjeneste.lagreINyTransaksjon(
            SimuleringLager(
                fagsystem = "BA",
                fagsakId = eksternFagsakId,
                behandlingId = fagsystemsbehandlingId,
                utbetalingsoppdrag = utbetalingsoppdrag,
                requestXml = requestXml,
                responseXml = responsXml,
            ),
        )

        val feilutbetalingerFraSimulering =
            simuleringTjeneste
                .hentFeilutbetalinger(
                    HentFeilutbetalingerFraSimuleringRequest(
                        ytelsestype = Ytelsestype.BARNETRYGD,
                        eksternFagsakId = eksternFagsakId,
                        fagsystemsbehandlingId = fagsystemsbehandlingId,
                    ),
                )
        assertTrue {
            feilutbetalingerFraSimulering.feilutbetaltePerioder.isNotEmpty() &&
                feilutbetalingerFraSimulering.feilutbetaltePerioder.size == 3
        }

        val feilutbetaltPeriode1 = feilutbetalingerFraSimulering.feilutbetaltePerioder[0]
        assertEquals(LocalDate.of(2022, 1, 1), feilutbetaltPeriode1.fom)
        assertEquals(LocalDate.of(2022, 1, 31), feilutbetaltPeriode1.tom)
        assertEquals(BigDecimal("1054.00"), feilutbetaltPeriode1.feilutbetaltBeløp)
        assertEquals(BigDecimal("1054.00"), feilutbetaltPeriode1.tidligereUtbetaltBeløp)
        assertEquals(BigDecimal("0.00"), feilutbetaltPeriode1.nyttBeløp)

        val feilutbetaltPeriode2 = feilutbetalingerFraSimulering.feilutbetaltePerioder[1]
        assertEquals(LocalDate.of(2022, 2, 1), feilutbetaltPeriode2.fom)
        assertEquals(LocalDate.of(2022, 2, 28), feilutbetaltPeriode2.tom)
        assertEquals(BigDecimal("1054.00"), feilutbetaltPeriode2.feilutbetaltBeløp)
        assertEquals(BigDecimal("1054.00"), feilutbetaltPeriode2.tidligereUtbetaltBeløp)
        assertEquals(BigDecimal("0.00"), feilutbetaltPeriode2.nyttBeløp)

        val feilutbetaltPeriode3 = feilutbetalingerFraSimulering.feilutbetaltePerioder[2]
        assertEquals(LocalDate.of(2022, 3, 1), feilutbetaltPeriode3.fom)
        assertEquals(LocalDate.of(2022, 3, 31), feilutbetaltPeriode3.tom)
        assertEquals(BigDecimal("1054.00"), feilutbetaltPeriode3.feilutbetaltBeløp)
        assertEquals(BigDecimal("1054.00"), feilutbetaltPeriode3.tidligereUtbetaltBeløp)
        assertEquals(BigDecimal("0.00"), feilutbetaltPeriode3.nyttBeløp)
    }

    @Test
    fun `hentFeilutbetalinger skal ikke hente feilutbetalinger når det ikke finnes feil postering`() {
        val eksternFagsakId = "10003"
        val fagsystemsbehandlingId = "3814"
        val utbetalingsoppdrag = lesFil("/simulering/testdata/utbetalingsoppdrag_fagsak_10003_EFBT.txt")
        val requestXml = lesFil("/simulering/testdata/requestXML_fagsak_10003_EFBT.xml")
        val responsXml = lesFil("/simulering/testdata/responsXML_fagsak_10003_EFBT.xml")

        simuleringLagerTjeneste.lagreINyTransaksjon(
            SimuleringLager(
                fagsystem = "EFBT",
                fagsakId = eksternFagsakId,
                behandlingId = fagsystemsbehandlingId,
                utbetalingsoppdrag = utbetalingsoppdrag,
                requestXml = requestXml,
                responseXml = responsXml,
            ),
        )

        val feilutbetalingerFraSimulering =
            simuleringTjeneste
                .hentFeilutbetalinger(
                    HentFeilutbetalingerFraSimuleringRequest(
                        ytelsestype = Ytelsestype.BARNETILSYN,
                        eksternFagsakId = eksternFagsakId,
                        fagsystemsbehandlingId = fagsystemsbehandlingId,
                    ),
                )
        assertTrue { feilutbetalingerFraSimulering.feilutbetaltePerioder.isEmpty() }
    }

    private fun lesFil(fileName: String): String {
        val url = requireNotNull(this::class.java.getResource(fileName)) { "fil med filnavn=$fileName finnes ikke" }
        return url.readText()
    }

    @Configuration
    @ComponentScan(
        basePackages = ["no.nav.familie.oppdrag"],
        excludeFilters = [ComponentScan.Filter(type = FilterType.REGEX, pattern = [".*[MQ].*"])],
    )
    class TestConfig
}
