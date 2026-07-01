package com.ianzb.hypernavbar.ui.screen.rules

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.ianzb.hypernavbar.LocaleHelper
import com.ianzb.hypernavbar.R
import java.io.File

class ScreenColorPickerService : Service() {

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getSavedLanguage(newBase)
        super.attachBaseContext(LocaleHelper.wrapContext(newBase, language))
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "screen_color_picker_channel"
        private const val NOTIFICATION_ID = 2002
        private const val SCREENSHOT_PATH = "/data/local/tmp/hypernavbar_screenshot.png"

        @Volatile
        var pendingColorResult: ((colorArgb: Int) -> Unit)? = null

        @Volatile
        private var runningInstance: ScreenColorPickerService? = null

        /** 取色器是否正在运行 */
        @JvmStatic
        val isRunning: Boolean get() = runningInstance != null
    }

    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null

    // Phase 1: small control window
    private var controlView: ViewGroup? = null
    private var controlX: Int = 0
    private var controlY: Int = 0
    private var controlTouchX: Float = 0f
    private var controlTouchY: Float = 0f
    private var isDragging = false
    private var dragThreshold = 0f

    // Phase 2: fullscreen picker
    private var pickerView: ViewGroup? = null
    private var screenshotBitmap: Bitmap? = null
    private var crosshairX: Float = 0f
    private var crosshairY: Float = 0f
    private var currentColor: Int = Color.BLACK
    private var colorPreview: View? = null
    private var colorText: TextView? = null
    private var scaledBitmap: Bitmap? = null

    override fun onCreate() {
        super.onCreate()
        runningInstance = this
        dragThreshold = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics
        )
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (controlView == null && pickerView == null) {
            showControlWindow()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        runningInstance = null
        removeView(controlView); controlView = null
        removeView(pickerView); pickerView = null
        screenshotBitmap?.recycle(); screenshotBitmap = null
        scaledBitmap?.recycle(); scaledBitmap = null
        try { File(SCREENSHOT_PATH).delete() } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Foreground notification ───────────────────────────────────────

    private fun startForegroundNotification() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.editor_screen_color_pick_title),
            NotificationManager.IMPORTANCE_LOW
        )
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, com.ianzb.hypernavbar.MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.editor_screen_color_pick_title))
            .setContentText(getString(R.string.editor_screen_color_hint))
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    // ── Phase 1: Small control window ─────────────────────────────────

    private fun showControlWindow() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                setColor(argb(0xE0, 0x1C, 0x1C, 0x1E))
                cornerRadius = dp(18).toFloat()
            }
        }

        // Title
        val titleTv = TextView(this).apply {
            text = getString(R.string.editor_screen_color_pick_title)
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(0, 0, 0, dp(8))
        }
        layout.addView(titleTv)

        // Hint
        val hintTv = TextView(this).apply {
            text = getString(R.string.editor_screen_color_hint)
            setTextColor(argb(0xFF, 0x99, 0x99, 0x99))
            textSize = 12f
            setPadding(0, 0, 0, dp(10))
        }
        layout.addView(hintTv)

        // Button row
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        // Screenshot button
        val captureBtn = TextView(this).apply {
            text = getString(R.string.editor_screen_color_capture)
            setTextColor(Color.WHITE)
            textSize = 13f
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setPadding(dp(16), dp(8), dp(16), dp(8))
            background = GradientDrawable().apply {
                setColor(argb(0xFF, 0x00, 0x7A, 0xFF))
                cornerRadius = dp(18).toFloat()
            }
            setOnClickListener { onCaptureClicked() }
        }
        btnRow.addView(captureBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(0, 0, dp(8), 0)
        })

        // Cancel button
        val cancelBtn = TextView(this).apply {
            text = getString(R.string.editor_screen_color_cancel)
            setTextColor(Color.WHITE)
            textSize = 13f
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setPadding(dp(16), dp(8), dp(16), dp(8))
            background = GradientDrawable().apply {
                setColor(argb(0xFF, 0x58, 0x58, 0x5A))
                cornerRadius = dp(18).toFloat()
            }
            setOnClickListener { stopSelf() }
        }
        btnRow.addView(cancelBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        layout.addView(btnRow)

        // Drag
        layout.setOnTouchListener { _, event -> handleControlDrag(layout, event) }

        controlView = layout

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(120)
        }
        windowManager!!.addView(controlView, params)
    }

    private fun handleControlDrag(view: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                controlX = (view.layoutParams as WindowManager.LayoutParams).x
                controlY = (view.layoutParams as WindowManager.LayoutParams).y
                controlTouchX = event.rawX
                controlTouchY = event.rawY
                isDragging = false
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - controlTouchX
                val dy = event.rawY - controlTouchY
                if (!isDragging && (Math.abs(dx) > dragThreshold || Math.abs(dy) > dragThreshold)) isDragging = true
                if (isDragging) {
                    val p = view.layoutParams as WindowManager.LayoutParams
                    p.x = controlX + dx.toInt()
                    p.y = controlY + dy.toInt()
                    windowManager?.updateViewLayout(view, p)
                    return true
                }
            }
            MotionEvent.ACTION_UP -> { if (isDragging) return true }
        }
        return false
    }

    // ── Capture button clicked: hide control, take screenshot, show picker ─

    private fun onCaptureClicked() {
        // Hide control window first
        removeView(controlView); controlView = null

        // Short delay to let the overlay disappear, then take screenshot
        handler.postDelayed({
            Thread {
                try {
                    val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "screencap -p $SCREENSHOT_PATH"))
                    val exitCode = process.waitFor()
                    if (exitCode != 0) {
                        handler.post {
                            Toast.makeText(applicationContext, getString(R.string.editor_screenshot_failed), Toast.LENGTH_SHORT).show()
                            stopSelf()
                        }
                        return@Thread
                    }
                    val bitmap = BitmapFactory.decodeFile(SCREENSHOT_PATH)
                    if (bitmap == null) {
                        handler.post {
                            Toast.makeText(applicationContext, getString(R.string.editor_screenshot_load_failed), Toast.LENGTH_SHORT).show()
                            stopSelf()
                        }
                        return@Thread
                    }
                    screenshotBitmap = bitmap
                    handler.post { showPickerWindow(bitmap) }
                } catch (e: Exception) {
                    handler.post {
                        Toast.makeText(applicationContext, getString(R.string.editor_screenshot_error, e.message ?: ""), Toast.LENGTH_SHORT).show()
                        stopSelf()
                    }
                }
            }.start()
        }, 200L)
    }

    // ── Phase 2: Fullscreen picker ────────────────────────────────────

    private fun showPickerWindow(bitmap: Bitmap) {
        windowManager = windowManager ?: getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = resources.displayMetrics
        val screenW = dm.widthPixels
        val screenH = dm.heightPixels

        // Screenshot scaled to 3/4 of screen (width & height), centered
        val imageW = (screenW * 3) / 4
        val imageH = (screenH * 3) / 4
        val sbmp = scaledBitmap ?: Bitmap.createScaledBitmap(bitmap, imageW, imageH, true)
        scaledBitmap = sbmp

        val root = FrameLayout(this)

        // Dark backdrop behind image (dim the rest of screen)
        val backdrop = View(this).apply {
            setBackgroundColor(argb(0xCC, 0x00, 0x00, 0x00))
        }
        root.addView(backdrop, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // ── Image area (3/4 w x 3/4 h, centered) ───────────────────
        val imageFrame = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(dp(2), Color.WHITE)
                cornerRadius = dp(4).toFloat()
            }
        }
        val imgView = ImageView(this).apply {
            setImageBitmap(sbmp)
            scaleType = ImageView.ScaleType.FIT_XY
            isClickable = false
            isFocusable = false
        }
        imageFrame.addView(imgView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val crosshairView = object : View(this) {
            override fun onDraw(c: Canvas) {
                super.onDraw(c)
                val cx = width / 2f; val cy = height / 2f
                val len = 36f; val gap = 12f
                // Gray outline
                val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = argb(0xFF, 0x66, 0x66, 0x66); strokeWidth = 5f; style = Paint.Style.STROKE
                }
                c.drawLine(cx - len, cy, cx - gap, cy, outline)
                c.drawLine(cx + gap, cy, cx + len, cy, outline)
                c.drawLine(cx, cy - len, cx, cy - gap, outline)
                c.drawLine(cx, cy + gap, cx, cy + len, outline)
                // White core
                val light = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE; strokeWidth = 2f; style = Paint.Style.STROKE
                }
                c.drawLine(cx - len, cy, cx - gap, cy, light)
                c.drawLine(cx + gap, cy, cx + len, cy, light)
                c.drawLine(cx, cy - len, cx, cy - gap, light)
                c.drawLine(cx, cy + gap, cx, cy + len, light)
                // Center dot
                val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL; color = currentColor
                }
                c.drawCircle(cx, cy, 5f, fill)
                val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; color = Color.WHITE; strokeWidth = 2f
                }
                c.drawCircle(cx, cy, 9f, ring)
            }
        }
        val cs = dp(72)
        val cp = FrameLayout.LayoutParams(cs, cs).apply { gravity = Gravity.CENTER }
        imageFrame.addView(crosshairView, cp)

        val imgFrameParams = FrameLayout.LayoutParams(imageW, imageH).apply {
            gravity = Gravity.CENTER
            // Shift up slightly to leave room for panel
            topMargin = -dp(32)
        }
        root.addView(imageFrame, imgFrameParams)

        // ── Bottom panel ────────────────────────────────────────────
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply {
                setColor(argb(0xE0, 0x1C, 0x1C, 0x1E))
                cornerRadius = dp(18).toFloat()
            }
            gravity = Gravity.CENTER_VERTICAL
        }
        val preview = View(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(currentColor) }
        }
        colorPreview = preview
        panel.addView(preview, LinearLayout.LayoutParams(dp(36), dp(36)))
        val hexTv = TextView(this).apply {
            text = colorToHex(currentColor); setTextColor(Color.WHITE); textSize = 14f; setPadding(dp(12), 0, 0, 0)
        }
        colorText = hexTv
        panel.addView(hexTv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        panel.addView(makeBtn(getString(R.string.editor_screen_color_confirm), argb(0xFF, 0x00, 0x7A, 0xFF)) { onConfirm() })
        panel.addView(makeBtn(getString(R.string.editor_screen_color_cancel), argb(0xFF, 0x58, 0x58, 0x5A)) { stopSelf() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8) })
        root.addView(panel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.BOTTOM })

        crosshairX = imageW / 2f; crosshairY = imageH / 2f
        updateColor(sbmp, imageW, imageH)

        // Touch — only on image area
        imageFrame.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    crosshairX = event.x.coerceIn(0f, (imageW - 1).toFloat())
                    crosshairY = event.y.coerceIn(0f, (imageH - 1).toFloat())
                    cp.leftMargin = (crosshairX - cs / 2).toInt(); cp.topMargin = (crosshairY - cs / 2).toInt()
                    cp.gravity = Gravity.NO_GRAVITY; crosshairView.layoutParams = cp
                    updateColor(sbmp, imageW, imageH); crosshairView.invalidate()
                    true
                }
                MotionEvent.ACTION_UP -> true
                else -> false
            }
        }

        pickerView = root
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        windowManager!!.addView(pickerView, WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = 0 })
    }

    private fun updateColor(bmp: Bitmap, w: Int, h: Int) {
        currentColor = bmp.getPixel(crosshairX.toInt().coerceIn(0, w - 1), crosshairY.toInt().coerceIn(0, h - 1))
        colorPreview?.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(currentColor) }
        colorText?.text = colorToHex(currentColor)
    }

    private fun onConfirm() {
        pendingColorResult?.invoke(currentColor)
        pendingColorResult = null
        // Return to app
        startActivity(Intent(this, com.ianzb.hypernavbar.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
        stopSelf()
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private fun makeBtn(text: String, bg: Int, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text; setTextColor(Color.WHITE); textSize = 13f; gravity = Gravity.CENTER
            isClickable = true; isFocusable = true; setPadding(dp(16), dp(8), dp(16), dp(8))
            background = GradientDrawable().apply { setColor(bg); cornerRadius = dp(18).toFloat() }
            setOnClickListener { onClick() }
        }
    }

    private fun removeView(v: ViewGroup?) {
        try { if (v != null) windowManager?.removeView(v) } catch (_: Exception) {}
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private fun argb(a: Int, r: Int, g: Int, b: Int): Int = (a shl 24) or (r shl 16) or (g shl 8) or b

    private fun colorToHex(color: Int): String {
        val a = (color ushr 24) and 0xFF; val r = (color ushr 16) and 0xFF
        val g = (color ushr 8) and 0xFF; val b = color and 0xFF
        return if (a == 255) "#${hex(r)}${hex(g)}${hex(b)}".uppercase()
        else "#${hex(a)}${hex(r)}${hex(g)}${hex(b)}".uppercase()
    }

    private fun hex(v: Int): String = v.toString(16).padStart(2, '0')
}
