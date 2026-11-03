package com.allanmelo.harness

import android.content.Intent
import android.speech.RecognitionService

class AssistRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        // Stub exigido pelo VoiceInteractionServiceInfo do Android 9
    }

    override fun onCancel(listener: Callback?) {
        // Stub exigido pelo VoiceInteractionServiceInfo do Android 9
    }

    override fun onStopListening(listener: Callback?) {
        // Stub exigido pelo VoiceInteractionServiceInfo do Android 9
    }
}
