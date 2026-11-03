package com.allanmelo.harness

import android.Manifest
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.wear.ambient.AmbientModeSupport
import java.io.File

class MainActivity : FragmentActivity(), AmbientModeSupport.AmbientCallbackProvider {

    companion object {
        const val TAG = "HarnessApp"
        const val EXTRA_AUTO_LISTEN = "EXTRA_AUTO_LISTEN"
        private const val PREFS_NAME = "harness_prefs"
        private const val KEY_BACKEND_URL = "backend_url"
        private const val REQ_AUDIO_PERM = 101
    }

    enum class RingState(val color: Int) {
        IDLE(0xFF333333.toInt()),
        LISTENING(0xFF00E5FF.toInt()),
        THINKING(0xFFFF9100.toInt()),
        DONE(0xFF00E676.toInt()),
        ERROR(0xFFFF1744.toInt())
    }

    private lateinit var ringView: HarnessRingView
    private lateinit var tvRoute: TextView
    private lateinit var tvAgent: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvAction: TextView

    private lateinit var prefs: SharedPreferences
    private lateinit var ambientController: AmbientModeSupport.AmbientController
    private val netClient = HarnessNetClient()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var recorder: MediaRecorder? = null
    private var isRecording = false
    private var isAmbient = false
    private var audioFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ambientController = AmbientModeSupport.attach(this)
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        Log.i(TAG, "onCreate: action=${intent?.action}")
        buildUI()
        handleLaunchIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent?.let { handleLaunchIntent(it) }
    }

    override fun onResume() {
        super.onResume()
        updateNetworkRoute()
        if (!isRecording && ringView.currentState == RingState.IDLE) {
            fetchBackendStatus()
        }
    }

    private fun handleLaunchIntent(intent: Intent) {
        val autoListen = intent.getBooleanExtra(EXTRA_AUTO_LISTEN, false) ||
                Intent.ACTION_ASSIST == intent.action ||
                "android.intent.action.VOICE_ASSIST" == intent.action ||
                "android.intent.action.VOICE_COMMAND" == intent.action ||
                "android.speech.action.WEB_SEARCH" == intent.action

        Log.i(TAG, "handleLaunchIntent: action=${intent.action}, autoListen=$autoListen")
        if (autoListen) {
            mainHandler.postDelayed({
                checkPermAndStartRecording()
            }, 300)
        }
    }

    private fun buildUI() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        ringView = HarnessRingView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(ringView)

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(32, 40, 32, 40)
        }

        tvRoute = TextView(this).apply {
            text = "[SCANNING NET...]"
            setTextColor(0xFF888888.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            gravity = Gravity.CENTER
            isClickable = true
            setOnLongClickListener {
                showConfigDialog()
                true
            }
        }
        contentLayout.addView(tvRoute)

        val spacerMid1 = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, 0, 1.0f)
        }
        contentLayout.addView(spacerMid1)

        tvAgent = TextView(this).apply {
            text = "HERMES-01"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            gravity = Gravity.CENTER
            paint.isFakeBoldText = true
        }
        contentLayout.addView(tvAgent)

        tvStatus = TextView(this).apply {
            text = "STANDBY"
            setTextColor(0xFFB0BEC5.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            setPadding(16, 6, 16, 6)
        }
        contentLayout.addView(tvStatus)

        val spacerMid2 = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, 0, 1.0f)
        }
        contentLayout.addView(spacerMid2)

        tvAction = TextView(this).apply {
            text = "[ TAP TO SPEAK ]"
            setTextColor(0xFF00E5FF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            paint.isFakeBoldText = true
            setPadding(12, 12, 12, 12)
        }
        contentLayout.addView(tvAction)

        root.addView(contentLayout)

        root.setOnClickListener {
            onTapAction()
        }

        setContentView(root)
    }

    private fun onTapAction() {
        if (isRecording) {
            stopAndSendAudio()
        } else {
            checkPermAndStartRecording()
        }
    }

    private fun checkPermAndStartRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQ_AUDIO_PERM
            )
            return
        }
        startRecording()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_AUDIO_PERM && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startRecording()
        } else {
            tvStatus.text = "MIC PERM DENIED"
            ringView.setState(RingState.ERROR)
        }
    }

    private fun startRecording() {
        if (isRecording) return
        try {
            audioFile = File(cacheDir, "cmd.m4a").apply {
                if (exists()) delete()
            }

            Log.i(TAG, "Iniciando MediaRecorder no arquivo: ${audioFile?.absolutePath}")
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000)
                setAudioEncodingBitRate(24000)
                setAudioChannels(1)
                setOutputFile(audioFile!!.absolutePath)
                prepare()
                start()
            }

            isRecording = true
            vibratePulse(40)
            ringView.setState(RingState.LISTENING)
            tvStatus.text = "RECORDING (16kHz AAC)..."
            tvAction.text = "[ TAP TO SEND ]"
            tvAction.setTextColor(0xFFFF5252.toInt())
            Log.i(TAG, "Gravacao iniciada com sucesso.")
        } catch (e: Exception) {
            isRecording = false
            Log.e(TAG, "Erro ao iniciar gravacao: ${e.message}", e)
            tvStatus.text = "REC ERR: ${e.message}"
            ringView.setState(RingState.ERROR)
        }
    }

    private fun stopAndSendAudio() {
        if (!isRecording) return
        isRecording = false

        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            // Ignore stop errors on premature release
        }
        recorder = null

        val file = audioFile
        if (file == null || !file.exists() || file.length() == 0L) {
            tvStatus.text = "EMPTY AUDIO"
            ringView.setState(RingState.ERROR)
            tvAction.text = "[ TAP TO SPEAK ]"
            tvAction.setTextColor(0xFF00E5FF.toInt())
            return
        }

        ringView.setState(RingState.THINKING)
        tvStatus.text = "UPLOADING (${file.length()}B)..."
        tvAction.text = "[ RUNNING AGENT ]"
        tvAction.setTextColor(0xFFFF9100.toInt())

        val backendUrl = getBackendUrl()
        Log.i(TAG, "Enviando audio para $backendUrl/voice (${file.length()} bytes)")
        netClient.sendVoice(backendUrl, file) { result ->
            mainHandler.post {
                result.fold(
                    onSuccess = { status ->
                        Log.i(TAG, "Audio enviado com sucesso! Status: $status")
                        ringView.setState(RingState.DONE)
                        vibratePulse(80)
                        tvAgent.text = status.agent
                        tvStatus.text = status.lastMsg
                        tvAction.text = "[ TAP TO SPEAK ]"
                        tvAction.setTextColor(0xFF00E5FF.toInt())

                        mainHandler.postDelayed({
                            if (!isRecording && ringView.currentState == RingState.DONE) {
                                ringView.setState(RingState.IDLE)
                            }
                        }, 4000)
                    },
                    onFailure = { err ->
                        Log.e(TAG, "Falha no envio do audio: ${err.message}", err)
                        ringView.setState(RingState.ERROR)
                        vibratePattern(longArrayOf(0, 70, 70, 70))
                        tvStatus.text = "ERR: ${err.message}"
                        tvAction.text = "[ TAP TO RETRY ]"
                        tvAction.setTextColor(0xFFFF1744.toInt())
                    }
                )
            }
        }
    }

    private fun fetchBackendStatus() {
        val backendUrl = getBackendUrl()
        netClient.getStatus(backendUrl) { result ->
            mainHandler.post {
                result.onSuccess { status ->
                    tvAgent.text = status.agent
                    if (!isRecording) {
                        tvStatus.text = "${status.state} // ${status.lastMsg}"
                    }
                }
            }
        }
    }

    private fun updateNetworkRoute() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(activeNetwork)

        val route = when {
            caps == null -> "[OFFLINE]"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "[WIFI DIRECT]"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "[BLE PROXY]"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "[CELLULAR]"
            else -> "[CONNECTED]"
        }
        tvRoute.text = route
    }

    private fun getBackendUrl(): String {
        return prefs.getString(KEY_BACKEND_URL, BuildConfig.DEFAULT_BACKEND)
            ?: BuildConfig.DEFAULT_BACKEND
    }

    private fun showConfigDialog() {
        val current = getBackendUrl()
        val input = EditText(this).apply {
            setText(current)
            setSelection(current.length)
        }

        AlertDialog.Builder(this)
            .setTitle("Backend URL")
            .setView(input)
            .setPositiveButton("Salvar") { _, _ ->
                val newUrl = input.text.toString().trim()
                if (newUrl.isNotEmpty()) {
                    prefs.edit().putString(KEY_BACKEND_URL, newUrl).apply()
                    fetchBackendStatus()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun vibratePulse(durationMs: Long) {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }

    private fun vibratePattern(pattern: LongArray) {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    override fun getAmbientCallback(): AmbientModeSupport.AmbientCallback = object : AmbientModeSupport.AmbientCallback() {
        override fun onEnterAmbient(ambientDetails: Bundle?) {
            super.onEnterAmbient(ambientDetails)
            isAmbient = true
            ringView.setAmbient(true)
            tvAction.visibility = View.INVISIBLE
            tvRoute.visibility = View.INVISIBLE
        }

        override fun onExitAmbient() {
            super.onExitAmbient()
            isAmbient = false
            ringView.setAmbient(false)
            tvAction.visibility = View.VISIBLE
            tvRoute.visibility = View.VISIBLE
            updateNetworkRoute()
        }

        override fun onUpdateAmbient() {
            super.onUpdateAmbient()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        recorder?.release()
        recorder = null
    }

    class HarnessRingView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        private val oval = RectF()
        var currentState: RingState = RingState.IDLE
            private set
        private var isAmbient = false
        private var pulsePhase = 0f
        private var animator: ValueAnimator? = null

        fun setState(state: RingState) {
            currentState = state
            if (state == RingState.LISTENING) {
                startPulseAnimation()
            } else {
                stopPulseAnimation()
            }
            invalidate()
        }

        fun setAmbient(ambient: Boolean) {
            isAmbient = ambient
            if (ambient) stopPulseAnimation()
            invalidate()
        }

        private fun startPulseAnimation() {
            if (animator?.isRunning == true) return
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1000
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    pulsePhase = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        private fun stopPulseAnimation() {
            animator?.cancel()
            animator = null
            pulsePhase = 0f
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val strokeW = if (isAmbient) {
                2f
            } else if (currentState == RingState.LISTENING) {
                8f + (pulsePhase * 6f)
            } else {
                6f
            }

            paint.strokeWidth = strokeW
            paint.color = if (isAmbient) Color.DKGRAY else currentState.color

            val pad = strokeW / 2f + 4f
            oval.set(pad, pad, width - pad, height - pad)

            if (currentState == RingState.THINKING && !isAmbient) {
                // Indicador girando/parcial para modo thinking
                val startAngle = (System.currentTimeMillis() % 1200) / 1200f * 360f
                canvas.drawArc(oval, startAngle, 260f, false, paint)
                postInvalidateOnAnimation()
            } else {
                canvas.drawOval(oval, paint)
            }
        }
    }
}
