#!/usr/bin/env bash
# gitflow.sh - add, commit, push, dan jalankan workflow GitHub Actions dari terminal Ubuntu.
# Jalankan dari dalam folder repo YTMaterial.
set -euo pipefail

REPO="${REPO:-muhammadwaridfarhan-max/YTMaterial}"
BRANCH="${BRANCH:-main}"
WORKFLOW="${WORKFLOW:-build.yml}"   # nama file workflow (Android CI)

c_ok()  { printf '\033[32m%s\033[0m\n' "$*"; }
c_err() { printf '\033[31m%s\033[0m\n' "$*" >&2; }
need()  { command -v "$1" >/dev/null 2>&1 || { c_err "'$1' belum terpasang. Jalankan: ./gitflow.sh setup"; exit 1; }; }
in_repo() { git rev-parse --is-inside-work-tree >/dev/null 2>&1 || { c_err "Bukan folder git. cd ke folder repo dulu."; exit 1; }; }

usage() {
cat <<EOF
Pemakaian: ./gitflow.sh <perintah> [argumen]

  setup               pasang git + gh, atur nama/email git, set remote
  login               login GitHub (gh auth login)
  status              status git + 5 run CI terakhir
  save "pesan"        add -A + commit + pull --rebase + push ke $BRANCH
  push                push commit yang sudah ada ke $BRANCH
  ci                  jalankan workflow Android CI di $BRANCH lalu pantau sampai selesai
  watch               pantau run CI terbaru
  log                 tampilkan log langkah yang gagal dari run terakhir
  apk                 unduh artifact debug-apk dari run terakhir ke ./apk-out
  all "pesan"         save + ci + (kalau gagal) tampilkan log error

Variabel opsional: REPO=$REPO BRANCH=$BRANCH WORKFLOW=$WORKFLOW
EOF
}

cmd_setup() {
  local SUDO=""
  [ "$(id -u)" -ne 0 ] && command -v sudo >/dev/null 2>&1 && SUDO="sudo"
  $SUDO apt-get update -y
  $SUDO apt-get install -y git curl ca-certificates
  if ! command -v gh >/dev/null 2>&1; then
    $SUDO apt-get install -y gh || {
      c_err "Paket gh tidak ada di apt. Lihat https://github.com/cli/cli/blob/trunk/docs/install_linux.md"
      exit 1
    }
  fi
  if [ -z "$(git config --global user.name || true)" ]; then
    read -rp "Nama untuk commit: " n && git config --global user.name "$n"
  fi
  if [ -z "$(git config --global user.email || true)" ]; then
    read -rp "Email untuk commit: " e && git config --global user.email "$e"
  fi
  git config --global init.defaultBranch main
  git config --global pull.rebase true
  if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    if git remote get-url origin >/dev/null 2>&1; then
      git remote set-url origin "https://github.com/$REPO.git"
    else
      git remote add origin "https://github.com/$REPO.git"
    fi
    git checkout -B "$BRANCH" 2>/dev/null || true
    c_ok "Remote origin -> https://github.com/$REPO.git"
  else
    c_ok "Selesai. Masuk ke folder repo lalu jalankan ./gitflow.sh setup lagi untuk set remote."
  fi
  c_ok "Setup selesai. Lanjut: ./gitflow.sh login"
}

cmd_login() {
  need gh
  gh auth login -h github.com -p https -w
  gh auth setup-git
  c_ok "Login berhasil."
}

cmd_status() {
  need git; in_repo
  git status -sb
  if command -v gh >/dev/null 2>&1; then
    echo; gh run list -R "$REPO" -L 5 || true
  fi
}

cmd_push() {
  need git; in_repo
  git pull --rebase origin "$BRANCH" || { c_err "Pull --rebase bentrok. Selesaikan konflik lalu: git rebase --continue"; exit 1; }
  git push origin "HEAD:$BRANCH"
  c_ok "Push ke $BRANCH selesai."
}

cmd_save() {
  need git; in_repo
  local msg="${1:-}"
  [ -z "$msg" ] && { c_err 'Beri pesan commit: ./gitflow.sh save "pesan"'; exit 1; }
  git add -A
  if git diff --cached --quiet; then
    c_ok "Tidak ada perubahan untuk di-commit."
  else
    git commit -m "$msg"
  fi
  cmd_push
}

latest_run_id() {
  gh run list -R "$REPO" -w "$WORKFLOW" -b "$BRANCH" -L 1 --json databaseId -q '.[0].databaseId'
}

cmd_watch() {
  need gh
  local id; id="$(latest_run_id)"
  [ -z "$id" ] && { c_err "Belum ada run."; exit 1; }
  gh run watch "$id" -R "$REPO" --exit-status
}

cmd_ci() {
  need gh
  gh workflow run "$WORKFLOW" -R "$REPO" --ref "$BRANCH"
  c_ok "Workflow dijalankan di $BRANCH. Menunggu run muncul..."
  sleep 6
  if cmd_watch; then
    c_ok "Build BERHASIL. Unduh APK: ./gitflow.sh apk"
  else
    c_err "Build GAGAL. Lihat error: ./gitflow.sh log"
    return 1
  fi
}

cmd_log() {
  need gh
  local id; id="$(latest_run_id)"
  gh run view "$id" -R "$REPO" --log-failed | grep -E "e: |error:|FAILED|What went wrong|Unresolved" | head -60 \
    || gh run view "$id" -R "$REPO" --log-failed | tail -80
}

cmd_apk() {
  need gh
  local id; id="$(latest_run_id)"
  rm -rf apk-out && mkdir -p apk-out
  gh run download "$id" -R "$REPO" -n debug-apk -D apk-out
  c_ok "APK ada di: $(pwd)/apk-out"
  ls -lh apk-out
}

cmd_all() {
  cmd_save "${1:-}"
  cmd_ci || cmd_log
}

case "${1:-}" in
  setup)  cmd_setup ;;
  login)  cmd_login ;;
  status) cmd_status ;;
  save)   shift; cmd_save "${1:-}" ;;
  push)   cmd_push ;;
  ci)     cmd_ci ;;
  watch)  cmd_watch ;;
  log)    cmd_log ;;
  apk)    cmd_apk ;;
  all)    shift; cmd_all "${1:-}" ;;
  *)      usage ;;
esac
