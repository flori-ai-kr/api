#!/usr/bin/env bash
# PostToolUse(Edit|Write|MultiEdit): Kotlin 편집 사실만 스크래치에 기록.
# 여기서는 gradle 안 돌리고 파일도 안 건드린다 → 편집은 빠르게, "Read 이후 변경" 누수 0.
# 실제 포맷/컴파일 검증은 턴 종료(Stop)의 kt-gate.sh가 한 번에 처리.
set -uo pipefail

input="$(cat)"
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "$here/../.." && pwd)"   # api/ (gradlew 위치)

fp="$(printf '%s' "$input" | jq -r '.tool_input.file_path // empty' 2>/dev/null || true)"
case "$fp" in
  *.kt|*.kts) printf '%s\n' "$fp" >> "$root/.claude/.kt-touched" ;;
esac
exit 0
