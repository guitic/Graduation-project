#!/bin/sh
# 校验两份 schema 是否同源（红线 R8）。
#
# 背景：resources/db/schema.sql 与 sql/schema.sql 曾不同步，导致启动报
# "Column action_type not found"，排查时错误现场离根因很远。
# 在 CI 里守住这一条，比事后靠人记得 cp 更可靠。
#
# 兼容性约束：必须能在 POSIX sh（dash）下跑，因此
#   - 不用 bash 专有的进程替换 <(...)
#   - 不用 mktemp（Windows Git Bash 下 /tmp 会映射成盘符路径）
# 临时文件放在仓库根目录，用相对路径创建与删除。

set -e

A="sql/schema.sql"
B="backend/src/main/resources/db/schema.sql"
TMP_A=".schema_check_a.tmp"
TMP_B=".schema_check_b.tmp"

cleanup() {
  rm -f "./$TMP_A" "./$TMP_B"
}
trap cleanup EXIT

if [ ! -f "$A" ] || [ ! -f "$B" ]; then
  echo "✗ 缺少 schema 文件：$A 或 $B"
  exit 1
fi

# 归一化：去掉 CRLF 的行尾 CR、行尾空白、空行，只比较有意义的语句
tr -d '\r' < "$A" | sed 's/[[:space:]]*$//' | sed '/^$/d' > "./$TMP_A"
tr -d '\r' < "$B" | sed 's/[[:space:]]*$//' | sed '/^$/d' > "./$TMP_B"

if ! cmp -s "./$TMP_A" "./$TMP_B"; then
  echo "✗ 两份 schema 不一致，请统一后再提交："
  echo "  A = $A"
  echo "  B = $B"
  echo ""
  diff -u "./$TMP_A" "./$TMP_B" | head -40 || true
  exit 1
fi

echo "✓ schema 一致（$A == $B）"
