#!/bin/bash
set -euo pipefail

REMOTE_ORIGIN="origin"
REMOTE_GITEE="gitee"
DRY_RUN=false
STASHED=false
INITIAL_BRANCH=""
FAILED_BRANCHES=()
SUCCESS_BRANCHES=()

# ========================================
# 工具函数
# ========================================

usage() {
  cat <<EOF
用法: $0 [选项] [分支...]

选项:
  -n, --dry-run    仅预览操作，不实际执行推送
  -h, --help       显示帮助信息

默认同步分支: master
示例:
  $0                        # 同步 master
  $0 master develop         # 同步多个分支
  $0 -n master              # 预览同步 master
EOF
  exit 0
}

log_info()  { echo -e "\033[32m[INFO]\033[0m $*"; }
log_warn()  { echo -e "\033[33m[WARN]\033[0m $*"; }
log_error() { echo -e "\033[31m[ERROR]\033[0m $*"; }

# 安全推送：dry-run 模式下仅打印命令
safe_push() {
  if [ "$DRY_RUN" = true ]; then
    log_warn "[DRY-RUN] git push $*"
    return 0
  fi
  git push "$@"
}

# 安全 rebase：失败时中止并返回非零
try_rebase() {
  local upstream="$1"
  if git rebase "$upstream"; then
    return 0
  else
    log_error "rebase 到 $upstream 失败，正在中止..."
    git rebase --abort 2>/dev/null || true
    return 1
  fi
}

# 清理函数：恢复 stash、切回原始分支
cleanup() {
  local exit_code=$?
  if [ "$STASHED" = true ]; then
    echo ""
    log_info "恢复本地更改..."
    git stash pop 2>/dev/null || log_warn "恢复 stash 有冲突，请手动处理: git stash pop"
  fi
  if [[ -n "$INITIAL_BRANCH" ]] && git show-ref --verify --quiet "refs/heads/$INITIAL_BRANCH" 2>/dev/null; then
    git checkout "$INITIAL_BRANCH" --quiet 2>/dev/null || true
  fi
  echo ""
  print_summary
  exit $exit_code
}

