package no.nav.familie.oppdrag.repository

import no.nav.familie.oppdrag.service.Fagsystem
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.FilterType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID
import kotlin.test.assertEquals

@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@SpringBootTest(
    classes = [MellomlagringKonsistensavstemmingRepositoryTest.TestConfig::class],
)
@DisabledIfEnvironmentVariable(named = "CIRCLECI", matches = "true")
@Testcontainers
internal class MellomlagringKonsistensavstemmingRepositoryTest {
    @Autowired lateinit var repository: MellomlagringKonsistensavstemmingRepository

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

    @Test
    fun `Test lesing aggregert beløp og antall oppgaver fra tom tabell`() {
        val transaksjonsId = UUID.randomUUID()
        assertEquals(0, repository.hentAggregertTotalBeløp(transaksjonsId))
        assertEquals(0, repository.hentAggregertAntallOppdrag(transaksjonsId))
    }

    @Test
    fun `Test lesing aggregert beløp og antall oppgaver`() {
        val transaksjonsId = UUID.randomUUID()

        repository.insert(opprettMellomlagringKonsistensavstemming(10, 161, transaksjonsId))
        repository.insert(opprettMellomlagringKonsistensavstemming(30, 222, transaksjonsId))

        assertEquals(383, repository.hentAggregertTotalBeløp(transaksjonsId))
        assertEquals(40, repository.hentAggregertAntallOppdrag(transaksjonsId))
    }

    @Test
    fun `Test nullstilling`() {
        val transaksjonsId = UUID.randomUUID()

        repository.insert(opprettMellomlagringKonsistensavstemming(10, 161, transaksjonsId))
        repository.insert(opprettMellomlagringKonsistensavstemming(30, 222, transaksjonsId))

        val transaksjonsId2 = UUID.randomUUID()
        assertEquals(0, repository.hentAggregertTotalBeløp(transaksjonsId2))
        assertEquals(0, repository.hentAggregertAntallOppdrag(transaksjonsId2))
    }

    fun opprettMellomlagringKonsistensavstemming(
        antallOppdrag: Int,
        totalBeløp: Long,
        transaksjonsId: UUID,
    ) = MellomlagringKonsistensavstemming(
        fagsystem = Fagsystem.BA,
        transaksjonsId = transaksjonsId,
        antallOppdrag = antallOppdrag,
        totalBeløp = totalBeløp,
    )

    @Configuration
    @ComponentScan(
        basePackages = ["no.nav.familie.oppdrag"],
        excludeFilters = [ComponentScan.Filter(type = FilterType.REGEX, pattern = [".*[MQ].*"])],
    )
    class TestConfig
}
