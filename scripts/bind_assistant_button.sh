#!/usr/bin/env bash
set -euo pipefail

PKG="com.allanmelo.harness"

echo "[1/4] Concedendo permissão de microfone previamente via ADB (sem pop-up)..."
adb shell pm grant "$PKG" android.permission.RECORD_AUDIO

echo "[2/4] Registrando o Harness como Assistente Padrão e VoiceInteractionService..."
adb shell settings put secure assistant "$PKG/.MainActivity"
adb shell settings put secure voice_interaction_service "$PKG/.AssistStubService"
adb shell settings put secure voice_recognition_service "$PKG/.AssistRecognitionService"

echo "[3/4] Garantindo comportamento do botão de hardware no PhoneWindowManager..."
# Valor 4 = LONG_PRESS_POWER_GO_TO_VOICE_ASSIST (padrão Wear OS para Assistente de Voz)
# Se não estiver setado, forçar para 4
adb shell settings put global long_press_power_behavior 4 || true

echo "[4/4] Verificando bindings do sistema..."
echo "assistant -> $(adb shell settings get secure assistant)"
echo "voice_interaction_service -> $(adb shell settings get secure voice_interaction_service)"
echo "voice_recognition_service -> $(adb shell settings get secure voice_recognition_service)"
echo "dumpsys voiceinteraction:"
adb shell dumpsys voiceinteraction | grep -E "mEnableService|mService|NOT VALID" || true
