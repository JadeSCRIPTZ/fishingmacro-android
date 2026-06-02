package com.jadescriptz.fishingmacro

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import java.lang.ref.WeakReference

class WatcherService : Service() {

    companion object {
        const val CHANNEL_ID = "FishingMacroChannel"
        const val NOTIF_ID   = 1

        var pixelX        = -1
        var pixelY        = -1
        var targetR       = -1
        var targetG       = -1
        var targetB       = -1
        var tolerance     = 15
        var reactionDelay = 100L
        var cooldownMs    = 3000f
        var naturalMode   = false
        var captureForColor = false

        @Volatile var running = false
        fun stopWatcher() { running = false }

        // Callback catre Activity (WeakReference pentru a nu face memory leak)
        var activityRef: WeakReference<MainActivity>? = null

        fun notifyActivity(msg: String) {
            activityRef?.get()?.runOnUiThread {
                activityRef?.get()?.appendLog(msg)
            }
        }
        fun notifyColorCaptured(r: Int, g: Int, b: Int) {
            activityRef?.get()?.runOnUiThread {
                activityRef?.get()?.updateColorPreview(r, g, b)
            }
        }
    }

    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var workerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var screenWidth  = 0
    private var screenHeight = 0
    private var screenDensity = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as WindowManager)
            .defaultDisplay.getMetrics(metrics)
        screenWidth   = metrics.widthPixels
        screenHeight  = metrics.heightPixels
        screenDensity = metrics.densityDpi
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())

        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED)
            ?: return START_NOT_STICKY

        @Suppress("DEPRECATION")
        val data: Intent? = intent.getParcelableExtra("data")
        data ?: return START_NOT_STICKY

        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mgr.getMediaProjection(resultCode, data)

        imageReader = ImageReader.newInstance(
            screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)

        virtualDisplay = mediaProjection!!.createVirtualDisplay(
            "FishingCapture", screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, null)

        workerThread = HandlerThread("WatcherWorker").also { it.start() }
        handler = Handler(workerThread!!.looper)

        if (captureForColor) {
            handler!!.postDelayed({ captureColorOnce() }, 400)
        } else {
            running = true
            handler!!.post { watchLoop() }
        }
        return START_NOT_STICKY
    }

    // ── Captureaza culoarea o singura data ────────────────────────────────────
    private fun captureColorOnce() {
        val bmp = captureScreen() ?: run {
            notifyActivity("❌ Eroare la capturarea ecranului.")
            stopSelf(); return
        }
        val x = pixelX.coerceIn(0, bmp.width  - 1)
        val y = pixelY.coerceIn(0, bmp.height - 1)
        val pixel = bmp.getPixel(x, y)
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8)  and 0xFF
        val b =  pixel         and 0xFF
        bmp.recycle()
        notifyColorCaptured(r, g, b)
        stopSelf()
    }

    // ── Loop principal ────────────────────────────────────────────────────────
    private fun watchLoop() {
        notifyActivity("Stare: RESET — astept ca pixelul sa fie normal...")
        var state   = "RESET"
        var catches = 0
        var skipped = 0

        while (running) {
            try {
                val bmp = captureScreen()
                if (bmp == null) { Thread.sleep(50); continue }

                val x = pixelX.coerceIn(0, bmp.width  - 1)
                val y = pixelY.coerceIn(0, bmp.height - 1)
                val pixel = bmp.getPixel(x, y)
                bmp.recycle()

                val cr = (pixel shr 16) and 0xFF
                val cg = (pixel shr 8)  and 0xFF
                val cb =  pixel         and 0xFF
                val isMatch = Math.abs(cr - targetR) <= tolerance &&
                              Math.abs(cg - targetG) <= tolerance &&
                              Math.abs(cb - targetB) <= tolerance

                when (state) {
                    "RESET" -> {
                        if (!isMatch) {
                            state = "WATCH"
                            notifyActivity("Stare: WATCH — monitorizez bobberul...")
                        }
                    }
                    "WATCH" -> {
                        if (isMatch) {
                            // Mod natural: 8% skip
                            if (naturalMode && Math.random() < 0.08) {
                                skipped++
                                notifyActivity("[skip #$skipped] Mod natural — sarit intentionat.")
                                state = "RESET"
                                Thread.sleep(50)
                                continue
                            }
                            catches++
                            notifyActivity("[#$catches] Bobber detectat! RGB($cr,$cg,$cb) — astept ${reactionDelay}ms...")
                            if (reactionDelay > 0) Thread.sleep(reactionDelay)
                            if (!running) break

                            // Tap x2 (right-click echivalent pe Android)
                            FishingAccessibilityService.instance?.performTap(
                                pixelX.toFloat(), pixelY.toFloat())
                            Thread.sleep(500)
                            FishingAccessibilityService.instance?.performTap(
                                pixelX.toFloat(), pixelY.toFloat())

                            notifyActivity("[#$catches] Re-aruncat! Cooldown ${cooldownMs.toLong()}ms...")
                            Thread.sleep(cooldownMs.toLong())
                            state = "RESET"
                            notifyActivity("Stare: RESET — astept resetare pixel...")
                        }
                    }
                }
                Thread.sleep(50)
            } catch (e: InterruptedException) {
                break
            } catch (e: Exception) {
                notifyActivity("Eroare: ${e.message}")
                break
            }
        }
        running = false
        notifyActivity("⏹ Watcher oprit.")
        stopSelf()
    }

    // ── Screenshot ────────────────────────────────────────────────────────────
    private fun captureScreen(): Bitmap? {
        return try {
            val image = imageReader?.acquireLatestImage() ?: return null
            val planes     = image.planes
            val buffer     = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride   = planes[0].rowStride
            val rowPadding  = rowStride - pixelStride * screenWidth
            val bmp = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight, Bitmap.Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(buffer)
            image.close()
            Bitmap.createBitmap(bmp, 0, 0, screenWidth, screenHeight)
                .also { bmp.recycle() }
        } catch (e: Exception) { null }
    }

    override fun onDestroy() {
        running = false
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        workerThread?.quitSafely()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Fishing Macro",
                NotificationManager.IMPORTANCE_LOW).apply {
                description = "Pixel watcher activ"
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FishingMacro Pro")
            .setContentText("Pixel watcher activ...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }
}
