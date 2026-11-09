package no.nav.familie.oppdrag.config

import no.nav.familie.kontrakter.felles.Ressurs
import no.nav.familie.kontrakter.felles.jsonMapper
import no.nav.familie.sikkerhet.context.FamilieFellesSpringSecurityKonfigurasjon
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler

@Configuration
@EnableWebSecurity
@Import(FamilieFellesSpringSecurityKonfigurasjon::class)
class SecurityConfig {
    private val logger = LoggerFactory.getLogger(SecurityConfig::class.java)

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            authorizeHttpRequests {
                authorize("/internal", permitAll)
                authorize("/internal/health/**", permitAll)
                authorize("/internal/metrics/**", permitAll)
                authorize("/internal/prometheus", permitAll)
                authorize("/v3/api-docs/**", permitAll)
                authorize("/swagger-ui/**", permitAll)
                authorize("/swagger-ui.html", permitAll)
                authorize(anyRequest, authenticated)
            }
            oauth2ResourceServer {
                jwt { }
                authenticationEntryPoint = authenticationEntryPoint()
                accessDeniedHandler = accessDeniedHandler()
            }
        }
        return http.build()
    }

    private fun authenticationEntryPoint() =
        AuthenticationEntryPoint { request, response, authException ->
            logger.debug("AuthenticationException caught: ${authException.message}")
            response.status = HttpStatus.UNAUTHORIZED.value()
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.writer.write(
                jsonMapper.writeValueAsString(Ressurs.failure<Nothing>("Unauthorized")),
            )
            response.writer.flush()
        }

    private fun accessDeniedHandler() =
        AccessDeniedHandler { request, response, accessDeniedException ->
            logger.debug("AccessDeniedException caught: ${accessDeniedException.message}")
            // Maintains backward compatibility - returns 401 instead of 403
            response.status = HttpStatus.FORBIDDEN.value()
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.writer.write(
                jsonMapper.writeValueAsString(Ressurs.failure<Nothing>("Forbidden")),
            )
            response.writer.flush()
        }
}
