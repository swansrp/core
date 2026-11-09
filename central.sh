#!/usr/bin/env bash
# ============================================================
# central.sh — 本机发布 core 到 Maven Central（Portal 通道）
#
# 用法:
#   ./central.sh                 # 快枪（跳测试，版本自动递增 0.0.1→0.0.2→…）
#   ./central.sh --full          # 全量测试枪（验收/首发推荐，约 12 分钟）
#   ./central.sh 1.2.3           # 手动指定版本
#   ./central.sh --tag           # 成功后把版本 tag push 到 origin
#
# 版本号规则（tag.sh 同款公式）:
#   候选 = 本地 tag central_X.Y.Z ＋ repo1 已发布元数据里的裸版本 X.Y.Z（防撞号，网络查询尽力而为）
#   取最大 → 末位+1 → 满99进十位、十位满99进主位；无候选从 0.0.1 起。
#   号源消耗：构建成功后自动打本地 tag central_<版本>（--tag 再 push）；下次跑自然递增。
#
# 前置（均已一次性就位，脚本会自检并给出提示）:
#   1. ~/.m2/settings.xml 有 <server><id>central</id>（Portal token）
#   2. 本机 gpg 密钥已推 keyserver（BE394BF4699404D61C6D5010B3B51922BEB29743）
#   3. JDK17 工具链（脚本自动寻找，找不到会报错）
#
# 流程: 自检 → gpg 缓存热身（缓存过期时弹一次口令窗）→ mvn clean deploy
#       -Pcentral-dist → 等 Portal 校验过 → 去网页点 Publish（发布即永久）
#
# 纪律：签名密钥/口令/Portal token 全部只在本机，不交 Jenkins、不入库。
# 与内网 Nexus 通道（mvn deploy 不带本档）互不干扰。
# ============================================================
set -euo pipefail
cd "$(dirname "$0")"

GPG_KEY_ID="${GPG_KEY_ID:-BE394BF4699404D61C6D5010B3B51922BEB29743}"

RUN_TESTS=false
DO_TAG=false
STAMP=""
for a in "$@"; do
  case "$a" in
    --full) RUN_TESTS=true ;;
    --tag)  DO_TAG=true ;;
    --*)    echo "未知参数: $a（可用 --full / --tag / <版本戳>）" >&2; exit 2 ;;
    *)      STAMP="$a" ;;
  esac
done
# ---------- 版本号自动递增（tag.sh 公式＋已发布元数据双保险） ----------
next_version() {
  local vers=() v max a b c
  while IFS= read -r v; do [ -n "$v" ] && vers+=("$v"); done < <(
    git tag -l 'central_*' | sed 's/^central_//'
    # 已发布裸版本（7ccff197 那种 train_ 戳不匹配、自动忽略）；查不到就只信本地 tag
    curl -s -m 8 'https://repo1.maven.org/maven2/io/github/swansrp/kernel/maven-metadata.xml' 2>/dev/null \
      | grep -oE '<version>[0-9]+\.[0-9]+\.[0-9]+</version>' | sed -E 's~</?version>~~g'
  )
  if [ ${#vers[@]} -eq 0 ]; then echo '0.0.1'; return; fi
  max=$(printf '%s\n' "${vers[@]}" | sort -V | tail -1)
  IFS=. read -r a b c <<<"$max"
  c=$((c+1)); [ "$c" -gt 99 ] && { c=0; b=$((b+1)); }
  [ "$b" -gt 99 ] && { b=0; a=$((a+1)); }
  echo "$a.$b.$c"
}
STAMP="${STAMP:-$(next_version)}"

# ---------- 自检 ----------
command -v mvn  >/dev/null || { echo "✗ 未找到 mvn" >&2; exit 1; }
command -v gpg  >/dev/null || { echo "✗ 未找到 gpg（Git for Windows 自带）" >&2; exit 1; }
grep -q '<id>central</id>' "$HOME/.m2/settings.xml" 2>/dev/null \
  || { echo "✗ ~/.m2/settings.xml 缺 server id=central（Portal token）" >&2; exit 1; }
gpg --list-secret-keys "$GPG_KEY_ID" >/dev/null 2>&1 \
  || { echo "✗ 本机没有私钥 $GPG_KEY_ID（或 GPG_KEY_ID 环境变量指错了）" >&2; exit 1; }

# ---------- JDK17+ 工具链 ----------
pick_jdk() {
  local dirs=() d
  [ -n "${JAVA_HOME:-}" ] && dirs+=("$JAVA_HOME")
  while IFS= read -r d; do dirs+=("$d"); done < <(
    ls -d "/c/Program Files/Java/"jdk1[7-9]*      2>/dev/null
    ls -d "/c/Program Files/Eclipse Adoptium/"*   2>/dev/null
    ls -d "/c/Program Files/Microsoft/"*jdk-1[7-9]* 2>/dev/null
  )
  for d in "${dirs[@]}"; do
    [ -x "$d/bin/javac" ] || continue
    # javac -version 输出形如「javac 17.0.21」（≠ java -version 的带引号形态）
    major="$("$d/bin/javac" -version 2>&1 | head -1 | awk '{print $2}' | cut -d. -f1)"
    if [ "${major:-0}" -ge 17 ] 2>/dev/null; then
      echo "$d"; return 0
    fi
  done
  return 1
}
JDK17="$(pick_jdk)" || { echo "✗ 找不到 JDK17+（设 JAVA_HOME 或装到 Program Files/Java）" >&2; exit 1; }
export JAVA_HOME="$JDK17"
echo "── 工具链: $JAVA_HOME"

# ---------- gpg 口令热身（agent 缓存命中则静默；过期弹一次窗，此后 2h 免输） ----------
if ! echo ping | gpg --batch --pinentry-mode error --local-user "$GPG_KEY_ID" \
        --clearsign -o /dev/null 2>/dev/null; then
  echo "── gpg-agent 缓存已空：接下来弹一次口令窗，输入签名口令"
  echo ping | gpg --local-user "$GPG_KEY_ID" --clearsign -o /dev/null \
    || { echo "✗ 签名自检失败（口令错/keyserver 无此钥？），不发版" >&2; exit 1; }
fi
echo "── gpg 就绪"

# ---------- 发布 ----------
ARGS=(clean deploy -Pcentral-dist "-DjarVersion=$STAMP")
$RUN_TESTS || ARGS+=(-Dmaven.test.skip=true)
echo "── 发版: mvn ${ARGS[*]}"
echo "── 版本戳: $STAMP"
mvn "${ARGS[@]}"

# ---------- 成功收尾：消耗号源（本地 tag 必打；--tag 才 push） ----------
git tag "central_$STAMP" 2>/dev/null || true
echo "── 已打本地 tag: central_$STAMP（下次自动 $STAMP→递增）"
if $DO_TAG; then
  git push origin "central_$STAMP"
  echo "── tag 已 push: central_$STAMP"
fi
cat <<EOF

✔ 构建+上传+校验全部通过（deployment 状态 validated）。
  最后一下人点： https://central.sonatype.com/publishing/deployments
  找到 $STAMP → Publish。【发布即永久：不可删、不可覆盖】
EOF
