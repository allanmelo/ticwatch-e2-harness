#!/usr/bin/env bash
# block_ota.sh — Silencia o OTA do Wear OS (SystemUpdate do GMS) no TicWatch E2.
# Reversão: scripts/restore_stock.sh (seção OTA).
#
# Os nomes de componentes variam por versão do GMS. Neste relógio (GMS 16.0.89)
# NÃO existem SystemUpdateActivity / SystemUpdateService$Receiver; os reais são
# descobertos dinamicamente em com.google.android.gms/.update.*
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOG="$ROOT/logs/execution.log"
STATE="$ROOT/logs/ota_disabled_components.txt"
mkdir -p "$ROOT/logs"

log() { printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*" | tee -a "$LOG"; }
run() {
  log "\$ $*"
  local out rc
  out="$("$@" 2>&1 | tr -d '\r')"; rc=$?
  [[ -n "$out" ]] && printf '%s\n' "$out" | sed 's/^/    /' | tee -a "$LOG"
  log "  -> exit $rc"
  [[ $rc -eq 0 && "$out" != *Error* && "$out" != *Exception* ]]
}

[[ "$(adb get-state 2>/dev/null)" == "device" ]] || { log "ERRO: dispositivo não conectado."; exit 1; }
log "=== BLOCK OTA ==="

# Nomes da especificação (outras versões do GMS) + descoberta dinâmica.
SPEC_COMPONENTS=(
  '.update.SystemUpdateActivity'
  '.update.SystemUpdateService'
  '.update.SystemUpdateGcmTaskService'
  '.update.SystemUpdateService$ActiveReceiver'
  '.update.SystemUpdateService$Receiver'
  '.update.SystemUpdateService$SecretCodeReceiver'
)
DUMP="$(adb shell dumpsys package com.google.android.gms | tr -d '\r')"
FOUND="$(grep -oE 'com\.google\.android\.gms/\.update\.[A-Za-z0-9_$]+' <<<"$DUMP" | sed 's#^com.google.android.gms/##' | sort -u)"

CANDIDATES="$( { printf '%s\n' "${SPEC_COMPONENTS[@]}"; printf '%s\n' "$FOUND"; } | sort -u )"

log "[1/3] Desativando componentes de SystemUpdate do GMS..."
: >"$STATE"
while read -r c; do
  [[ -z "$c" ]] && continue
  comp="com.google.android.gms/$c"
  if ! grep -qF "$c" <<<"$FOUND"; then
    log "skip (não existe neste GMS): $comp"; continue
  fi
  # disable-user não é aceito para componentes em alguns builds; cai para disable.
  if run adb shell pm disable-user --user 0 "$comp" || run adb shell pm disable --user 0 "$comp"; then
    echo "$comp" >>"$STATE"
  else
    log "FALHA: $comp"
  fi
done <<<"$CANDIDATES"

log "[2/3] Bloqueando checagem automática de OTA..."
run adb shell settings put global ota_disable_automatic_update 1

log "[3/3] Limpando notificações residuais de update..."
run adb shell service call notification 1 || true

log "Verificação: ota_disable_automatic_update=$(adb shell settings get global ota_disable_automatic_update | tr -d '\r')"
log "Componentes desativados ($(wc -l <"$STATE" | tr -d ' ')):"
adb shell dumpsys package com.google.android.gms | tr -d '\r' \
  | sed -n '/disabledComponents:/,/enabledComponents:/p' | grep -i '\.update\.' | tee -a "$LOG"
