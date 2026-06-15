#!/usr/bin/env bash
# Stop 훅: 이번 턴에 Kotlin을 만졌을 때만 1회 — 포맷 + 컴파일 검증.
# - ktlintFormat: 자동 포맷(턴 끝 1회라 편집 중 재-Read 안 생김)
# - compileTestKotlin: main+test 컴파일 선검증 (gradle BUILD FAILED 조기차단)
# 스크래치를 먼저 비우므로 무한 Stop 루프가 안 생긴다.
# Claude가 고치며 .kt를 다시 편집하면 재기록되어 다시 게이트됨(의도된 동작).
set -uo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "$here/../.." && pwd)"
touched="$root/.claude/.kt-touched"

[ -s "$touched" ] || exit 0   # 이번 턴 .kt 편집 없음 → 조용히 통과
rm -f "$touched"              # 먼저 소비 → 재실행 루프 방지

cd "$root" || exit 0
out="$(./gradlew ktlintFormat compileTestKotlin -q --console=plain 2>&1)"
code=$?

if [ "$code" -ne 0 ]; then
  reason="$(printf '%s\n' "$out" | tail -n 40)"
  jq -n --arg r "$reason" \
    '{decision:"block", reason:("⚠️ ktlintFormat/compile 실패 — 끝내기 전에 고쳐라:\n\n" + $r)}' \
    2>/dev/null || printf '%s\n' "⚠️ ktlintFormat/compile 실패 (jq 없음). ./gradlew ktlintFormat compileTestKotlin 직접 확인." >&2
fi
exit 0
