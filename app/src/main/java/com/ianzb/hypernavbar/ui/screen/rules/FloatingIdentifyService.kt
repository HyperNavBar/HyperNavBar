package com.ianzb.hypernavbar.ui.screen.rules

// allow: SIZE_OK — single-responsibility overlay service; View API verbosity inflates line count
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.ianzb.hypernavbar.AppIdentifyAccessibilityService

class FloatingIdentifyService : Service() {

    companion object {
        const val ACTION_QUICK_ADD = "com.ianzb.hypernavbar.QUICK_ADD_APP"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_APP_NAME = "app_name"
        const val EXTRA_ACTIVITY_NAME = "activity_name"
        private const val NOTIFICATION_CHANNEL_ID = "floating_identify_channel"
        private const val NOTIFICATION_ID = 2001
    }

    private var windowManager: WindowManager? = null
    private var floatingView: ViewGroup? = null
    private var tvPkgValue: TextView? = null
    private var tvAppValue: TextView? = null
    private var tvActivityValue: TextView? = null

    private var currentPkg: String = ""
    private var currentApp: String = ""
    private var currentActivity: String = ""

    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var isDragging = false
    private var dragThreshold = 0f

    private val foregroundReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != AppIdentifyAccessibilityService.ACTION_FOREGROUND_CHANGED) return
            currentPkg = intent.getStringExtra(AppIdentifyAccessibilityService.EXTRA_PACKAGE_NAME) ?: return
            currentActivity = intent.getStringExtra(AppIdentifyAccessibilityService.EXTRA_ACTIVITY_NAME) ?: ""
            currentApp = intent.getStringExtra(AppIdentifyAccessibilityService.EXTRA_APP_NAME) ?: ""
            updateDisplayedInfo()
        }
    }

    override fun onCreate() {
        super.onCreate()
        dragThreshold = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics
        )
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (floatingView == null) {
            createFloatingWindow()
        }
        registerForegroundReceiver()
        return START_STICKY
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(foregroundReceiver)
        } catch (_: Exception) {
            // Receiver was not registered
        }
        removeFloatingWindow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Foreground notification ──────────────────────────────────────────

    private fun startForegroundNotification() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Floating Identify",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notification for floating window identify service"
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, com.ianzb.hypernavbar.MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("App Identifier Active")
            .setContentText("Tap to return to HyperNavBar")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    // ── Broadcast receiver ───────────────────────────────────────────────

    private fun registerForegroundReceiver() {
        val filter = IntentFilter(AppIdentifyAccessibilityService.ACTION_FOREGROUND_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(foregroundReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(foregroundReceiver, filter)
        }
    }

    // ── Floating window creation ─────────────────────────────────────────

    private fun createFloatingWindow() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(argb(0xDD, 0x1C, 0x1C, 0x1E))
            alpha = 0.85f
        }

        // Title bar
        val titleText = TextView(this).apply {
            text = "App Identifier"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(6))
        }
        layout.addView(titleText)

        // Separator
        layout.addView(createSeparator())

        // Row: Package Name
        layout.addView(createInfoRow("Package", "") { tvPkgValue = it })

        // Row: App Name
        layout.addView(createInfoRow("App", "") { tvAppValue = it })

        // Row: Activity
        layout.addView(createInfoRow("Activity", "") { tvActivityValue = it })

        // Separator
        layout.addView(createSeparator())

        // Button row
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, 0)
        }

        val quickAddBtn = Button(this).apply {
            text = "Quick Add"
            setTextColor(Color.WHITE)
            setBackgroundColor(argb(0xFF, 0x00, 0x7A, 0xFF))
            textSize = 12f
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener { onQuickAdd() }
        }
        buttonRow.addView(quickAddBtn, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ).apply { setMargins(0, 0, dp(6), 0) })

        val closeBtn = Button(this).apply {
            text = "Close"
            setTextColor(Color.WHITE)
            setBackgroundColor(argb(0xFF, 0x44, 0x44, 0x44))
            textSize = 12f
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener { stopSelf() }
        }
        buttonRow.addView(closeBtn, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ))

        layout.addView(buttonRow)

        // Drag handler
        layout.setOnTouchListener { view, event ->
            handleDrag(view, event)
        }

        floatingView = layout

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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(120)
        }

        windowManager!!.addView(floatingView, params)
    }

    // ── Helper views ─────────────────────────────────────────────────────

    private fun createSeparator(): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
            ).apply {
                setMargins(0, dp(4), 0, dp(4))
            }
            setBackgroundColor(argb(0x44, 0xFF, 0xFF, 0xFF))
        }
    }

    private fun createInfoRow(
        label: String,
        initialValue: String,
        valueRef: (TextView) -> Unit,
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(2), 0, dp(2))
        }

        val labelView = TextView(this).apply {
            text = label
            setTextColor(argb(0xFF, 0x99, 0x99, 0x99))
            textSize = 10f
            setPadding(0, 0, 0, dp(1))
        }
        row.addView(labelView)

        val valueView = TextView(this).apply {
            text = initialValue.ifEmpty { "—" }
            setTextColor(Color.WHITE)
            textSize = 12f
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setOnClickListener { onCopyValue(text.toString()) }
        }
        valueRef(valueView)
        row.addView(valueView)

        return row
    }

    // ── Display update ───────────────────────────────────────────────────

    private fun updateDisplayedInfo() {
        tvPkgValue?.text = currentPkg.ifEmpty { "—" }
        tvAppValue?.text = currentApp.ifEmpty { "—" }
        tvActivityValue?.text = currentActivity.ifEmpty { "—" }
    }

    // ── Copy to clipboard ────────────────────────────────────────────────

    private fun onCopyValue(value: String) {
        if (value == "—" || value.isEmpty()) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("app_info", value)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Copied: $value", Toast.LENGTH_SHORT).show()
    }

    // ── Quick Add broadcast ──────────────────────────────────────────────

    private fun onQuickAdd() {
        if (currentPkg.isEmpty()) {
            Toast.makeText(this, "No app detected yet", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(ACTION_QUICK_ADD).apply {
            putExtra(EXTRA_PACKAGE_NAME, currentPkg)
            putExtra(EXTRA_APP_NAME, currentApp)
            putExtra(EXTRA_ACTIVITY_NAME, currentActivity)
        }
        sendBroadcast(intent)
        Toast.makeText(this, "Sent to editor", Toast.LENGTH_SHORT).show()
    }

    // ── Drag handling ────────────────────────────────────────────────────

    private fun handleDrag(view: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = (view.layoutParams as WindowManager.LayoutParams).x
                initialY = (view.layoutParams as WindowManager.LayoutParams).y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.rawX - initialTouchX
                val deltaY = event.rawY - initialTouchY
                if (!isDragging && (Math.abs(deltaX) > dragThreshold || Math.abs(deltaY) > dragThreshold)) {
                    isDragging = true
                }
                if (isDragging) {
                    val params = view.layoutParams as WindowManager.LayoutParams
                    params.x = initialX + deltaX.toInt()
                    params.y = initialY + deltaY.toInt()
                    windowManager?.updateViewLayout(view, params)
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    return true
                }
            }
        }
        return false
    }

    // ── Cleanup ──────────────────────────────────────────────────────────

    private fun removeFloatingWindow() {
        try {
            if (floatingView != null) {
                windowManager?.removeView(floatingView)
                floatingView = null
            }
        } catch (_: Exception) {
            // Window already removed
        }
    }

    // ── Utility ──────────────────────────────────────────────────────────

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
        ).toInt()
    }

    private fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int {
        return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
    }
}
