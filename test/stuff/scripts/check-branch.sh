#!/bin/bash

BRANCH_NAME=${GITHUB_HEAD_REF:-${GITHUB_REF##*/}}

check_prerequisite_branch() {
  local branch=$1
  local prerequisite=$2
  local error_code=$3

  PULL=$(gh api -H "Accept: application/vnd.github+json" -H "X-GitHub-Api-Version: 2022-11-28" "/repos/${GITHUB_REPOSITORY}/pulls?head=${GITHUB_REPOSITORY_OWNER}:${prerequisite}" || true)
  OPEN=$(jq '. | length' <<< "$PULL")

  if [[ "$OPEN" != "0" && "$GITHUB_REPOSITORY_OWNER" != "praktikum-java" ]]; then
    PULL_URL=$(jq -r '.[0].html_url' <<< "$PULL")
    echo "❗ Merge the $prerequisite branch pull request: ${PULL_URL}"
    echo "❗ Объедините pull request ветки $prerequisite: ${PULL_URL}"
    exit "$error_code"
  fi

  echo "$prerequisite - Merged"
}

check_target_branch() {
  local expected_branch=$1
  if [[ "$GITHUB_BASE_REF" != "$expected_branch" && "$GITHUB_REPOSITORY_OWNER" != "praktikum-java" ]]; then
    echo "❗ Set the pull request to merge branch '$expected_branch'"
    echo "❗ Задайте в Pull request ветку слияния '$expected_branch' (вместо '$GITHUB_BASE_REF')"
    exit 2
  fi
}

case "$BRANCH_NAME" in
  "spring-cloud")
    echo "✅ Spring Cloud - OK"
    check_target_branch "main"
    ;;

  "microservices")
    echo "✅ Microservices - OK"``
    check_prerequisite_branch "microservices" "spring-cloud" 3
    check_target_branch "main"
    ;;

  "recommendations")
    echo "✅ Recommendations - OK"
    check_prerequisite_branch "recommendations" "microservices" 4
    check_target_branch "main"
    ;;

  *)
    echo "❌ Unknown branch: $BRANCH_NAME"
    exit 12
    ;;
esac

echo "✅ Github target '$GITHUB_BASE_REF' - OK"
exit 0