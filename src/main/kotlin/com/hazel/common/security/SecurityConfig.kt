package com.hazel.common.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.hazel.common.error.ErrorCode
import com.hazel.common.error.ErrorResponse
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
import org.springframework.web.cors.CorsConfigurationSource

/**
 * 무상태(stateless) JWT 보안 구성.
 * 공개 경로: 인증 엔드포인트, 헬스체크, Swagger/OpenAPI. 그 외 전부 인증 필요.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties::class)
class SecurityConfig(
    private val tokenProvider: JwtTokenProvider,
    private val objectMapper: ObjectMapper,
    private val corsConfigurationSource: CorsConfigurationSource,
) {
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun authenticationManager(configuration: AuthenticationConfiguration): AuthenticationManager = configuration.authenticationManager

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            csrf { disable() }
            httpBasic { disable() }
            formLogin { disable() }
            cors { configurationSource = corsConfigurationSource }
            headers {
                // X-Frame-Options: DENY, X-Content-Type-Options: nosniff 는 기본값. Referrer-Policy만 추가.
                referrerPolicy { policy = ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN }
            }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            authorizeHttpRequests {
                authorize("/auth/**", permitAll)
                authorize("/health", permitAll)
                authorize("/actuator/**", permitAll)
                authorize("/v3/api-docs/**", permitAll)
                authorize("/swagger-ui/**", permitAll)
                authorize("/swagger-ui.html", permitAll)
                authorize(anyRequest, authenticated)
            }
            exceptionHandling {
                authenticationEntryPoint = authenticationEntryPoint()
            }
        }
        http.addFilterBefore(
            JwtAuthenticationFilter(tokenProvider),
            UsernamePasswordAuthenticationFilter::class.java,
        )
        return http.build()
    }

    /** 인증 실패 시 표준 JSON 에러(스택/디테일 노출 금지). */
    private fun authenticationEntryPoint() =
        org.springframework.security.web.AuthenticationEntryPoint { _, response, _ ->
            response.status = HttpStatus.UNAUTHORIZED.value()
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.characterEncoding = Charsets.UTF_8.name()
            response.writer.write(
                objectMapper.writeValueAsString(
                    ErrorResponse(ErrorCode.UNAUTHORIZED.name, ErrorCode.UNAUTHORIZED.defaultMessage),
                ),
            )
        }
}
