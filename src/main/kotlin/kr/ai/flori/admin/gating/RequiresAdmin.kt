package kr.ai.flori.admin.gating

/**
 * 운영자 전용 엔드포인트 표시. 컨트롤러 메서드/클래스에 붙이면 [AdminInterceptor]가
 * 진입 전 User.isAdmin 을 강제하고, 아니면 403.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequiresAdmin
