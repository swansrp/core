#!/bin/bash
set -e

# 定义远程仓库
REMOTE_ORIGIN="origin"
REMOTE_GITEE="gitee"

# 获取要同步的分支
if [ $# -eq 0 ]; then
  BRANCHES=("master")
else
  BRANCHES=("$@")
fi

echo "开始同步分支: ${BRANCHES[*]}"
echo "============================="

# 记录是否有 stash
STASHED=false

# 如果有本地改动，先 stash
if [[ -n "$(git status --porcelain)" ]]; then
  echo "检测到本地未提交更改，执行 git stash..."
  git stash push -m "auto-stash-before-sync"
  STASHED=true
fi

# 循环同步每个分支
for BRANCH in "${BRANCHES[@]}"; do
  echo ""
  echo "🔁 同步分支: $BRANCH"
  echo "-----------------------------"

  # 确保本地有该分支
  if git show-ref --verify --quiet "refs/heads/$BRANCH"; then
    git checkout "$BRANCH"
  else
    echo "本地无 $BRANCH 分支，尝试从 $REMOTE_ORIGIN 拉取..."
    git fetch "$REMOTE_ORIGIN" "$BRANCH":"$BRANCH" || {
      echo "❌ 从 $REMOTE_ORIGIN 拉取 $BRANCH 失败，跳过..."
      continue
    }
    git checkout "$BRANCH"
  fi

  # 更新 origin -> 本地
  echo "⬇️ 从 $REMOTE_ORIGIN 拉取最新代码..."
  git fetch "$REMOTE_ORIGIN" "$BRANCH"
  git rebase "$REMOTE_ORIGIN/$BRANCH" || git rebase --abort

  # 推送到 gitee
  echo "⬆️ 推送到 $REMOTE_GITEE..."
  if ! git push "$REMOTE_GITEE" "$BRANCH"; then
    echo "⚠️ 推送被拒绝，尝试拉取后重推..."
    git pull "$REMOTE_GITEE" "$BRANCH" --rebase || true
    git push "$REMOTE_GITEE" "$BRANCH" || echo "⚠️ Gitee 推送失败（可能分支冲突）"
  fi

  # 再同步 Gitee -> Origin
  echo "⬇️ 从 $REMOTE_GITEE 拉取最新代码..."
  git fetch "$REMOTE_GITEE" "$BRANCH"
  git rebase "$REMOTE_GITEE/$BRANCH" || git rebase --abort

  echo "⬆️ 推送到 $REMOTE_ORIGIN..."
  if ! git push "$REMOTE_ORIGIN" "$BRANCH"; then
    echo "⚠️ 推送被拒绝，尝试拉取后重推..."
    git pull "$REMOTE_ORIGIN" "$BRANCH" --rebase || true
    git push "$REMOTE_ORIGIN" "$BRANCH" || echo "⚠️ Origin 推送失败（可能分支冲突）"
  fi

  echo "✅ 分支 $BRANCH 同步完成"
done

# 恢复 stash
if [ "$STASHED" = true ]; then
  echo ""
  echo "恢复本地改动..."
  git stash pop || echo "⚠️ 恢复 stash 可能有冲突，请手动处理"
fi

echo ""
echo "🎉 所有分支同步完成！"
