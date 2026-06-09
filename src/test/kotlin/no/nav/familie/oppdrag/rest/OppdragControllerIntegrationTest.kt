package no.nav.familie.oppdrag.rest

import no.nav.familie.kontrakter.felles.Ressurs
import no.nav.familie.kontrakter.felles.oppdrag.OppdragId
import no.nav.familie.kontrakter.felles.oppdrag.OppdragStatus
import no.nav.familie.kontrakter.felles.oppdrag.oppdragId
import no.nav.familie.oppdrag.iverksetting.OppdragMapper
import no.nav.familie.oppdrag.repository.OppdragLagerRepository
import no.nav.familie.oppdrag.service.OppdragService
import no.nav.familie.oppdrag.util.Containers
import no.nav.familie.oppdrag.util.TestConfig
import no.nav.familie.oppdrag.util.TestUtbetalingsoppdrag.utbetalingsoppdragMedTilfeldigAktoer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.jms.annotation.EnableJms
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.shaded.org.awaitility.Awaitility.await
import java.time.Duration
import kotlin.test.assertEquals

@ActiveProfiles("dev")
@ContextConfiguration(initializers = [Containers.MQInitializer::class])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@SpringBootTest(classes = [TestConfig::class])
@EnableJms
@DisabledIfEnvironmentVariable(named = "CIRCLECI", matches = "true")
@Testcontainers
internal class OppdragControllerIntegrationTest {
    @Autowired lateinit var oppdragService: OppdragService

    @Autowired lateinit var oppdragLagerRepository: OppdragLagerRepository

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

        @Container var ibmMQContainer = Containers.ibmMQContainer
    }

    @Test
    fun `Test skal lagre oppdrag for utbetalingoppdrag`() {
        val mapper = OppdragMapper()
        val oppdragController = OppdragController(oppdragService, mapper)

        val utbetalingsoppdrag = utbetalingsoppdragMedTilfeldigAktoer()
        oppdragController.sendOppdrag(utbetalingsoppdrag)

        assertOppdragStatus(utbetalingsoppdrag.oppdragId, OppdragStatus.KVITTERT_OK)
    }

    @Test
    fun `Test skal returnere https statuscode 409 ved dobbel sending`() {
        val mapper = OppdragMapper()
        val oppdragController = OppdragController(oppdragService, mapper)

        val utbetalingsoppdrag = utbetalingsoppdragMedTilfeldigAktoer()

        val responseFørsteSending = oppdragController.sendOppdrag(utbetalingsoppdrag)
        assertEquals(HttpStatus.OK, responseFørsteSending.statusCode)
        assertEquals(Ressurs.Status.SUKSESS, responseFørsteSending.body?.status)

        val responseAndreSending = oppdragController.sendOppdrag(utbetalingsoppdrag)

        assertEquals(HttpStatus.CONFLICT, responseAndreSending.statusCode)
        assertEquals(Ressurs.Status.FEILET, responseAndreSending.body?.status)

        assertOppdragStatus(utbetalingsoppdrag.oppdragId, OppdragStatus.KVITTERT_OK)
    }

    @Test
    fun `skal kunne resende et oppdrag hvis statusen er funksjonell feil`() {
        val mapper = OppdragMapper()
        val oppdragController = OppdragController(oppdragService, mapper)

        val utbetalingsoppdrag = utbetalingsoppdragMedTilfeldigAktoer()
        oppdragController.sendOppdrag(utbetalingsoppdrag)
        oppdragLagerRepository.oppdaterStatus(utbetalingsoppdrag.oppdragId, OppdragStatus.KVITTERT_FUNKSJONELL_FEIL)

        oppdragController.resendOppdrag(utbetalingsoppdrag.oppdragId)
        assertOppdragStatus(utbetalingsoppdrag.oppdragId, OppdragStatus.KVITTERT_OK)
    }

    private fun assertOppdragStatus(
        oppdragId: OppdragId,
        oppdragStatus: OppdragStatus,
    ) {
        await()
            .pollInterval(Duration.ofMillis(200))
            .atMost(Duration.ofSeconds(10))
            .untilAsserted {
                assertThat(oppdragLagerRepository.hentOppdrag(oppdragId).status).isEqualTo(oppdragStatus)
            }
    }
}
