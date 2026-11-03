#!/usr/bin/env bash
# debloat_level2.sh — Remoção cirúrgica "Nível 2" do TicWatch E2 (tunny).
#
# - pm uninstall -k --user 0  : toda a suíte Mobvoi + Google Assistant/Search
# - pm disable-user --user 0  : Google Play Store
# - settings                  : sem scan Wi-Fi/BLE em background, sem animações
# - pm trim-caches            : limpa caches
#
# Uso: scripts/debloat_level2.sh [--dry-run]
# Rollback: scripts/restore_stock.sh
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOG="$ROOT/logs/execution.log"
SETTINGS_BACKUP="$ROOT/logs/settings_before.txt"
mkdir -p "$ROOT/logs"

DRY_RUN=0
[[ "${1:-}" == "--dry-run" ]] && DRY_RUN=1

# ---------------------------------------------------------------------------
# Alvos
# ---------------------------------------------------------------------------
# Lista base (confirmada pela varredura de 2026-11-02 no build PWDR.190618.001.B8).
UNINSTALL_TARGETS=(
  com.mobvoi.wear.system.aw          # TicSystem — principal causa de lag
  com.mobvoi.wear.fitness.aw         # TicExercise
  com.mobvoi.wear.health.aw          # TicHealth
  com.mobvoi.wear.account.aw         # Mobvoi Account
  com.mobvoi.wear.appsservice        # Mobvoi App Service
  com.mobvoi.wear.privacy.aw         # Mobvoi Privacy / telemetria
  com.mobvoi.wear.watchface.aw       # Watchfaces Mobvoi
  com.mobvoi.companion.aw            # TicCompanion (msg proxy + clima Mobvoi)
  # Presentes em outros builds; ignorados se não existirem:
  com.mobvoi.ticwear.sidewearvoicesearch
  com.mobvoi.wear.stepcounter
  com.mobvoi.wear.heartrate.aw
  com.mobvoi.wear.sleep.aw
  # Google
  com.google.android.googlequicksearchbox   # Assistant / hotword (~60-80 MB)
)

DISABLE_TARGETS=(
  com.android.vending                # Play Store (dex2oat em background)
)

# Pacotes Mobvoi que a varredura encontra mas que NÃO devem ser removidos.
#  - cmodel.e2: RRO (overlay sem código) sobre o app de Configurações;
#    não roda processo, não libera RAM e carrega dados de modelo/regulatórios.
VENDOR_EXCLUDE=(
  com.mobvoi.ticwatch.cmodel.e2
)
# Qualquer pacote Mobvoi novo com esses termos exige avaliação manual
# (display ambiente / sensor hub / overlays).
VENDOR_RISKY_REGEX='mcu|cmodel|overlay|sensorhub|display'

# ---------------------------------------------------------------------------
# Trava de segurança (whitelist)
# ---------------------------------------------------------------------------
PROTECTED_EXACT=(
  com.google.android.gms
  com.google.android.gsf
  com.google.android.wearable.app
  com.google.android.apps.fitness
  com.google.android.apps.wearable.bluetooth
  com.google.android.WearableBluetooth
  com.google.android.apps.wearable.systemui
  com.google.android.apps.wearable.settings
  com.google.android.wearable.setupwizard
  android
)

is_protected() {
  local p="$1"
  [[ "$p" == "com.android.vending" ]] && return 1   # única exceção permitida (só disable)
  local x
  for x in "${PROTECTED_EXACT[@]}"; do [[ "$p" == "$x" ]] && return 0; done
  case "$p" in
    com.android.*|android.*|com.qualcomm.*|com.qti.*|org.codeaurora.*) return 0 ;;
    *[Bb]luetooth*|*overlay*) return 0 ;;
  esac
  return 1
}

