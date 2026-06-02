package com.jadescriptz.fishingmacro

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.lang.ref.WeakReference

class MainActivity : AppCompatActivity() {

    private val PROJECTION_REQUEST = 100

    private lateinit var tvStatus: TextView
    private lateinit var tvPixelCoords: TextView
    private lateinit var tvTargetColor: TextView
    private lateinit var colorPreview: View
    private lateinit var etPixelX: EditText
    private lateinit var etPixelY: EditText
    private lateinit var etTolerance: EditText
    private lateinit var etDelay: EditText
    private lateinit var etCooldown: EditText
    private lateinit var btnGetColor: Button
    private lateinit var btnNatural: Button
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var tvLog: TextView
    private lateinit var scrollLog: ScrollView

    private var naturalMode = false
    private var projectionManager: MediaProjectionManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inregistreaza callback-ul pentru log-uri din WatcherService
        WatcherService.activityRef = WeakReference(this)

        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE)
            as MediaProjectionManager

        bindViews()
        setupListeners()
        checkAccessibility()

        appendLog("🎣 FishingMacro Pro gata.")
        appendLog("Pas 1: Activeaza Accessibility Service.")
        appendLog("Pas 2: Introdu coordonatele X/Y ale bobber-ului.")
        appendLog("Pas 3: Apasa 'Preia Culoarea', apoi START.")
    }

    private fun bindViews() {
        tvStatus      = findViewById(R.id.tvStatus)
        tvPixelCoords = findViewById(R.id.tvPixelCoords)
        tvTargetColor = findViewById(R.id.tvTargetColor)
        colorPreview  = findViewById(R.id.colorPreview)
        etPixelX      = findViewById(R.id.etPixelX)
        etPixelY      = findViewById(R.id.etPixelY)
        etTolerance   = findViewById(R.id.etTolerance)
        etDelay       = findViewById(R.id.etDelay)
        etCooldown    = findViewById(R.id.etCooldown)
        btnGetColor   = findViewById(R.id.btnGetColor)
        btnNatural    = findViewById(R.id.btnNatural)
        btnStart      = findViewById(R.id.btnStart)
        btnStop       = findViewById(R.id.btnStop)
        tvLog         = findViewById(R.id.tvLog)
        scrollLog     = findViewById(R.id.scrollLog)
    }

    private fun setupListeners() {

        // Seteaza coordonatele din campurile X/Y
        btnGetColor.setOnClickListener {
            val x = etPixelX.text.toString().toIntOrNull() ?: -1
            val y = etPixelY.text.toString().toIntOrNull() ?: -1
            if (x < 0 || y < 0) {
                appendLog("⚠ Introdu coordonate X si Y valide mai intai!")
                return@setOnClickListener
            }
            WatcherService.pixelX = x
            WatcherService.pixelY = y
            tvPixelCoords.text = "Pixel setat: X=$x, Y=$y"
            appendLog("📍 Pixel setat la X=$x, Y=$y")
            appendLog("Captez culoarea... accepta permisiunea screen capture.")
            requestScreenCapture(forColor = true)
        }

        btnNatural.setOnClickListener {
            naturalMode = !naturalMode
            WatcherService.naturalMode = naturalMode
            if (naturalMode) {
                btnNatural.text = "🎲 MOD NATURAL: ON"
                btnNatural.setBackgroundColor(0xFFF0883E.toInt())
                appendLog("🎲 Mod Natural ACTIVAT — 8% sansa sa sara o detectie.")
            } else {
                btnNatural.text = "🎲 MOD NATURAL: OFF"
                btnNatural.setBackgroundColor(0xFF30363D.toInt())
                appendLog("Mod Natural DEZACTIVAT.")
            }
        }

        btnStart.setOnClickListener {
            if (!isAccessibilityEnabled()) {
                appendLog("❌ Activeaza Accessibility Service mai intai!")
                openAccessibilitySettings(); return@setOnClickListener
            }
            if (WatcherService.pixelX < 0 || WatcherService.pixelY < 0) {
                appendLog("❌ Seteaza coordonatele X/Y si preia culoarea mai intai!")
                return@setOnClickListener
            }
            if (WatcherService.targetR < 0) {
                appendLog("❌ Preia culoarea pixelului mai intai!")
                return@setOnClickListener
            }
            applySettings()
            requestScreenCapture(forColor = false)
        }

        btnStop.setOnClickListener {
            WatcherService.stopWatcher()
            setUiRunning(false)
            appendLog("⏹ Macro oprit.")
        }
    }

    private fun applySettings() {
        WatcherService.tolerance     = etTolerance.text.toString().toIntOrNull() ?: 15
        WatcherService.reactionDelay = etDelay.text.toString().toLongOrNull() ?: 100L
        WatcherService.cooldownMs    = (etCooldown.text.toString().toFloatOrNull() ?: 3f) * 1000f
        WatcherService.naturalMode   = naturalMode
    }

    private fun requestScreenCapture(forColor: Boolean) {
        WatcherService.captureForColor = forColor
        startActivityForResult(
            projectionManager!!.createScreenCaptureIntent(), PROJECTION_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PROJECTION_REQUEST &&
            resultCode == Activity.RESULT_OK && data != null) {

            val serviceIntent = Intent(this, WatcherService::class.java).apply {
                putExtra("resultCode", resultCode)
                putExtra("data", data)
            }
            ContextCompat.startForegroundService(this, serviceIntent)

            if (!WatcherService.captureForColor) {
                setUiRunning(true)
                appendLog("▶ Macro pornit! Monitorez pixelul...")
            } else {
                appendLog("📸 Captez culoarea la (${WatcherService.pixelX}, ${WatcherService.pixelY})...")
            }
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val service = "${packageName}/${FishingAccessibilityService::class.java.canonicalName}"
        return try {
            val enabled = Settings.Secure.getString(
                contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            enabled.contains(service)
        } catch (e: Exception) { false }
    }

    private fun checkAccessibility() {
        val btnAccess = findViewById<Button>(R.id.btnAccessibility)
        if (!isAccessibilityEnabled()) {
            tvStatus.text = "⚠ Accessibility Service inactiv"
            tvStatus.setTextColor(0xFFD29922.toInt())
            btnAccess.visibility = View.VISIBLE
            btnAccess.setOnClickListener {
                openAccessibilitySettings()
                appendLog("→ Gaseste 'FishingMacro Pro' si activeaza-l.")
            }
        } else {
            tvStatus.text = "✅ Accessibility activ"
            tvStatus.setTextColor(0xFF3FB950.toInt())
            btnAccess.visibility = View.GONE
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    fun appendLog(msg: String) {
        runOnUiThread {
            val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())
            tvLog.append("[$ts] $msg\n")
            scrollLog.post { scrollLog.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    fun updateColorPreview(r: Int, g: Int, b: Int) {
        runOnUiThread {
            val color = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            colorPreview.setBackgroundColor(color)
            tvTargetColor.text = "RGB($r,$g,$b)  #%02X%02X%02X".format(r, g, b)
            WatcherService.targetR = r
            WatcherService.targetG = g
            WatcherService.targetB = b
            appendLog("🎨 Culoare capturata → RGB($r,$g,$b)")
        }
    }

    private fun setUiRunning(running: Boolean) {
        btnStart.isEnabled   = !running
        btnStop.isEnabled    = running
        btnGetColor.isEnabled = !running
        btnNatural.isEnabled  = !running
        tvStatus.text = if (running) "● RUNNING" else "● OPRIT"
        tvStatus.setTextColor(
            if (running) 0xFF3FB950.toInt() else 0xFF8B949E.toInt())
    }

    override fun onResume() {
        super.onResume()
        WatcherService.activityRef = WeakReference(this)
        checkAccessibility()
    }

    override fun onDestroy() {
        WatcherService.activityRef = null
        super.onDestroy()
    }
}
