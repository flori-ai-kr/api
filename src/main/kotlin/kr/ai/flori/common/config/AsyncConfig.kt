package kr.ai.flori.common.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync

/**
 * @Async 활성화 (Discord 에러 리포팅을 응답 스레드와 분리).
 */
@Configuration
@EnableAsync
class AsyncConfig
