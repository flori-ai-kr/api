package kr.ai.flori.sales.service

import kr.ai.flori.sales.dto.SaleResponse
import kr.ai.flori.sales.entity.Sale
import kr.ai.flori.settings.entity.LabelDomains
import kr.ai.flori.settings.entity.LabelKinds
import kr.ai.flori.settings.service.LabelSettingReader
import org.springframework.stereotype.Component

/**
 * 매출 응답 조립 — 현재 테넌트의 카테고리/결제수단/채널 라벨(id→label)을 해석해 SaleResponse를 만든다.
 * SaleService·SaleUnpaidService가 공유한다.
 */
@Component
class SaleResponseAssembler(
    private val labelReader: LabelSettingReader,
) {
    /** 단건 응답 — 라벨 3종을 조회해 채운다. 목록은 labels()를 한 번만 조회해 toResponse에 재사용할 것. */
    fun single(sale: Sale): SaleResponse = toResponse(sale, labels())

    fun toResponse(
        sale: Sale,
        labels: SaleLabels,
        photos: List<String> = emptyList(),
    ): SaleResponse =
        SaleResponse.from(
            sale,
            sale.categoryId?.let { labels.categories[it] },
            sale.paymentMethodId?.let { labels.payments[it] },
            sale.channelId?.let { labels.channels[it] },
            photos,
        )

    /** 현재 테넌트의 매출 라벨(카테고리·결제수단·채널) id→label 맵 묶음. */
    fun labels(): SaleLabels =
        SaleLabels(
            labelReader.labelMap(LabelDomains.SALE, LabelKinds.CATEGORY),
            labelReader.labelMap(LabelDomains.SALE, LabelKinds.PAYMENT),
            labelReader.labelMap(LabelDomains.SALE, LabelKinds.CHANNEL),
        )

    data class SaleLabels(
        val categories: Map<Long, String>,
        val payments: Map<Long, String>,
        val channels: Map<Long, String>,
    )
}
