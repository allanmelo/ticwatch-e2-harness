#!/usr/bin/env bash
# audit_device.sh — Diagnóstico do TicWatch E2 e descoberta dinâmica de bloatware.
#
# Uso:
#   scripts/audit_device.sh before   # grava snapshots/*_before.txt
#   scripts/audit_device.sh after    # grava snapshots/*_after.txt
#   scripts/audit_device.sh scan     # só lista pacotes Mobvoi/Ticwear (sem gravar)
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SNAP="$ROOT/snapshots"
LOG="$ROOT/logs/execution.log"
mkdir -p "$SNAP" "$ROOT/logs"

STAGE="${1:-before}"

log() { printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*" | tee -a "$LOG" >&2; }

# Roda um comando adb, registrando comando + retorno no log.
run() {
  log "\$ $*"
  local out rc
  out="$("$@" 2>&1)"; rc=$?
  printf '%s\n' "$out" >>"$LOG"
  log "  -> exit $rc"
  printf '%s\n' "$out"
  return $rc
}

require_device() {
  local state
  state="$(adb get-state 2>/dev/null || true)"
  if [[ "$state" != "device" ]]; then
    log "ERRO: dispositivo ADB não está em 'device' (estado: ${state:-nenhum})."
    log "Se estiver 'unauthorized', aceite a depuração USB na tela do relógio."
    adb devices -l | tee -a "$LOG"
    exit 1
  fi
}

scan_vendor() {
  adb shell pm list packages | tr -d '\r' | sed 's/^package://' \
    | grep -iE 'mobvoi|ticwear|chumenwenwen' | sort
}

require_device

case "$STAGE" in
  scan)
    log "=== Varredura Mobvoi/Ticwear ==="
    scan_vendor | tee -a "$LOG"
    exit 0
    ;;
  before|after) ;;
  *) echo "Uso: $0 before|after|scan"; exit 2 ;;
esac

log "=== AUDITORIA ($STAGE) ==="
run adb devices -l >/dev/null
for p in ro.product.model ro.product.name ro.product.device ro.build.version.release ro.build.fingerprint; do
  printf '%-28s %s\n' "$p" "$(adb shell getprop "$p" | tr -d '\r')" | tee -a "$LOG"
done

run adb shell pm list packages -f | tr -d '\r' | sort >"$SNAP/packages_${STAGE}.txt"
log "Pacotes: $(wc -l <"$SNAP/packages_${STAGE}.txt" | tr -d ' ') -> snapshots/packages_${STAGE}.txt"

{
  echo "# Métricas ($STAGE) — $(date '+%Y-%m-%d %H:%M:%S')"
  echo "## uptime";               adb shell uptime
  echo; echo "## free -m";        adb shell free -m
  echo; echo "## /proc/meminfo";  adb shell cat /proc/meminfo | head -n 15
  echo; echo "## ZRAM";           adb shell cat /proc/swaps
  adb shell 'for f in /sys/block/zram0/mm_stat /sys/block/zram0/disksize; do echo "$f: $(cat $f 2>/dev/null)"; done'
  echo; echo "## top -n 1 -m 15"; adb shell top -n 1 -m 15
  echo; echo "## Pacotes habilitados (-e): $(adb shell pm list packages -e | wc -l)"
  echo "## Pacotes Mobvoi/Ticwear:"; scan_vendor
} 2>&1 | tr -d '\r' >"$SNAP/metrics_${STAGE}.txt"
log "Métricas -> snapshots/metrics_${STAGE}.txt"
