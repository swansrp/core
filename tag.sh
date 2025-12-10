#!/bin/bash
set -e

# ===============================
# 多前缀 TAG 计数器（旗舰增强版）
# 支持新增前缀（模块）
# 支持 prefix-X.Y.Z 的 tag 规范
# ===============================

ALL_TAGS=$(git tag)

echo "=============================="
echo "      多前缀 TAG 管理器"
echo "=============================="
echo ""

PREFIXES=""

if [ -n "$ALL_TAGS" ]; then
  PREFIXES=$(echo "$ALL_TAGS" \
    | sed -E 's/-[0-9]+\.[0-9]+\.[0-9]+$//' \
    | sort | uniq)
fi

i=1
declare -A PREFIX_MAP

if [ -n "$PREFIXES" ]; then
  echo "已发现模块前缀："
  for p in $PREFIXES; do
    echo "  $i) $p"
    PREFIX_MAP[$i]=$p
    i=$((i+1))
  done
fi

echo "  $i) 新增前缀"
ADD_PREFIX_OPTION=$i

echo ""
read -p "请选择模块编号: " choice

# ===============================
# 处理新增前缀
# ===============================
if [ "$choice" == "$ADD_PREFIX_OPTION" ]; then
  echo ""
  read -p "请输入新的前缀（如：core-xxx）: " NEW_PREFIX

  if [[ ! $NEW_PREFIX =~ ^[a-zA-Z0-9._-]+$ ]]; then
    echo "前缀包含非法字符，仅允许字母、数字、.、_、-"
    exit 1
  fi

  PREFIX=$NEW_PREFIX
  LAST_TAG="${PREFIX}-0.0.0"
  echo ""
  echo "新增模块：$PREFIX"
  echo "将从 $LAST_TAG 开始计数。"
else
  PREFIX=${PREFIX_MAP[$choice]}
  if [ -z "$PREFIX" ]; then
    echo "无效选择"
    exit 1
  fi

  # 获取该前缀历史 tag
  MODULE_TAGS=$(echo "$ALL_TAGS" | grep "^${PREFIX}-" || true)

  if [ -z "$MODULE_TAGS" ]; then
    LAST_TAG="${PREFIX}-0.0.0"
  else
    LAST_TAG=$(echo "$MODULE_TAGS" \
      | sed -E "s/${PREFIX}-//" \
      | sort -t. -k1,1n -k2,2n -k3,3n \
      | tail -n 1)
    LAST_TAG="${PREFIX}-${LAST_TAG}"
  fi
fi

echo ""
echo "当前最新 tag：$LAST_TAG"
echo ""

echo "请选择操作："
echo "  1) 新 tag（自动自增）"
echo "  2) 重置当前 tag（删除 → 同名重建）"
read -p "请输入 1 或 2: " op

# ===============================
# 生成新tag
# ===============================
if [ "$op" == "1" ]; then
  NUM=$(echo "$LAST_TAG" | sed -E "s/${PREFIX}-//")
  IFS='.' read -r A B C <<< "$NUM"

  C=$((C+1))
  if [ $C -gt 99 ]; then
    C=0
    B=$((B+1))
  fi
  if [ $B -gt 99 ]; then
    B=0
    A=$((A+1))
  fi

  NEW_TAG="${PREFIX}-${A}.${B}.${C}"

  echo ""
  echo "创建新 tag：$NEW_TAG"
  git tag "$NEW_TAG"
  git push origin "$NEW_TAG"
  echo "完成。"

# ===============================
# 重置当前 tag
# ===============================
elif [ "$op" == "2" ]; then
  echo ""
  echo "重置 tag：$LAST_TAG"

  git tag -d "$LAST_TAG" || true
  git push origin ":refs/tags/$LAST_TAG" || true

  git tag "$LAST_TAG"
  git push origin "$LAST_TAG"

  echo "重置完成：$LAST_TAG"

else
  echo "非法输入"
  exit 1
fi

