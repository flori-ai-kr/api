package kr.ai.flori.common.push

/**
 * 푸시 딥링크. 한 목적지를 두 클라이언트가 각자 해석한다.
 * - web(sw.js): [webUrl] 을 그대로 navigate (실제 web 라우트는 모두 `/admin/...`).
 * - mobile(resolveNotificationRoute): [mobileType] + [id] 시맨틱으로 자기 라우트 매핑.
 *
 * 경로를 템플릿마다 손으로 박지 않고 [PushLinks] SSOT 한 곳에서만 만든다(드리프트 방지).
 */
data class PushLink(
    val webUrl: String,
    val mobileType: String? = null,
    val id: String? = null,
) {
    /**
     * FCM data 페이로드. 모바일 resolveNotificationRoute 가 읽는 키(type/id)만 담는다.
     * (web 은 WebPushPayload.url 을 쓰므로 여기 url 은 넣지 않는다.)
     */
    fun toData(): Map<String, String> =
        buildMap {
            mobileType?.let { put("type", it) }
            id?.let { put("id", it) }
        }
}

/**
 * 딥링크 SSOT. web 라우트(`/admin/...`)와 모바일 시맨틱 type 을 한 곳에서 짝지어 정의한다.
 * 모바일 type 어휘는 `mobile/.../push/model/deepLink.ts` resolveNotificationRoute 와 1:1.
 */
object PushLinks {
    fun calendar(): PushLink = PushLink(webUrl = "/admin/calendar", mobileType = "calendar")

    fun community(postId: Long): PushLink = PushLink(webUrl = "/admin/community/$postId", mobileType = "community", id = postId.toString())

    fun insights(): PushLink = PushLink(webUrl = "/admin/insights", mobileType = "insight")

    fun support(inquiryId: Long): PushLink =
        PushLink(webUrl = "/admin/support/$inquiryId", mobileType = "support", id = inquiryId.toString())

    fun home(): PushLink = PushLink(webUrl = "/", mobileType = "dashboard")
}
