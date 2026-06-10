# 이 repo의 Kotlin/Spring 관용구 (입문자용)

Kotlin이 처음이라면 이 문서를 읽고 코드를 보면 된다. **이 repo가 실제로 쓰는 문법만** 골라, 실제 코드 조각과 함께 설명한다. "왜 이렇게 쓰는가"까지 적었다.

> 큰 그림(레이어·테넌시·DTO 등 *설계* 패턴)은 [PATTERNS.md](PATTERNS.md). 이 문서는 *문법* 레벨이다.

## 목차

- [기본기](#기본기)
  - [① `val` / `var`](#-val--var-불변--가변)
  - [② Null 안전성 (`?`, `?:`, `?.let`, `requireNotNull`)](#-null-안전성--repo의-핵심-습관)
  - [③ 타입은 이름 뒤에, 세미콜론 없음](#-타입은-뒤에-세미콜론-없음)
  - [④ 문자열 템플릿 `$`](#-문자열-템플릿)
- [함수와 클래스](#함수와-클래스)
  - [⑤ 표현식 본문 함수 `fun f() = ...`](#-표현식-본문-함수)
  - [⑥ 생성자 주입 (Spring DI)](#-생성자-주입--spring-의존성-주입)
  - [⑦ `data class` (DTO)](#-data-class--dto에-쓰는-이유)
  - [⑧ 이름 붙은 인자 · 기본값 인자](#-이름-붙은-인자--기본값-인자)
  - [⑨ `companion object` (= static)](#-companion-object--static-대용)
  - [⑩ `object` (싱글톤)](#-object--싱글톤)
- [표현식](#표현식)
  - [⑪ `when` (= 강력한 switch)](#-when--강력한-switch)
  - [⑫ 스코프 함수 `let` / `apply` / `also`](#-스코프-함수-let--apply--also)
  - [⑬ 컬렉션 함수형 (`map`/`filter`/`sumOf`…)](#-컬렉션-함수형)
  - [⑭ 확장 함수](#-확장-함수)
  - [⑮ 메서드 참조 `::from`](#-메서드-참조-from)
- [⚠️ Spring + Kotlin 함정 (꼭 읽기)](#️-spring--kotlin-함정-꼭-읽기)

---

## 기본기

### ① `val` / `var` (불변 / 가변)

- `val` = 재할당 불가(자바 `final`, JS `const`). **기본은 항상 `val`.**
- `var` = 재할당 가능. 꼭 바뀌어야 할 때만.

```kotlin
val userId = TenantContext.currentUserId()   // 다시 대입 안 함 → val
var reminderSent: Boolean = false            // 엔티티 필드는 갱신되므로 var
```

### ② Null 안전성 — repo의 핵심 습관

Kotlin은 타입에 `?`가 붙어야 null이 될 수 있다. `String`은 null 불가, `String?`은 null 가능. 컴파일러가 강제한다.

| 연산자 | 의미 | repo 예시 |
|---|---|---|
| `?.` | null이면 건너뜀(안전 호출) | `header?.startsWith(...)` |
| `?:` | "엘비스" — 왼쪽이 null이면 오른쪽 | `holder.get() ?: throw AppException(...)` |
| `?.let { }` | null이 아닐 때만 블록 실행 | `request.customerId?.let { verify(it) }` |
| `requireNotNull(x)` | null이면 예외, 아니면 non-null로 좁힘 | `requireNotNull(request.amount)` |
| `!!` | "null 아님을 단언" (위험) | repo에선 **거의 안 씀** — `requireNotNull` 선호 |

repo에서 가장 자주 보는 두 패턴:

```kotlin
// (a) 없으면 404 — 엘비스 + throw
private fun load(id: Long): Sale =
    saleRepository.findByIdAndUserId(id, TenantContext.currentUserId())
        ?: throw AppException(ErrorCode.NOT_FOUND)

// (b) 선택 필드가 있을 때만 실행 — ?.let
request.customerId?.let { verifyCustomerOwnership(userId, it) }
//                          ↑ it = customerId (null 아님이 보장된 값)
```

> **DTO 요청 필드가 왜 nullable(`val amount: Int?`)인가?** JSON에 키가 빠지면 Kotlin이 그냥 0이 아니라 null로 받아야 "안 보냄"과 "0"을 구분할 수 있다. 그래서 요청은 nullable로 받고, 검증(`@field:NotNull`) 통과 후 서비스에서 `requireNotNull`로 non-null로 좁힌다.

### ③ 타입은 뒤에, 세미콜론 없음

```kotlin
val limit: Int = 100          // 타입은 변수명 뒤 (자바와 반대)
fun get(id: Long): SaleResponse   // 반환 타입도 뒤
```
타입을 추론할 수 있으면 생략 가능(`val limit = 100`). 줄 끝 세미콜론 없음.

### ④ 문자열 템플릿

`"$변수"` 또는 `"${식}"`로 문자열에 값을 끼운다.

```kotlin
"${it.field}: ${it.defaultMessage}"
log.info("고정비 자동생성 완료: date={} inserted={}", today, count)  // 로깅은 SLF4J {} 자리표시자
```

---

## 함수와 클래스

### ⑤ 표현식 본문 함수

함수 몸통이 식 하나면 `{ return ... }` 대신 `= 식`으로 쓴다. repo 전반에서 컨트롤러/짧은 서비스가 이 형태다.

```kotlin
fun get(id: Long): SaleResponse = saleService.get(id)

@Transactional(readOnly = true)
fun get(id: Long): SaleResponse = SaleResponse.from(load(id))
```

본문이 여러 줄이면 평범하게 `{ }` + `return`을 쓴다.

### ⑥ 생성자 주입 = Spring 의존성 주입

클래스 이름 바로 뒤 괄호가 **주 생성자(primary constructor)**다. 여기에 `private val`로 협력자를 선언하면 그게 곧 필드이자 생성자 파라미터다. Spring은 이 생성자로 의존성을 주입한다 — **`@Autowired` 필요 없음**.

```kotlin
@Service
class SaleService(
    private val saleRepository: SaleRepository,       // ← 주입됨 + 필드
    private val customerRepository: CustomerRepository,
)
```
자바의 "필드 선언 + 생성자 + 대입" 3중 보일러플레이트가 한 줄로 끝난다.

### ⑦ `data class` — DTO에 쓰는 이유

`data class`는 `equals`/`hashCode`/`toString`/`copy`를 자동 생성한다. 값(데이터)을 담는 DTO에 딱 맞다.

```kotlin
data class SalesPageResponse(
    val sales: List<SaleResponse>,
    val hasMore: Boolean,
)
```

`copy`는 부분 변경에 유용: `properties.copy(accessTtlSeconds = -1)` (테스트에서 일부만 바꿔 새 객체 생성).

> ⚠️ **JPA 엔티티에는 `data class`를 쓰지 않는다** — 아래 [함정](#️-spring--kotlin-함정-꼭-읽기) 참고.

### ⑧ 이름 붙은 인자 · 기본값 인자

- **기본값**: 파라미터에 `= 기본값`을 주면 호출 시 생략 가능.
- **이름 붙은 인자**: `필드 = 값`으로 호출하면 순서 무관 + 가독성↑.

```kotlin
// 정의: 선택 필드에 기본값
data class SaleUpdateRequest(
    val amount: Int? = null,
    val note: String? = null,
)

// 호출: 이름을 붙여 명확하게 (repo의 from()이 대표적)
SaleResponse(
    id = requireNotNull(sale.id),
    amount = sale.amount,
    fee = sale.fee,
)
```
자바의 "오버로드 + 빌더 패턴"을 이 둘이 대체한다.

### ⑨ `companion object` (= static 대용)

Kotlin엔 `static`이 없다. 클래스에 딸린 정적 멤버는 `companion object`에 둔다.

```kotlin
data class SaleResponse(/* ... */) {
    companion object {
        fun from(sale: Sale): SaleResponse = SaleResponse(/* ... */)  // 정적 팩토리
    }
}
// 호출: SaleResponse.from(sale)
```
상수는 `companion object` 안에서 `const val`:
```kotlin
private companion object {
    const val MIN_LIMIT = 1
    const val MAX_LIMIT = 100
}
```

### ⑩ `object` (싱글톤)

`object`는 **인스턴스가 하나뿐인 싱글톤**을 선언한다. 상태 보관소나 상수 묶음에 쓴다.

```kotlin
object TenantContext {                       // 앱 전체에 단 하나
    private val holder = ThreadLocal<Long?>()
    fun currentUserId(): Long = holder.get() ?: throw AppException(ErrorCode.UNAUTHORIZED)
}
// 호출: TenantContext.currentUserId()  (인스턴스 생성 없이 바로)

object PaymentMethods {                       // 상수 묶음
    const val UNPAID = "unpaid"
    val SALE = setOf("card", "cash", "transfer", "unpaid")
}
```

---

## 표현식

### ⑪ `when` (= 강력한 switch)

`when`은 값을 반환할 수 있고, 범위·타입·조건도 분기한다.

```kotlin
val label = when (status) {
    "pending" -> "예약대기"
    "confirmed" -> "예약확정"
    else -> "기타"
}
```

### ⑫ 스코프 함수 `let` / `apply` / `also`

객체를 받아 블록을 실행하는 함수들. repo가 쓰는 셋만:

| 함수 | 블록 안 객체 | 반환 | 용도 | repo 예 |
|---|---|---|---|---|
| `let` | `it` | 블록 결과 | null 체크 후 변환 | `request.customerId?.let { verify(it) }` |
| `apply` | `this` | **객체 자신** | 객체 설정(빌더처럼) | 엔티티 필드 세팅 |
| `also` | `it` | **객체 자신** | 곁다리(로깅 등) | — |

```kotlin
// let: 토큰이 있을 때만 파싱하고 그 결과로 또 처리
resolveToken(request)?.let { token ->
    tokenProvider.parse(token)?.let { principal ->
        TenantContext.set(principal.userId)
    }
}
```

### ⑬ 컬렉션 함수형

리스트를 루프 없이 변환/집계한다. repo에서 자주 보는 것들:

```kotlin
page.content.map(SaleResponse::from)              // 각 원소 변환 → 리스트
due.filter { it.id !in skipped }                  // 조건 통과만 남김
due.filter { ... }.sumOf { insertExpense(it, date) }   // 변환 후 합계
list.mapNotNull { it.id }                         // 변환 + null 제거
ex.bindingResult.fieldErrors.firstOrNull()        // 첫 원소 또는 null
```
- `it` = 람다의 단일 파라미터 기본 이름.
- `in` / `!in` = 포함 여부(`x in someSet`).

### ⑭ 확장 함수

기존 타입에 메서드를 "추가"한 것처럼 호출한다. 표준 라이브러리 확장을 repo가 활용:

```kotlin
limit.coerceIn(MIN_LIMIT, MAX_LIMIT)   // Int.coerceIn — 범위로 clamp
offset.coerceAtLeast(0)                // 음수 방지
"Bearer ...".startsWith(BEARER_PREFIX) // String.startsWith
```
직접 정의도 가능하다(`fun String.toX() = ...`). 공통 유틸을 만들 때 쓸 수 있다.

### ⑮ 메서드 참조 `::from`

람다 `{ x -> SaleResponse.from(x) }` 대신 `SaleResponse::from`으로 짧게.

```kotlin
page.content.map(SaleResponse::from)
```

---

## ⚠️ Spring + Kotlin 함정 (꼭 읽기)

Kotlin과 Spring을 같이 쓸 때 입문자가 반드시 밟는 지뢰들. repo는 이미 대응돼 있으니 **왜 그런지**만 알아두면 된다.

### (가) 클래스/메서드가 기본 `final` → `kotlin-spring` 플러그인이 자동 `open`

Kotlin은 클래스·메서드가 기본 `final`(상속/오버라이드 불가)이다. 그런데 Spring은 `@Transactional`·AOP를 위해 **프록시(상속)**를 만들어야 한다. 그래서 `kotlin-spring` 플러그인이 `@Service`·`@Component`·`@RestController` 등을 자동으로 `open` 처리한다 → 평소엔 신경 쓸 필요 없음.

**예외(중요)**: 플러그인은 **abstract 베이스 클래스는 열지 않는다.** 추상 클래스에 `@Transactional` 메서드를 두면 프록시가 안 걸린다 → 그 메서드는 명시적으로 `open fun`이어야 한다. 추상 클래스로 공통 서비스 로직을 빼낼 때 이 점을 기억.

### (나) JPA 엔티티는 `data class`로 만들지 않는다

`data class`의 자동 `equals`/`hashCode`는 모든 필드를 쓰는데, JPA의 지연 로딩 프록시·미할당 ID와 충돌해 미묘한 버그를 만든다. 그래서 엔티티는 **일반 `class` + `var` 필드**로 둔다. ([`Sale.kt`](../src/main/kotlin/kr/ai/flori/sales/entity/Sale.kt))

```kotlin
@Entity
@Table(name = "sales")
class Sale(                                       // data class 아님
    @Column(name = "user_id", nullable = false) var userId: Long,  // var (영속 상태 변경)
    /* ... */
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)   // BIGINT 시퀀스(IDENTITY)
    var id: Long? = null                          // 저장 전엔 null → nullable
}
```
- DTO는 `data class`(값), 엔티티는 `class`(영속 객체) — 역할이 다르다.
- `id`가 `Long? = null`인 이유: `save()` 전엔 ID가 없으므로. 그래서 응답 변환 때 `requireNotNull(sale.id)`로 좁힌다.

### (다) `@field:` 의 의미 (애너테이션 적용 위치)

`val date: LocalDate?`는 Kotlin이 내부적으로 필드·게터·생성자 파라미터를 만든다. Bean Validation 애너테이션을 그중 **필드(field)**에 붙이려면 `@field:`를 명시해야 한다.

```kotlin
@field:NotNull(message = "날짜는 필수입니다")
val date: LocalDate?
```
`@field:` 없이 `@NotNull`만 쓰면 엉뚱한 곳에 붙어 검증이 동작하지 않을 수 있다. **요청 DTO 검증엔 항상 `@field:`.**

### (라) `lateinit` — 테스트의 늦은 초기화

생성자에서 못 받고 나중에 주입되는 경우(테스트의 `@Autowired`) `lateinit var`를 쓴다.

```kotlin
@Autowired
lateinit var saleRepository: SaleRepository    // 스프링이 나중에 주입
```
운영 코드는 생성자 주입(⑥)을 쓰므로 `lateinit`은 주로 테스트에서 본다.

---

## 더 볼 거리

- 설계 패턴(레이어·테넌시·DTO 경계·에러): [PATTERNS.md](PATTERNS.md)
- 아키텍처 전체 그림: [ARCHITECTURE.md](ARCHITECTURE.md)
- 공식 문서: [Kotlin 문서](https://kotlinlang.org/docs/home.html) · [Spring Boot + Kotlin](https://docs.spring.io/spring-boot/reference/features/kotlin.html)
