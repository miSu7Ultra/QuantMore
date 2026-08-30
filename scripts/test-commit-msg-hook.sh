#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
HOOK="$ROOT_DIR/.githooks/commit-msg"
TEMP_DIR=$(mktemp -d)
trap 'rm -rf "$TEMP_DIR"' EXIT

if [[ ! -x "$HOOK" ]]; then
  echo "FAIL: commit-msg Hook 不存在或不可执行: $HOOK" >&2
  exit 1
fi

assert_accepts() {
  local name=$1
  local message=$2
  local file="$TEMP_DIR/$name.txt"
  printf '%s\n' "$message" > "$file"
  if ! "$HOOK" "$file" >/dev/null 2>&1; then
    echo "FAIL: 应接受提交消息: $name" >&2
    return 1
  fi
}

assert_rejects() {
  local name=$1
  local message=$2
  local file="$TEMP_DIR/$name.txt"
  printf '%s\n' "$message" > "$file"
  if "$HOOK" "$file" >/dev/null 2>&1; then
    echo "FAIL: 应拒绝提交消息: $name" >&2
    return 1
  fi
}

assert_accepts "valid-feature" $'feat: 新增策略模板\n\n- 增加策略模板解析流程\n- 补充解析失败的提示信息'
assert_accepts "valid-scope" $'fix(question): 修复题目导入失败\n\n- 兼容空标签的题目数据'
assert_accepts "generated-merge" "Merge branch 'main'"
assert_accepts "generated-revert" 'Revert "feat: 新增策略模板"'
assert_accepts "generated-fixup" "fixup! feat: 新增策略模板"
assert_accepts "generated-squash" "squash! feat: 新增策略模板"
assert_accepts "generated-amend" "amend! feat: 新增策略模板"

assert_rejects "english-subject" $'feat: add strategy template import\n\n- 增加策略模板流程'
assert_rejects "unsupported-type" $'feature: 增加策略模板\n\n- 增加策略模板流程'
assert_rejects "missing-body" "feat: 增加策略模板"
assert_rejects "missing-blank-line" $'feat: 增加策略模板\n- 增加策略模板解析流程'
assert_rejects "non-bullet-body" $'feat: 增加策略模板\n\n增加策略模板解析流程'
assert_rejects "english-bullet" $'feat: 增加策略模板\n\n- add strategy template import'

echo "PASS: commit-msg Hook 格式校验通过"
