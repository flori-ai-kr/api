package com.hazel.subscriptions.gating

/**
 * 프리미엄(구독 전용) 엔드포인트 표시. 컨트롤러 메서드나 클래스에 붙이면
 * [SubscriptionAccessInterceptor]가 진입 전 활성 구독을 강제하고, 없으면 403을 응답한다.
 *
 * 선언적 게이팅 — 비즈니스 로직을 건드리지 않고 접근 제어를 표현한다.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequiresSubscription
