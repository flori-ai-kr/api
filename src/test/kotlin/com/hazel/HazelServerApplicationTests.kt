package com.hazel

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class HazelServerApplicationTests {
    @Test
    fun contextLoads() {
        // 외부 의존성(DB 등) 없이 Spring 컨텍스트가 로딩되는지 검증한다.
    }
}
