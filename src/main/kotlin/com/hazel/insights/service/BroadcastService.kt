package com.hazel.insights.service

import com.hazel.common.push.PushMessage
import com.hazel.common.push.PushService
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service

/**
 * 전체 활성 구독에 푸시 브로드캐스트(트렌드 수집 등). 영구실패 토큰은 비활성화.
 */
@Service
class BroadcastService(
    private val pushService: PushService,
    private val jdbcTemplate: JdbcTemplate,
) {
    fun broadcast(
        title: String,
        body: String,
    ): Int {
        val tokens =
            jdbcTemplate.queryForList(
                "SELECT endpoint FROM push_subscriptions WHERE is_active = TRUE",
                String::class.java,
            )
        tokens.forEach { token ->
            val result = pushService.send(PushMessage(token = token, title = title, body = body))
            if (result.tokenInvalid) {
                jdbcTemplate.update("UPDATE push_subscriptions SET is_active = FALSE WHERE endpoint = ?", token)
            }
        }
        return tokens.size
    }
}
