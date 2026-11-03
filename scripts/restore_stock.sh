#!/usr/bin/env bash
# restore_stock.sh — Rollback completo do debloat_level2.sh.
# Reinstala para o usuário 0 os pacotes de sistema removidos, reativa a Play Store
# e restaura os settings originais (ou os padrões de fábrica).
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOG="$ROOT/logs/execution.log"
SETTINGS_BACKUP="$ROOT/logs/settings_before.txt"
mkdir -p "$ROOT/logs"

log() { printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*" | tee -a "$LOG"; }
run() {
  log "\$ $*"
  local out rc
  out="$("$@" 2>&1 | tr -d '\r')"; rc=$?
  [[ -n "$out" ]] && printf '%s\n' "$out" | sed 's/^/    /' | tee -a "$LOG"
  log "  -> exit $rc"
  return $rc
}

[[ "$(adb get-state 2>/dev/null)" == "device" ]] || { log "ERRO: dispositivo não conectado."; exit 1; }
log "=== RESTORE STOCK ==="

# Inverso exato de cada pm uninstall -k --user 0 do debloat_level2.sh
for p in \
  com.mobvoi.wear.system.aw \
  com.mobvoi.wear.fitness.aw \
  com.mobvoi.wear.health.aw \
  com.mobvoi.wear.account.aw \
  com.mobvoi.wear.appsservice \
  com.mobvoi.wear.privacy.aw \
  com.mobvoi.wear.watchface.aw \
  com.mobvoi.companion.aw \
  com.mobvoi.ticwear.sidewearvoicesearch \
  com.mobvoi.wear.stepcounter \
  com.mobvoi.wear.heartrate.aw \
  com.mobvoi.wear.sleep.aw \
  com.google.android.googlequicksearchbox
do
  run adb shell cmd package install-existing "$p" || true
done

# Play Store
run adb shell pm enable --user 0 com.android.vending

# Settings: valores originais salvos pelo debloat; senão, padrões de fábrica.
get_saved() { [[ -f "$SETTINGS_BACKUP" ]] && sed -n "s/^$1=//p" "$SETTINGS_BACKUP"; }
restore_setting() {
  local k="$1" def="$2" v
  v="$(get_saved "$k")"
  if [[ -z "$v" || "$v" == "null" ]]; then v="$def"; fi
  run adb shell settings put global "$k" "$v"
}
restore_setting window_animation_scale     1.0
restore_setting transition_animation_scale 1.0
restore_setting animator_duration_scale    1.0
restore_setting wifi_scan_always_enabled   1
restore_setting ble_scan_always_enabled    1

# OTA & Assistant Rollback
run adb shell settings put global ota_disable_automatic_update 0
run adb shell settings delete secure assistant || true
run adb shell settings delete secure voice_interaction_service || true

log "Restore concluído. Recomenda-se: adb reboot"
