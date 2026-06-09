package no.nav.familie.oppdrag.config

import org.springframework.context.annotation.Profile
import no.nav.common.cxf.CXFClient
import no.nav.common.cxf.StsConfig
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerFpService
import org.apache.cxf.interceptor.LoggingOutInterceptor
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Files
import java.nio.file.Paths

@Profile("never")
@Configuration
class ServiceConfig(
    @Value("\${SECURITYTOKENSERVICE_URL}") private val stsUrl: String,
    @Value("\${OPPDRAG_SERVICE_URL}") private val simulerFpServiceUrl: String,
) {
    @Bean
    fun systemuserUsername(
        @Value("\${vault.systembruker.username}") filePath: String,
    ): String {
        val path = Paths.get(filePath)
        return Files.readString(path)
    }

    @Bean
    fun systemuserPassword(
        @Value("\${vault.systembruker.password}") filePath: String,
    ): String {
        val path = Paths.get(filePath)
        return Files.readString(path)
    }

    @Bean
    fun stsConfig(
        systemuserUsername: String,
        systemuserPassword: String,
    ): StsConfig =
        StsConfig
            .builder()
            .url(stsUrl)
            .username(systemuserUsername)
            .password(systemuserPassword)
            .build()

    @Bean
    fun simulerFpServicePort(stsConfig: StsConfig): SimulerFpService =
        CXFClient(SimulerFpService::class.java)
            .address(simulerFpServiceUrl)
            .timeout(20000, 20000)
            .configureStsForSystemUser(stsConfig)
            .withOutInterceptor(LoggingOutInterceptor())
            .build()
}
