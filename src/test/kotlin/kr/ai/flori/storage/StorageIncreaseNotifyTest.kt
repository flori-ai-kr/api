package kr.ai.flori.storage

import kr.ai.flori.common.notification.discord.DiscordChannel
import kr.ai.flori.common.notification.discord.DiscordMessage
import kr.ai.flori.common.notification.discord.DiscordNotifier
import kr.ai.flori.storage.event.StorageIncreaseRequestedEvent
import kr.ai.flori.storage.listener.StorageIncreaseRequestedListener
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito

/**
 * StorageIncreaseRequestedListener 단위 테스트.
 *
 * springmockk(@SpykBean) 미사용 이유: com.ninjasquad.springmockk 의존성이 없음.
 * @Async·AFTER_COMMIT 동기화 복잡성을 피하기 위해 리스너를 직접 인스턴스화하고
 * handle() 을 직접 호출하는 순수 단위 테스트로 작성. Spring 컨텍스트·DB 불필요.
 *
 * Mockito ArgumentMatchers 를 쓰지 않는 이유: Kotlin 2.x call-site null-check 때문에
 * non-nullable 파라미터 자리에 null matcher 를 넘기면 NPE 가 발생함.
 * mockingDetails().invocations 로 실제 인자를 직접 꺼내 검증한다.
 */
class StorageIncreaseNotifyTest {
    private val notifier: DiscordNotifier = Mockito.mock(DiscordNotifier::class.java)
    private val listener = StorageIncreaseRequestedListener(notifier)

    @Test
    fun `증설 요청 이벤트를 받으면 SUPPORT 채널로 본문을 갖춘 Discord 알림이 발송된다`() {
        // GB 환산이 깔끔하도록 정수 GiB 값 사용(4GiB→"4.00", 5GiB→"5.00").
        val event =
            StorageIncreaseRequestedEvent(
                requestId = 1L,
                userId = 42L,
                reason = "용량 부족",
                nickname = "플로리사장",
                storeName = "플로리 꽃집",
                usedBytes = 4L * 1024 * 1024 * 1024,
                quotaBytes = 5L * 1024 * 1024 * 1024,
            )

        listener.handle(event)

        val invocations = Mockito.mockingDetails(notifier).invocations.toList()
        assertThat(invocations).hasSize(1)
        assertThat(invocations[0].arguments[0]).isEqualTo(DiscordChannel.SUPPORT)

        val content = (invocations[0].arguments[1] as DiscordMessage).content
        // 본문에 작성자·used/quota GB·사유가 포함되는지 검증(시각은 동적이라 제외).
        assertThat(content)
            .contains("플로리사장")
            .contains("(userId: 42)")
            .contains("4.00GB")
            .contains("5.00GB")
            .contains("용량 부족")
            .contains("플로리 꽃집")
    }
}
