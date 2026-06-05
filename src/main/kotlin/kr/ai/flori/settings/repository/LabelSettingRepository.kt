package kr.ai.flori.settings.repository

import kr.ai.flori.settings.entity.LabelSetting
import org.springframework.data.jpa.repository.JpaRepository

interface LabelSettingRepository : JpaRepository<LabelSetting, Long> {
    fun findByUserIdAndDomainAndKindOrderBySortOrderAsc(
        userId: Long,
        domain: String,
        kind: String,
    ): List<LabelSetting>

    fun findByIdAndUserIdAndDomainAndKind(
        id: Long,
        userId: Long,
        domain: String,
        kind: String,
    ): LabelSetting?
}
