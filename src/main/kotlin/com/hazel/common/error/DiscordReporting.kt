package com.hazel.common.error

/**
 * Discord 리포팅 보조 함수(순수). 내부 디테일 노출 방지를 위한 새니타이즈/잘라내기.
 */
internal const val MAX_FIELD_LENGTH = 256
internal const val MAX_STACK_LENGTH = 1000
internal const val MAX_STACK_LINES = 20
private const val ELLIPSIS_RESERVE = 3

/** 스택에서 경로/이메일/토큰/비밀번호/키를 마스킹하고 줄 수를 제한한다. */
internal fun sanitizeStack(stack: String): String =
    stack
        .replace(Regex("/Users/[^/]+"), "/home/user")
        .replace(Regex("[A-Za-z0-9_.-]+@[A-Za-z0-9.-]+"), "[EMAIL]")
        .replace(Regex("(?i)token[:=]\\s*['\"]?[^\\s'\"]+"), "token=[REDACTED]")
        .replace(Regex("(?i)password[:=]\\s*['\"]?[^\\s'\"]+"), "password=[REDACTED]")
        .replace(Regex("(?i)key[:=]\\s*['\"]?[^\\s'\"]{20,}"), "key=[REDACTED]")
        .lineSequence()
        .take(MAX_STACK_LINES)
        .joinToString("\n")

internal fun truncate(
    value: String,
    max: Int,
): String = if (value.length > max) value.take(max - ELLIPSIS_RESERVE) + "..." else value