# ---------------------------------------------------------------------------
log() { printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*" | tee -a "$LOG"; }

run() {
  if (( DRY_RUN )); then log "[dry-run] \$ $*"; return 0; fi
  log "\$ $*"
  local out rc
  out="$("$@" 2>&1 | tr -d '\r')"; rc=$?
  [[ -n "$out" ]] && printf '%s\n' "$out" | sed 's/^/    /' | tee -a "$LOG"
  log "  -> exit $rc"
  return $rc
}

installed() { grep -qx "$1" <<<"$INSTALLED"; }

# ---------------------------------------------------------------------------
state="$(adb get-state 2>/dev/null || true)"
if [[ "$state" != "device" ]]; then
  log "ERRO: dispositivo não está em 'device' (estado: ${state:-nenhum}). Abortando."
  exit 1
fi

log "=== DEBLOAT NÍVEL 2 $( ((DRY_RUN)) && echo '(DRY-RUN)') ==="
INSTALLED="$(adb shell pm list packages | tr -d '\r' | sed 's/^package://')"

# Descoberta dinâmica: inclui Mobvoi/Ticwear novos que não estão na lista base.
while read -r p; do
  [[ -z "$p" ]] && continue
  skip=0
  for x in "${UNINSTALL_TARGETS[@]}" "${VENDOR_EXCLUDE[@]}"; do [[ "$p" == "$x" ]] && skip=1; done
  (( skip )) && continue
  if [[ "$p" =~ $VENDOR_RISKY_REGEX ]]; then
    log "AVISO: $p encontrado mas exige avaliação manual — NÃO removido."
    continue
  fi
  log "Descoberta dinâmica: $p adicionado à remoção."
  UNINSTALL_TARGETS+=("$p")
done < <(grep -iE 'mobvoi|ticwear|chumenwenwen' <<<"$INSTALLED")

# Valida a whitelist ANTES de qualquer alteração.
for p in "${UNINSTALL_TARGETS[@]}" "${DISABLE_TARGETS[@]}"; do
  if is_protected "$p"; then
    log "ERRO FATAL: '$p' é protegido pela whitelist. Abortando sem alterações."
    exit 3
  fi
done

# Backup dos settings que serão alterados (para o restore_stock.sh).
SETTINGS_KEYS=(wifi_scan_always_enabled ble_scan_always_enabled
               window_animation_scale transition_animation_scale animator_duration_scale)
if [[ ! -f "$SETTINGS_BACKUP" ]] && (( ! DRY_RUN )); then
  for k in "${SETTINGS_KEYS[@]}"; do
    printf '%s=%s\n' "$k" "$(adb shell settings get global "$k" | tr -d '\r')"
  done >"$SETTINGS_BACKUP"
  log "Settings originais salvos em logs/settings_before.txt"
fi

REMOVED=(); DISABLED=(); FAILED=()

log "--- Desinstalando (--user 0, mantendo dados com -k) ---"
for p in "${UNINSTALL_TARGETS[@]}"; do
  if ! installed "$p"; then log "skip (não instalado): $p"; continue; fi
  if run adb shell pm uninstall -k --user 0 "$p"; then REMOVED+=("$p"); else FAILED+=("$p"); fi
done

log "--- Desativando (disable-user) ---"
for p in "${DISABLE_TARGETS[@]}"; do
  if ! installed "$p"; then log "skip (não instalado): $p"; continue; fi
  run adb shell am force-stop "$p"
  if run adb shell pm disable-user --user 0 "$p"; then DISABLED+=("$p"); else FAILED+=("$p"); fi
done

log "--- Otimizações de subsistema ---"
run adb shell settings put global wifi_scan_always_enabled 0
run adb shell settings put global ble_scan_always_enabled 0
run adb shell settings put global window_animation_scale 0.0
run adb shell settings put global transition_animation_scale 0.0
run adb shell settings put global animator_duration_scale 0.0

log "--- Limpeza de caches ---"
run adb shell pm trim-caches 999G

log "=== RESUMO ==="
log "Removidos (${#REMOVED[@]}): ${REMOVED[*]:-}"
log "Desativados (${#DISABLED[@]}): ${DISABLED[*]:-}"
log "Falhas (${#FAILED[@]}): ${FAILED[*]:-nenhuma}"
(( ${#FAILED[@]} == 0 ))
