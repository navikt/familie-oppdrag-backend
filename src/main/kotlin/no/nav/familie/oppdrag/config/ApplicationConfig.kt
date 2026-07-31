package no.nav.familie.oppdrag.config

import no.nav.familie.log.NavSystemtype
import no.nav.familie.log.filter.LogFilter
import no.nav.familie.log.filter.RequestTimeFilter
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.jetty.servlet.JettyServletWebServerFactory
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.web.server.servlet.ServletWebServerFactory
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootConfiguration
@EntityScan(ApplicationConfig.PAKKENAVN)
@ComponentScan(ApplicationConfig.PAKKENAVN, "no.nav.familie.felles.tokenklient")
@EnableScheduling
class ApplicationConfig {
    @Bean
    fun servletWebServerFactory(): ServletWebServerFactory {
        val serverFactory = JettyServletWebServerFactory()
        serverFactory.port = 8087
        return serverFactory
    }

    @Bean
    fun logFilter(): FilterRegistrationBean<LogFilter> {
        val filterRegistration = FilterRegistrationBean<LogFilter>()
        filterRegistration.setFilter(LogFilter(NavSystemtype.NAV_INTEGRASJON))
        filterRegistration.order = 1
        return filterRegistration
    }

    @Bean
    fun requestTimeFilter(): FilterRegistrationBean<RequestTimeFilter> {
        val filterRegistration = FilterRegistrationBean<RequestTimeFilter>()
        filterRegistration.setFilter(RequestTimeFilter())
        filterRegistration.order = 2
        return filterRegistration
    }

    companion object {
        const val PAKKENAVN = "no.nav.familie.oppdrag"
        val lokaleProfiler = setOf("dev", "e2e", "dev_psql_mq")
    }
}
