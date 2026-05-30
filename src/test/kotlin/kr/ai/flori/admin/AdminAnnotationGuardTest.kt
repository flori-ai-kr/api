package kr.ai.flori.admin

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.admin.gating.RequiresAdmin
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.util.ClassUtils
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 인가 회귀 가드: `/admin` 경로에 매핑된 모든 @RestController 는 클래스 레벨 @RequiresAdmin 을 가져야 한다.
 * 새 admin 컨트롤러가 어노테이션을 빠뜨리면(= 인가 우회) 이 테스트가 실패해 노출 회귀를 막는다.
 */
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class AdminAnnotationGuardTest {
    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Test
    fun `admin 경로 컨트롤러는 모두 @RequiresAdmin 을 가진다`() {
        val violations =
            applicationContext
                .getBeansWithAnnotation(RestController::class.java)
                .values
                .map { ClassUtils.getUserClass(it) }
                .filter { type ->
                    type
                        .getAnnotation(RequestMapping::class.java)
                        ?.value
                        ?.any { it.startsWith("/admin") } == true
                }.filterNot { it.isAnnotationPresent(RequiresAdmin::class.java) }
                .map { it.simpleName }

        assertThat(violations)
            .describedAs("@RequiresAdmin 누락 admin 컨트롤러(인가 우회 위험) — 클래스에 @RequiresAdmin 추가 필요")
            .isEmpty()
    }
}
