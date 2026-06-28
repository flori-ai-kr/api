package kr.ai.flori.insights.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseCreatedEntity
import java.time.LocalDate

/**
 * 화훼 경매 시세(일별 경락가, aT f001 적재). 공유 읽기 테이블 — user_id 격리 대상이 아니다.
 *
 * 실제 aT 화훼유통정보 f001 API 는 시장/법인 구분이 없는 단일 시장(aT 양재) 응답이다.
 * 응답 필드(saleDate/flowerGubn/pumName/goodName/lvNm + 금액/수량)를 그대로 적재한다.
 *
 * 등락률 컬럼이 없다: 평균단가(avg_amt) 시계열을 직전 정산일자와 비교해 응답 DTO에서 파생 계산한다.
 * (FlowerAuctionPriceQueryRepository 가 idx_fap_item_date 로 LAG 윈도.)
 */
@Entity
@Table(name = "flower_auction_prices")
class FlowerAuctionPrice(
    @Column(name = "sale_date", nullable = false)
    var saleDate: LocalDate,
    @Column(name = "flower_gubn", nullable = false)
    var flowerGubn: String,
    @Column(name = "pum_name", nullable = false)
    var pumName: String,
) : BaseCreatedEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "good_name", nullable = false)
    var goodName: String = ""

    @Column(name = "lv_nm", nullable = false)
    var lvNm: String = ""

    @Column(name = "avg_amt")
    var avgAmt: Int? = null

    @Column(name = "max_amt")
    var maxAmt: Int? = null

    @Column(name = "min_amt")
    var minAmt: Int? = null

    @Column(name = "tot_qty")
    var totQty: Long? = null

    @Column(name = "tot_amt")
    var totAmt: Long? = null
}
