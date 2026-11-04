#!/usr/bin/env bash
# gitflow.sh - helper add / commit / push / workflow untuk YTMaterial (Ubuntu terminal)
# Pakai:  ./scripts/gitflow.sh <perintah> [argumen]
set -euo pipefail

REPO_URL="https://github.com/muhammadwaridfarhan-max/YTMaterial.git"
BRANCH="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo main)"

need() { command -v "$1" >/dev/null 2>&1 || { echo "x '$1' belum terpasang. Jalankan: ./scripts/gitflow.sh setup"; exit 1; }; }

usage() {
cat <<USAGE
Perintah:
  setup                   pasang git + gh (GitHub CLI) di Ubuntu & atur remote origin
  login                   login GitHub lewat 'gh auth login'
  status                  ringkas status repo
  save "pesan"            git add -A + commit (+ push ke branch aktif)
  push                    push branch aktif (set upstream otomatis)
  branch <nama>           buat & pindah ke branch baru
  build                   build lokal: ./gradlew assembleDebug
  ci                      picu workflow 'Android CI' (workflow_dispatch) lalu pantau
  watch                   pantau run workflow terakhir
  release <vX.Y.Z>        buat tag, push tag -> workflow 'Release APK' jalan otomatis
USAGE
}

cmd="${1:-help}"; shift || true

case "$cmd" in
  setup)
    sudo apt-get update
    sudo apt-get install -y git curl openjdk-17-jdk
    if ! command -v gh >/dev/null 2>&1; then
      sudo mkdir -p -m 755 /etc/apt/keyrings
      curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg \
        | sudo tee /etc/apt/keyrings/githubcli-archive-keyring.gpg >/dev/null
      sudo chmod go+r /etc/apt/keyrings/githubcli-archive-keyring.gpg
      echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" \
        | sudo tee /etc/apt/sources.list.d/github-cli.list >/dev/null
      sudo apt-get update && sudo apt-get install -y gh
    fi
    [ -d .git ] || git init -b main
    git remote get-url origin >/dev/null 2>&1 || git remote add origin "$REPO_URL"
    echo "Isi identitas git bila belum ada:"
    echo "  git config --global user.name  \"Nama Kamu\""
    echo "  git config --global user.email \"email@kamu.com\""
    echo "Lalu: ./scripts/gitflow.sh login"
    ;;
  login)  need gh; gh auth login; gh auth setup-git ;;
  status) need git; git status -sb; git log --oneline -5 ;;
  save)
    need git
    msg="${1:-}"; [ -n "$msg" ] || { echo "x Beri pesan commit: ./scripts/gitflow.sh save \"pesan\""; exit 1; }
    git add -A
    if git diff --cached --quiet; then echo "Tidak ada perubahan untuk di-commit."; else git commit -m "$msg"; fi
    git push -u origin "$BRANCH"
    ;;
  push)   need git; git push -u origin "$BRANCH" ;;
  branch) need git; [ -n "${1:-}" ] || { echo "x Beri nama branch"; exit 1; }; git switch -c "$1" ;;
  build)  chmod +x gradlew; ./gradlew assembleDebug --stacktrace ;;
  ci)
    need gh
    gh workflow run "Android CI" --ref "$BRANCH"
    sleep 4
    gh run watch "$(gh run list --workflow='Android CI' --limit 1 --json databaseId -q '.[0].databaseId')"
    ;;
  watch)
    need gh
    gh run watch "$(gh run list --limit 1 --json databaseId -q '.[0].databaseId')"
    ;;
  release)
    need git
    tag="${1:-}"; [[ "$tag" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "x Format tag: v1.2.3"; exit 1; }
    git tag -a "$tag" -m "Release $tag"
    git push origin "$tag"
    echo "Tag $tag terkirim -> workflow 'Release APK' berjalan."
    ;;
  help|*) usage ;;
esac