print_summary() {
  echo "========================================="
  echo "       同步结果汇总"
  echo "========================================="
  if [ ${#SUCCESS_BRANCHES[@]} -gt 0 ]; then
    echo -e "  成功: \033[32m${SUCCESS_BRANCHES[*]}\033[0m"
  fi
  if [ ${#FAILED_BRANCHES[@]} -gt 0 ]; then
    echo -e "  失败: \033[31m${FAILED_BRANCHES[*]}\033[0m"
  fi
  echo "========================================="
}

# ========================================
# 参数解析
# ========================================

PARSE_ARGS=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    -n|--dry-run) DRY_RUN=true; shift ;;
    -h|--help)    usage ;;
    *)            PARSE_ARGS+=("$1"); shift ;;
  esac
done

if [ ${#PARSE_ARGS[@]} -eq 0 ]; then
  BRANCHES=("master")
else
  BRANCHES=("${PARSE_ARGS[@]}")
fi

# ========================================
# 前置检查
# ========================================

# 检查是否在 git 仓库中
if ! git rev-parse --is-inside-work-tree &>/dev/null; then
  log_error "当前目录不是 Git 仓库"
  exit 1
fi

# 检查 remote 是否存在
for remote in "$REMOTE_ORIGIN" "$REMOTE_GITEE"; do
  if ! git remote get-url "$remote" &>/dev/null; then
    log_error "远程仓库 '$remote' 不存在，请先添加: git remote add $remote <url>"
    exit 1
  fi
done

# 记录当前分支并注册 cleanup
INITIAL_BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "")
trap cleanup EXIT

echo "========================================="
echo "  Git 双向同步工具"
echo "  远程: $REMOTE_ORIGIN <-> $REMOTE_GITEE"
if [ "$DRY_RUN" = true ]; then
  echo "  模式: DRY-RUN (预览)"
fi
echo "  分支: ${BRANCHES[*]}"
echo "========================================="
echo ""

# 如果有本地改动，先 stash
if [[ -n "$(git status --porcelain)" ]]; then
  log_info "检测到本地未提交更改，执行 git stash..."
  git stash push -m "auto-stash-before-sync-$(date +%s)"
  STASHED=true
fi

######################################
# 同步 tags（双向）
######################################
log_info "同步 Git Tags..."

# 并行从两个远程拉取 tags
git fetch "$REMOTE_ORIGIN" --tags 2>&1 &
FETCH_PID1=$!
git fetch "$REMOTE_GITEE" --tags 2>&1 &
FETCH_PID2=$!
wait $FETCH_PID1 $FETCH_PID2

log_info "推送 tags -> $REMOTE_GITEE"
safe_push "$REMOTE_GITEE" --tags || log_warn "推送 tags 到 $REMOTE_GITEE 失败"

log_info "推送 tags -> $REMOTE_ORIGIN"
safe_push "$REMOTE_ORIGIN" --tags || log_warn "推送 tags 到 $REMOTE_ORIGIN 失败"

log_info "Tags 同步完成"
echo ""

######################################
# 同步各分支
######################################
for BRANCH in "${BRANCHES[@]}"; do
  log_info "同步分支: $BRANCH"
  echo "-----------------------------------------"

  # 确保本地有该分支
  if ! git show-ref --verify --quiet "refs/heads/$BRANCH"; then
    log_info "本地无 $BRANCH 分支，从 $REMOTE_ORIGIN 拉取..."
    if ! git fetch "$REMOTE_ORIGIN" "$BRANCH":"$BRANCH"; then
      log_error "无法获取 $BRANCH，跳过"
      FAILED_BRANCHES+=("$BRANCH")
      echo ""
      continue
    fi
  fi

  git checkout "$BRANCH" --quiet

  # 并行从两个远程拉取
  git fetch "$REMOTE_ORIGIN" "$BRANCH" 2>&1 &
  FETCH_PID1=$!
  git fetch "$REMOTE_GITEE" "$BRANCH" 2>&1 &
  FETCH_PID2=$!
  wait $FETCH_PID1 $FETCH_PID2

  # ---- origin -> gitee ----
  log_info "rebase 到 $REMOTE_ORIGIN/$BRANCH"
  if ! try_rebase "$REMOTE_ORIGIN/$BRANCH"; then
    log_error "rebase $REMOTE_ORIGIN/$BRANCH 失败，跳过分支 $BRANCH"
    FAILED_BRANCHES+=("$BRANCH")
    echo ""
    continue
  fi

  log_info "推送 $BRANCH -> $REMOTE_GITEE"
  if ! safe_push "$REMOTE_GITEE" "$BRANCH"; then
    log_warn "推送失败，尝试 pull --rebase 后重推..."
    if [ "$DRY_RUN" != true ]; then
      git pull "$REMOTE_GITEE" "$BRANCH" --rebase 2>/dev/null || true
      safe_push "$REMOTE_GITEE" "$BRANCH" || {
        log_error "推送到 $REMOTE_GITEE 失败，跳过反向同步"
        FAILED_BRANCHES+=("$BRANCH")
        echo ""
        continue
      }
    fi
  fi

  # ---- gitee -> origin ----
  log_info "rebase 到 $REMOTE_GITEE/$BRANCH"
  if ! try_rebase "$REMOTE_GITEE/$BRANCH"; then
    log_error "rebase $REMOTE_GITEE/$BRANCH 失败，跳过推送到 $REMOTE_ORIGIN"
    FAILED_BRANCHES+=("$BRANCH")
    echo ""
    continue
  fi

  log_info "推送 $BRANCH -> $REMOTE_ORIGIN"
  if ! safe_push "$REMOTE_ORIGIN" "$BRANCH"; then
    log_warn "推送失败，尝试 pull --rebase 后重推..."
    if [ "$DRY_RUN" != true ]; then
      git pull "$REMOTE_ORIGIN" "$BRANCH" --rebase 2>/dev/null || true
      safe_push "$REMOTE_ORIGIN" "$BRANCH" || {
        log_error "推送到 $REMOTE_ORIGIN 最终失败"
        FAILED_BRANCHES+=("$BRANCH")
        echo ""
        continue
      }
    fi
  fi

  SUCCESS_BRANCHES+=("$BRANCH")
  log_info "分支 $BRANCH 同步完成"
  echo ""
done
