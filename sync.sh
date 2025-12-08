#!/bin/bash
set -e

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

######################################
# 先同步 tags（双向）
######################################
echo ""
echo "🔁 同步所有 Git Tags..."
echo "----------------------------------"

# 从 origin 拉取 tags
git fetch $REMOTE_ORIGIN --tags

# 推送到 gitee
git push $REMOTE_GITEE --tags || true

# 从 gitee 拉取 tags
git fetch $REMOTE_GITEE --tags

# 推送到 origin（确保双向同步）
git push $REMOTE_ORIGIN --tags || true

echo "✅ Tags 已同步完成"
echo ""

######################################
# 再同步各分支
######################################
for BRANCH in "${BRANCHES[@]}"; do
  echo "🔁 同步分支: $BRANCH"
  echo "-----------------------------"

  # 确保本地有该分支
  if git show-ref --verify --quiet "refs/heads/$BRANCH"; then
    git checkout "$BRANCH"
  else
    echo "本地无 $BRANCH 分支，从 origin 拉取..."
    git fetch "$REMOTE_ORIGIN" "$BRANCH":"$BRANCH" || {
      echo "❌ 无法获取 $BRANCH，跳过..."
      continue
    }
    git checkout "$BRANCH"
  fi

  echo "⬇️ 从 origin 拉取最新分支..."
  git fetch "$REMOTE_ORIGIN" "$BRANCH"
  git rebase "$REMOTE_ORIGIN/$BRANCH" || git rebase --abort

  echo "⬆️ 推送到 gitee..."
  git push "$REMOTE_GITEE" "$BRANCH" || {
    echo "⚠️ 推送失败，尝试 rebase 后重推..."
    git pull "$REMOTE_GITEE" "$BRANCH" --rebase || true
    git push "$REMOTE_GITEE" "$BRANCH" || echo "⚠️ 依然失败"
  }

  echo "⬇️ 从 gitee 拉取最新分支..."
  git fetch "$REMOTE_GITEE" "$BRANCH"
  git rebase "$REMOTE_GITEE/$BRANCH" || git rebase --abort

  echo "⬆️ 推送回 origin..."
  git push "$REMOTE_ORIGIN" "$BRANCH" || {
    echo "⚠️ 推送失败，尝试 rebase 后重推..."
    git pull "$REMOTE_ORIGIN" "$BRANCH" --rebase || true
    git push "$REMOTE_ORIGIN" "$BRANCH" || echo "⚠️ 依然失败"
  }

  echo "✅ 分支 $BRANCH 同步完成"
done

######################################
# 恢复 stash
######################################
if [ "$STASHED" = true ]; then
  echo ""
  echo "恢复本地更改..."
  git stash pop || echo "⚠️ 恢复 stash 有冲突，请手动处理"
fi

echo ""
echo "🎉 所有分支 + Tags 已同步完成！"
