package kr.ai.flori.settings.service

import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.settings.entity.LabelDomains
import kr.ai.flori.settings.entity.LabelKinds
import kr.ai.flori.settings.repository.LabelSettingRepository
import org.springframework.stereotype.Service

/**
 * 매출/지출 라벨 설정(label_settings) 읽기 헬퍼.
 * - 쓰기 경계: requireOwned 로 id가 현재 테넌트의 (domain,kind)에 속하는지 검증(멀티테넌시 HARD).
 * - 읽기 경계: labelMap 으로 id→label 일괄 조회해 응답에서 라벨을 해석.
 */
@Service
class LabelSettingReader(
    private val repository: LabelSettingRepository,
) {
    /** 현재 테넌트의 (domain,kind) 라벨을 id→label 맵으로 반환(목록/통계 응답에서 라벨 해석용). */
    fun labelMap(
        domain: String,
        kind: String,
    ): Map<Long, String> =
        repository
            .findByUserIdAndDomainAndKindOrderBySortOrderAsc(TenantContext.currentUserId(), domain, kind)
            .associate { requireNotNull(it.id) to it.label }

    /** 쓰기 시 id 소유권·종류 검증. 미존재/타 테넌트/종류 불일치면 400. */
    fun requireOwned(
        id: Long,
        domain: String,
        kind: String,
    ): Long {
        repository.findByIdAndUserIdAndDomainAndKind(id, TenantContext.currentUserId(), domain, kind)
            ?: throw AppException(CommonErrorCode.VALIDATION, "유효하지 않은 설정 항목입니다")
        return id
    }

    /** 매출 채널 기본값('other')의 id. 채널 미지정 생성 시 기본 채널로 사용. */
    fun defaultSaleChannelId(): Long? =
        repository
            .findByUserIdAndDomainAndKindAndValue(
                TenantContext.currentUserId(),
                LabelDomains.SALE,
                LabelKinds.CHANNEL,
                DEFAULT_CHANNEL_VALUE,
            )?.id

    private companion object {
        const val DEFAULT_CHANNEL_VALUE = "other"
    }
}
