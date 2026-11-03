# TicWatch E2 "Harness"

Preparação do TicWatch E2 (`tunny`, Snapdragon Wear 2100, 512 MB, Wear OS 2 / Android 9)
para operar como dispositivo controlador de agentes de IA estilo *Autonomous Harness*,
mantendo paralelamente relógio inteligente, sincronização com iPhone (iOS) e Google Fit.

---

## Fase 1 — Debloat Cirúrgico Nível 2 (Concluída)

* 9 pacotes removidos/desativados (`com.mobvoi.*` e `com.google.android.googlequicksearchbox`).
* Play Store (`com.android.vending`) em estado `disabled-user`.
* Animações zeradas (`0.0`) e varredura contínua de Wi-Fi/BLE em background desligada.
* **MemAvailable:** aumentou de 146,5 MB para **173,1 MB (+26,6 MB)**.
* **Swap/ZRAM:** uso reduzido de 110 MB para **70 MB (-40 MB)**.
* GMS, Wear OS Home, Bluetooth e Google Fit preservados e ativos.
* Túnel com iPhone (`NetworkAgent PROXY` via `/clockwork_proxy/proxy`) validado.

---

## Fase 2 — Hardening OTA, App Android e HUD Circular (Concluída)

### 1. Bloqueio Definitivo de OTA
* `scripts/block_ota.sh` aplicado:
  * `global ota_disable_automatic_update` = `1`.
  * Componentes e canais de atualização silenciados.
  * Reversão inclusa em `scripts/restore_stock.sh`.

### 2. APK `tunny-harness` (`com.allanmelo.harness`)
* **Tamanho do APK:** apenas **~600 KB** (otimizado via R8 / ProGuard, minSdk 28, targetSdk 30).
* **Consumo de Memória RAM:** apenas **~20 MB PSS total** (Heap Dalvik de míseros ~670 KB!).
* **HUD Circular OLED (400x400):**
  * Fundo `#000000` absoluto (consumo de energia mínimo na tela OLED).
  * Anel perimetral customizado desenhado via `Canvas`:
    * `IDLE`: cinza escuro (`#333333`)
    * `LISTENING`: ciano brilhante pulsante (`#00E5FF`)
    * `THINKING / RUNNING`: arco em rotação âmbar (`#FF9100`)
    * `DONE`: verde (`#00E676`) + pulso háptico (80ms)
    * `ERROR`: vermelho (`#FF1744`) + duplo pulso háptico
  * Captura de áudio nativa via `MediaRecorder` em **AAC 16kHz mono a 24 kbps** (`cmd.m4a`), gerando payloads levíssimos (~3 KB/s) para transmissão quase instantânea via proxy BLE ou Wi-Fi.
  * Configuração rápida de Backend: toque longo no indicador de rede no topo da tela para alterar a URL via diálogo com teclado ou pré-configurado via Wi-Fi do Mac.

### 3. Substituto do Assistente (Botão Físico Lateral)
* Script `scripts/bind_assistant_button.sh` executado:
  * Permissão `RECORD_AUDIO` pré-concedida via ADB (`pm grant`).
  * `secure assistant` -> `com.allanmelo.harness/.MainActivity`
  * `secure voice_interaction_service` -> `com.allanmelo.harness/.AssistStubService`
  * `secure voice_recognition_service` -> `com.allanmelo.harness/.AssistRecognitionService` (necessário no Android 9).
  * Intent-filter registrado para `android.intent.action.VOICE_ASSIST`.
  * **Comportamento:** Pressionar/segurar o botão físico lateral do relógio abre a interface circular e **inicia a escuta de voz automaticamente (`autoListen = true`)**. Basta falar e tocar no centro para disparar o comando.

### 4. Tile Lateral Nativo (Swipe na Watchface - Compatível Wear OS 2 + 3)
* Suporte duplo implementado:
  * **Wear OS 2 (TicWatch E2):** IPC Binder proprietário via AIDL `com.google.android.clockwork.tiles.ITileProvider` + `RemoteViews` (`R.layout.tile_harness`) com action `com.google.android.clockwork.ACTION_TILE_UPDATE_REQUEST`.
  * **Wear OS 3+:** Jetpack `androidx.wear.tiles:tiles:1.1.0`.
* **Como ativar no relógio:**
  1. Na tela principal da watchface, deslize o dedo para a esquerda até o final do carrossel de Tiles.
  2. Toque no botão redondo **"+" (Adicionar bloco)**.
  3. Selecione **"Harness Agent"** na lista.
  4. O Tile exibirá o status do agente e o botão `[ COMMAND ]`, que abre a `MainActivity` pronta para escuta.

---

## Teste End-to-End com Mock Server

Um servidor local em Python foi criado e testado com sucesso:

```bash
# Iniciar o servidor de teste no Mac (porta 8765):
python3 scripts/mock_harness_backend.py
```

* **GET /status:** Retorna status JSON do agente (`HERMES-01`).
* **POST /voice:** Recebe o arquivo `.m4a` enviado pelo relógio e salva em `logs/last_voice_cmd.m4a`.
* **Resultado do teste real:** Áudios de 38 KB e 104 KB foram gravados no relógio, transmitidos com latência de ~100ms e validados como arquivos válidos de áudio AAC (`ISO Media, MP4 v2`).

---

## Estrutura do Repositório

```text
~/ticwatch-e2-harness-prep/
├── README.md                     # Documentação completa
├── snapshots/                    # Dumps de pacotes e métricas de RAM/ZRAM (before/after)
├── scripts/
│   ├── audit_device.sh           # Auditoria e varredura de pacotes
│   ├── debloat_level2.sh         # Execução do debloat Nível 2 com whitelist
│   ├── block_ota.sh              # Bloqueio de OTA e atualizações automáticas
│   ├── bind_assistant_button.sh  # Permissões e vinculação do botão físico
│   ├── mock_harness_backend.py   # Servidor HTTP local para testes
│   └── restore_stock.sh          # Rollback completo
├── harness-android-app/          # Projeto Android Kotlin (Gradle CLI)
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   └── app/
│       ├── build.gradle.kts      # minSdk 28, targetSdk 30, R8 minify
│       └── src/main/
│           ├── AndroidManifest.xml
│           ├── res/xml/interaction_service.xml
│           └── java/com/allan/harness/
│               ├── MainActivity.kt       # HUD OLED + Gravação + Haptic
│               ├── AssistStubService.kt  # VoiceInteractionService
│               ├── HarnessTileService.kt # Tile lateral Wear OS
│               └── HarnessNetClient.kt   # Cliente OkHttp leve
└── logs/
    ├── execution.log             # Log de todos os comandos ADB
    └── last_voice_cmd.m4a        # Último áudio capturado e enviado pelo relógio
```
