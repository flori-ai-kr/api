package kr.ai.flori.verification.gating

/**
 * 사업자 인증 전용 엔드포인트 표시. 컨트롤러 메서드/클래스에 붙이면
 * [BusinessVerifiedInterceptor]가 진입 전 APPROVED 인증을 강제하고, 없으면 403.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequiresBusinessVerified
