package kr.ai.flori.admin.domain

import kr.ai.flori.admin.error.AdminErrorCode
import kr.ai.flori.common.error.AppException

/**
 * SPEC-AI-008 프롬프트 모델 화이트리스트(LiteLLM 등록 모델만). 콘솔 CRUD·플레이그라운드 공용.
 * 그 외 모델은 비용 폭발/오용 방어를 위해 거부한다.
 */
object PromptModels {
    val ALLOWED = setOf("claude-haiku-4-5", "claude-sonnet-4-6")

    /** null/blank(=ai-server 기본값 사용)는 허용. 명시된 모델은 화이트리스트만 통과. */
    fun validate(model: String?) {
        if (!model.isNullOrBlank() && model !in ALLOWED) {
            throw AppException(AdminErrorCode.INVALID_PROMPT_MODEL)
        }
    }
}
