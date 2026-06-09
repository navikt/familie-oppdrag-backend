package no.nav.familie.oppdrag.grensesnittavstemming

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.mockk.every
import io.mockk.mockk
import no.nav.familie.kontrakter.felles.oppdrag.GrensesnittavstemmingRequest
import no.nav.familie.oppdrag.avstemming.AvstemmingSender
import no.nav.familie.oppdrag.iverksetting.OppdragMapper
import no.nav.familie.oppdrag.repository.OppdragLager
import no.nav.familie.oppdrag.repository.OppdragLagerRepository
import no.nav.familie.oppdrag.repository.TidligereKjørtGrensesnittavstemming
import no.nav.familie.oppdrag.repository.TidligereKjørteGrensesnittavstemmingerRepository
import no.nav.familie.oppdrag.service.GrensesnittavstemmingService
import no.nav.familie.oppdrag.util.TestConfig
import no.nav.familie.oppdrag.util.TestOppdragMedAvstemmingsdato
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDateTime
import java.util.UUID

@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@SpringBootTest(classes = [TestConfig::class])
@Testcontainers
class GrensesnittavstemmingIdTest(
    @Autowired
    val tidligereKjørteGrensesnittavstemmingerRepository: TidligereKjørteGrensesnittavstemmingerRepository,
    @Autowired
    val jdbcTemplate: JdbcTemplate,
    @Autowired
    val oppdragLagerRepository: OppdragLagerRepository,
    @Autowired val oppdragMapper: OppdragMapper,
) {
    val avstemmingSender: AvstemmingSender = mockk()

    val grensesnittavstemmingService =
        GrensesnittavstemmingService(
            avstemmingSender = avstemmingSender,
            oppdragLagerRepository = oppdragLagerRepository,
            tidligereKjørteGrensesnittavstemmingerRepository = tidligereKjørteGrensesnittavstemmingerRepository,
            antall = 2,
        )

    companion object {
        protected fun initLoggingEventListAppender(): ListAppender<ILoggingEvent> {
            val listAppender = ListAppender<ILoggingEvent>()
            listAppender.start()
            return listAppender
        }

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

    private val listAppender = initLoggingEventListAppender()

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE tidligere_kjoerte_grensesnittavstemminger")
    }

    @Test
    fun `Skal kunne lagre avstemming Id`() {
        val uuid = UUID.randomUUID()
        tidligereKjørteGrensesnittavstemmingerRepository.insert(TidligereKjørtGrensesnittavstemming(uuid))

        val lagretKjørtGrensesnittavstemming = tidligereKjørteGrensesnittavstemmingerRepository.findById(uuid)
        Assertions.assertNotNull(lagretKjørtGrensesnittavstemming)
    }

    @Test
    fun `Skal ikke kjøre grensesnittavstemming dersom det allerede er kjørt på samme avstemmingId`() {
        val logger: Logger = LoggerFactory.getLogger(GrensesnittavstemmingService::class.java) as Logger
        logger.addAppender(listAppender)

        opprettUtbetalingsoppdrag()
        every { avstemmingSender.sendGrensesnittAvstemming(any()) } returns Unit

        val avstemmingId = UUID.randomUUID()
        grensesnittavstemmingService.utførGrensesnittavstemming(
            GrensesnittavstemmingRequest(
                fagsystem = "BA",
                fra = LocalDateTime.now().minusDays(2),
                til = LocalDateTime.now(),
                avstemmingId = avstemmingId,
            ),
        )

        grensesnittavstemmingService.utførGrensesnittavstemming(
            GrensesnittavstemmingRequest(
                fagsystem = "BA",
                fra = LocalDateTime.now().minusDays(2),
                til = LocalDateTime.now(),
                avstemmingId = avstemmingId,
            ),
        )

        val fullførtMeldinger = listAppender.list.filter { "Fullført grensesnittavstemming" in it.message }
        Assertions.assertEquals(1, fullførtMeldinger.size)
    }

    @Test
    fun `Skal være mulig å kjøre grensesnittavstemming selv om avstemmingId er null`() {
        val logger: Logger = LoggerFactory.getLogger(GrensesnittavstemmingService::class.java) as Logger
        logger.addAppender(listAppender)

        opprettUtbetalingsoppdrag()
        every { avstemmingSender.sendGrensesnittAvstemming(any()) } returns Unit

        grensesnittavstemmingService.utførGrensesnittavstemming(
            GrensesnittavstemmingRequest(
                fagsystem = "BA",
                fra = LocalDateTime.now().minusDays(2),
                til = LocalDateTime.now(),
                avstemmingId = null,
            ),
        )

        val fullførtMeldinger = listAppender.list.filter { "Fullført grensesnittavstemming" in it.message }
        Assertions.assertEquals(1, fullførtMeldinger.size)
    }

    @Test
    fun `Skal være mulig å kjøre grensesnittavstemming flere ganger når ikke avstemmingId er satt`() {
        val logger: Logger = LoggerFactory.getLogger(GrensesnittavstemmingService::class.java) as Logger
        logger.addAppender(listAppender)

        opprettUtbetalingsoppdrag()
        every { avstemmingSender.sendGrensesnittAvstemming(any()) } returns Unit

        grensesnittavstemmingService.utførGrensesnittavstemming(
            GrensesnittavstemmingRequest(
                fagsystem = "BA",
                fra = LocalDateTime.now().minusDays(2),
                til = LocalDateTime.now(),
                avstemmingId = null,
            ),
        )

        grensesnittavstemmingService.utførGrensesnittavstemming(
            GrensesnittavstemmingRequest(
                fagsystem = "BA",
                fra = LocalDateTime.now().minusDays(2),
                til = LocalDateTime.now(),
                avstemmingId = null,
            ),
        )

        val fullførtMeldinger = listAppender.list.filter { "Fullført grensesnittavstemming" in it.message }
        Assertions.assertEquals(2, fullførtMeldinger.size)
    }

    @Test
    fun `Skal være mulig å kjøre grensesnittavstemming flere ganger om man bruker forskjellig avstemmingId`() {
        val logger: Logger = LoggerFactory.getLogger(GrensesnittavstemmingService::class.java) as Logger
        logger.addAppender(listAppender)

        opprettUtbetalingsoppdrag()
        every { avstemmingSender.sendGrensesnittAvstemming(any()) } returns Unit

        grensesnittavstemmingService.utførGrensesnittavstemming(
            GrensesnittavstemmingRequest(
                fagsystem = "BA",
                fra = LocalDateTime.now().minusDays(2),
                til = LocalDateTime.now(),
                avstemmingId = UUID.randomUUID(),
            ),
        )

        grensesnittavstemmingService.utførGrensesnittavstemming(
            GrensesnittavstemmingRequest(
                fagsystem = "BA",
                fra = LocalDateTime.now().minusDays(2),
                til = LocalDateTime.now(),
                avstemmingId = UUID.randomUUID(),
            ),
        )

        val fullførtMeldinger = listAppender.list.filter { "Fullført grensesnittavstemming" in it.message }
        Assertions.assertEquals(2, fullførtMeldinger.size)
    }

    private fun opprettUtbetalingsoppdrag() {
        val utbetalingsoppdrag =
            TestOppdragMedAvstemmingsdato.lagTestUtbetalingsoppdrag(
                LocalDateTime.now().minusDays(1),
                "BA",
                utbetalingsperiode = arrayOf(TestOppdragMedAvstemmingsdato.lagUtbetalingsperiode()),
            )
        val oppdrag = oppdragMapper.tilOppdrag(oppdragMapper.tilOppdrag110(utbetalingsoppdrag))
        oppdragLagerRepository.opprettOppdrag(OppdragLager.lagFraOppdrag(utbetalingsoppdrag, oppdrag), 0)
    }
}
