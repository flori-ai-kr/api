package kr.ai.flori.common.error

/**
 * Discord 리포팅 보조 함수(순수). 내부 디테일 노출 방지를 위한 새니타이즈/잘라내기.
 */
internal const val MAX_FIELD_LENGTH = 256
internal const val MAX_STACK_LENGTH = 1000
internal const val MAX_STACK_LINES = 20
private const val ELLIPSIS_RESERVE = 3

/** 경로/이메일/전화번호/토큰/비밀번호/키 등 PII·시크릿을 마스킹한다(공통). */
private fun maskSensitive(text: String): String =
    text
        .replace(Regex("/Users/[^/]+"), "/home/user")
        .replace(Regex("[A-Za-z0-9_.-]+@[A-Za-z0-9.-]+"), "[EMAIL]")
        .replace(Regex("\\d{2,3}-\\d{3,4}-\\d{4}"), "[PHONE]")
        .replace(Regex("(?i)token[:=]\\s*['\"]?[^\\s'\"]+"), "token=[REDACTED]")
        .replace(Regex("(?i)password[:=]\\s*['\"]?[^\\s'\"]+"), "password=[REDACTED]")
        .replace(Regex("(?i)key[:=]\\s*['\"]?[^\\s'\"]{20,}"), "key=[REDACTED]")

/** 스택에서 PII·시크릿을 마스킹하고 줄 수를 제한한다. */
internal fun sanitizeStack(stack: String): String =
    maskSensitive(stack)
        .lineSequence()
        .take(MAX_STACK_LINES)
        .joinToString("\n")

/** 메시지/액션 등 단문 필드의 PII·시크릿을 마스킹한다(줄 수 제한 없음). */
internal fun sanitizeMessage(text: String): String = maskSensitive(text)

internal fun truncate(
    value: String,
    max: Int,
): String = if (value.length > max) value.take(max - ELLIPSIS_RESERVE) + "..." else value
