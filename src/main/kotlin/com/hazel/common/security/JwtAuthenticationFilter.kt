package com.hazel.common.security

import com.hazel.common.tenant.TenantContext
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Bearer access 토큰을 검증해 SecurityContext와 TenantContext에 현재 사용자를 주입한다.
 * 요청 종료 시 TenantContext를 반드시 비운다(스레드 재사용 누수 방지).
 */
class JwtAuthenticationFilter(
    private val tokenProvider: JwtTokenProvider,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        try {
            resolveToken(request)?.let { token ->
                tokenProvider.parse(token)?.let { principal ->
                    val authentication =
                        UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            listOf(SimpleGrantedAuthority("ROLE_USER")),
                        )
                    SecurityContextHolder.getContext().authentication = authentication
                    TenantContext.set(principal.userId)
                }
            }
            filterChain.doFilter(request, response)
        } finally {
            TenantContext.clear()
        }
    }

    private fun resolveToken(request: HttpServletRequest): String? {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION) ?: return null
        return if (header.startsWith(BEARER_PREFIX)) header.substring(BEARER_PREFIX.length) else null
    }

    private companion object {
        const val BEARER_PREFIX = "Bearer "
    }
}
