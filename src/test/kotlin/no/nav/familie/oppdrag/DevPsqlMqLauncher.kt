package no.nav.familie.oppdrag

import no.nav.familie.oppdrag.config.ApplicationConfig
import org.springframework.boot.builder.SpringApplicationBuilder
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import java.util.Properties

object DevPsqlMqLauncher {
    @JvmStatic
    fun main(args: Array<String>) {
        val psql =
            KPostgreSQLContainer("postgres:17.9")
                .withDatabaseName("familie-oppdrag")
                .withUsername("postgres")
                .withPassword("test")

        psql.start()

        val mq =
            KGenericContainer("ibmcom/mq")
                .withEnv("LICENSE", "accept")
                .withEnv("MQ_QMGR_NAME", "QM1")
                .withExposedPorts(1414, 9443)

        mq.start()

        val properties = Properties()
        properties["SPRING_DATASOURCE_URL_OVERRIDE"] = psql.jdbcUrl
        properties["SPRING_DATASOURCE_USERNAME_OVERRIDE"] = psql.username
        properties["SPRING_DATASOURCE_PASSWORD_OVERRIDE"] = psql.password
        properties["SPRING_DATASOURCE_DRIVER_OVERRIDE"] = "org.postgresql.Driver"
        properties.put("OPPDRAG_MQ_PORT_OVERRIDE", mq.getMappedPort(1414))
        properties.put("spring.profiles.active", "dev_psql_mq")

        SpringApplicationBuilder(ApplicationConfig::class.java)
            .properties(properties)
            .run(*args)
    }
}

class KPostgreSQLContainer(
    imageName: String,
) : PostgreSQLContainer<KPostgreSQLContainer>(imageName)

class KGenericContainer(
    imageName: String,
) : GenericContainer<KGenericContainer>(imageName)
