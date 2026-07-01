package com.ianzb.hypernavbar.ui.screen.rules

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
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.ianzb.hypernavbar.AppIdentifyAccessibilityService
import com.ianzb.hypernavbar.LocaleHelper
import com.ianzb.hypernavbar.R
import java.util.Date

class FloatingIdentifyService : Service() {

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getSavedLanguage(newBase)
        super.attachBaseContext(LocaleHelper.wrapContext(newBase, language))
    }

    companion object {
        const val ACTION_QUICK_ADD = "com.ianzb.hypernavbar.QUICK_ADD_APP"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_APP_NAME = "app_name"
        const val EXTRA_ACTIVITY_NAME = "activity_name"
        private const val NOTIFICATION_CHANNEL_ID = "floating_identify_channel"
        private const val NOTIFICATION_ID = 2001

        @Volatile
        private var runningInstance: FloatingIdentifyService? = null

        @Volatile
        var pendingQuickAdd: ((pkg: String, appName: String, activity: String) -> Unit)? = null

        /** 悬浮窗是否正在运行 */
        @JvmStatic
        val isRunning: Boolean get() = runningInstance != null

        /** 重建悬浮窗（用于语言切换等场景） */
        @JvmStatic
        fun recreateFloatingWindow() {
            runningInstance?.let { instance ->
                instance.removeFloatingWindow()
                instance.createFloatingWindow()
                instance.updateDisplayedInfo()
            }
        }

        @JvmStatic
        fun notifyForegroundApp(pkg: String, appName: String, activity: String) {
            runningInstance?.apply {
                val pkgChanged = currentPkg != pkg
                currentPkg = pkg
                currentApp = appName
                // Only overwrite activity if we got a valid one; keep previous otherwise
                if (activity.isNotEmpty()) currentActivity = activity
                else if (pkgChanged) currentActivity = ""
                addToHistory(pkg, appName, currentActivity)
                handler.post { updateDisplayedInfo() }
            }
        }
    }

    private data class HistoryEntry(
        val pkg: String,
        val appName: String,
        val activity: String,
        val time: Long,
    )

    private val handler = Handler(Looper.getMainLooper())
    private val history = mutableListOf<HistoryEntry>()
    private val MAX_HISTORY = 30

    private var windowManager: WindowManager? = null
    private var floatingView: ViewGroup? = null
    private var tvPkgValue: TextView? = null
    private var tvAppValue: TextView? = null
    private var tvActivityValue: TextView? = null
    private var infoContainer: LinearLayout? = null
    private var historyContainer: LinearLayout? = null
    private var historyBtn: TextView? = null
    private var showingHistory = false

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
            addToHistory(currentPkg, currentApp, currentActivity)
            updateDisplayedInfo()
        }
    }

    override fun onCreate() {
        super.onCreate()
        runningInstance = this
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
        runningInstance = null
        try {
            unregisterReceiver(foregroundReceiver)
        } catch (_: Exception) { }
        removeFloatingWindow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // 语言变更时重建悬浮窗以应用新语言
        removeFloatingWindow()
        createFloatingWindow()
        updateDisplayedInfo()
    }

    // ── History tracking ──────────────────────────────────────────────

    private fun addToHistory(pkg: String, appName: String, activity: String) {
        if (pkg.isEmpty()) return
        // Deduplicate: skip if same as last entry
        val last = history.lastOrNull()
        if (last != null && last.pkg == pkg && last.activity == activity) return
        history.add(HistoryEntry(pkg, appName, activity, System.currentTimeMillis()))
        if (history.size > MAX_HISTORY) {
            history.removeAt(0)
        }
        refreshHistoryView()
    }

    private fun refreshHistoryView() {
        val container = historyContainer ?: return
        container.removeAllViews()
        val sepColor = argb(0x33, 0xFF, 0xFF, 0xFF)
        val timeColor = argb(0xFF, 0x99, 0x99, 0x99)
        val nameColor = Color.WHITE
        val pkgColor = argb(0xFF, 0xBB, 0xBB, 0xBB)

        if (history.isEmpty()) {
            val empty = TextView(this).apply {
                text = getString(R.string.editor_floating_waiting)
                setTextColor(timeColor)
                textSize = 12f
                setPadding(0, dp(4), 0, dp(4))
            }
            container.addView(empty)
            return
        }

        for (entry in history.reversed()) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(4), 0, dp(4))
            }

            // Time + app name
            val headerRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            val timeTv = TextView(this).apply {
                text = DateFormat.format("HH:mm:ss", Date(entry.time)).toString()
                setTextColor(timeColor)
                textSize = 11f
                setPadding(0, 0, dp(6), 0)
            }
            headerRow.addView(timeTv)
            val nameTv = TextView(this).apply {
                text = entry.appName.ifEmpty { entry.pkg }
                setTextColor(nameColor)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            headerRow.addView(nameTv, LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            ))
            row.addView(headerRow)

            // Three copyable rows: pkg, app name, activity — each with a label
            val rowPkg = createCopyRow(getString(R.string.editor_floating_pkg), entry.pkg, pkgColor)
            val rowName = createCopyRow(getString(R.string.editor_floating_app_name), entry.appName.ifEmpty { entry.pkg }, nameColor)
            val rowAct = createCopyRow(getString(R.string.editor_floating_activity), entry.activity.ifEmpty { "—" }, pkgColor)
            row.addView(rowPkg)
            row.addView(rowName)
            row.addView(rowAct)

            // Separator between entries
            if (entry != history.first()) {
                val sep = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
                    ).apply {
                        topMargin = dp(3)
                    }
                    setBackgroundColor(sepColor)
                }
                row.addView(sep)
            }

            container.addView(row)
        }
    }

    // ── Foreground notification ───────────────────────────────────────

    private fun startForegroundNotification() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.editor_floating_title),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.editor_floating_identify_summary)
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)

        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, com.ianzb.hypernavbar.MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.editor_floating_notification_title))
            .setContentText(getString(R.string.editor_floating_notification_text))
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

    // ── Broadcast receiver ────────────────────────────────────────────

    private fun registerForegroundReceiver() {
        val filter = IntentFilter(AppIdentifyAccessibilityService.ACTION_FOREGROUND_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(foregroundReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(foregroundReceiver, filter)
        }
    }

    // ── Floating window creation ──────────────────────────────────────

    private fun createFloatingWindow() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val waiting = getString(R.string.editor_floating_waiting)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply {
                setColor(argb(0xE0, 0x1C, 0x1C, 0x1E))
                cornerRadius = dp(18).toFloat()
            }
        }

        // Title row with history toggle
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(6))
        }
        val titleText = TextView(this).apply {
            text = getString(R.string.editor_floating_title)
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
        }
        titleRow.addView(titleText, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ))

        val historyBtn = TextView(this).apply {
            text = getString(R.string.editor_floating_history)
            setTextColor(argb(0xFF, 0x00, 0x7A, 0xFF))
            textSize = 12f
            setPadding(dp(8), 0, 0, 0)
            isClickable = true
            isFocusable = true
            setOnClickListener { toggleHistory() }
        }
        this@FloatingIdentifyService.historyBtn = historyBtn
        titleRow.addView(historyBtn)
        layout.addView(titleRow)

        // Separator
        layout.addView(createSeparator(dp(6)))

        // --- Info container (current app info) ---
        infoContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        infoContainer!!.addView(createInfoRow(
            getString(R.string.editor_floating_pkg), waiting, Color.WHITE, 10f, 12f
        ) { tvPkgValue = it })
        infoContainer!!.addView(createInfoRow(
            getString(R.string.editor_floating_app_name), waiting, Color.WHITE, 10f, 12f
        ) { tvAppValue = it })
        infoContainer!!.addView(createInfoRow(
            getString(R.string.editor_floating_activity), waiting, Color.WHITE, 10f, 12f
        ) { tvActivityValue = it })
        layout.addView(infoContainer!!)

        // --- History container (hidden initially) ---
        historyContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(120)
            )
            isVerticalScrollBarEnabled = true
            visibility = View.GONE
            addView(historyContainer)
        }
        layout.addView(scrollView)

        // Separator
        layout.addView(createSeparator(dp(6)))

        // Button row: Quick Add | Close
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }

        val quickAddBtn = createMiuixButton(
            text = getString(R.string.editor_floating_add),
            bgColor = argb(0xFF, 0x00, 0x7A, 0xFF),
        ) { onQuickAdd() }
        buttonRow.addView(quickAddBtn, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ).apply { setMargins(0, 0, dp(8), 0) })

        val closeBtn = createMiuixButton(
            text = getString(R.string.editor_floating_close),
            bgColor = argb(0xFF, 0x58, 0x58, 0x5A),
        ) { stopSelf() }
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

    // ── Toggle between info vs. history ───────────────────────────────

    private fun toggleHistory() {
        showingHistory = !showingHistory
        historyBtn?.text = getString(
            if (showingHistory) R.string.editor_floating_back_to_current
            else R.string.editor_floating_history
        )
        infoContainer?.visibility = if (showingHistory) View.GONE else View.VISIBLE
        historyContainer?.let { hc ->
            val scrollParent = hc.parent as? ViewGroup
            val vis = if (showingHistory) View.VISIBLE else View.GONE
            scrollParent?.visibility = vis
            hc.visibility = vis
        }
        refreshHistoryView()
    }

    // ── Miuix-style button ────────────────────────────────────────────

    private fun createMiuixButton(
        text: String,
        bgColor: Int,
        onClick: () -> Unit,
    ): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = dp(18).toFloat()
            }
            setOnClickListener { onClick() }
        }
    }

    // ── Helper views ──────────────────────────────────────────────────

    private fun createSeparator(marginDp: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
            ).apply {
                setMargins(0, marginDp, 0, marginDp)
            }
            setBackgroundColor(argb(0x33, 0xFF, 0xFF, 0xFF))
        }
    }

    private fun createInfoRow(
        label: String, valueText: String, valueColor: Int,
        labelSize: Float, valueSize: Float,
        valueRef: (TextView) -> Unit,
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(3), 0, dp(3))
        }
        val labelView = TextView(this).apply {
            text = label
            setTextColor(argb(0xFF, 0x99, 0x99, 0x99))
            textSize = labelSize
            setPadding(0, 0, 0, dp(2))
        }
        row.addView(labelView)
        val valueView = TextView(this).apply {
            text = valueText
            setTextColor(valueColor)
            textSize = valueSize
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setOnClickListener { onCopyValue(text.toString()) }
        }
        valueRef(valueView)
        row.addView(valueView)
        return row
    }

    private fun createCopyRow(label: String, value: String, color: Int): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(1), 0, dp(1))
        }
        val labelTv = TextView(this).apply {
            text = label
            setTextColor(argb(0xFF, 0x77, 0x77, 0x77))
            textSize = 10f
            setPadding(0, 0, dp(6), 0)
        }
        row.addView(labelTv)
        val valueTv = TextView(this).apply {
            text = value
            setTextColor(color)
            textSize = 11f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            isClickable = true
            isFocusable = true
            setOnClickListener { onCopyValue(value) }
        }
        row.addView(valueTv, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ))
        return row
    }

    // ── Display update ────────────────────────────────────────────────

    private fun updateDisplayedInfo() {
        val waiting = getString(R.string.editor_floating_waiting)
        tvPkgValue?.text = currentPkg.ifEmpty { waiting }
        tvAppValue?.text = currentApp.ifEmpty { waiting }
        tvActivityValue?.text = currentActivity.ifEmpty { waiting }
    }

    // ── Copy to clipboard ─────────────────────────────────────────────

    private fun onCopyValue(value: String) {
        if (value.isEmpty() || value == getString(R.string.editor_floating_waiting) || value == "—") return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("app_info", value))
        Toast.makeText(applicationContext, getString(R.string.editor_copied_toast), Toast.LENGTH_SHORT).show()
    }

    // ── Quick Add ────────────────────────────────────────────────────

    private fun onQuickAdd() {
        if (currentPkg.isEmpty()) {
            Toast.makeText(applicationContext, getString(R.string.editor_no_app_detected), Toast.LENGTH_SHORT).show()
            return
        }
        // Direct callback (same process, no timing issues)
        pendingQuickAdd?.invoke(currentPkg, currentApp, currentActivity)
        // Also send broadcast for any registered receivers
        val bcIntent = Intent(ACTION_QUICK_ADD).apply {
            putExtra(EXTRA_PACKAGE_NAME, currentPkg)
            putExtra(EXTRA_APP_NAME, currentApp)
            putExtra(EXTRA_ACTIVITY_NAME, currentActivity)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            sendBroadcast(bcIntent, null)
        } else {
            @Suppress("DEPRECATION")
            sendBroadcast(bcIntent)
        }
        // Bring app to foreground
        val launchIntent = Intent(this, com.ianzb.hypernavbar.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(launchIntent)
        Toast.makeText(applicationContext, getString(R.string.editor_sent_to_editor), Toast.LENGTH_SHORT).show()
    }

    // ── Drag handling ─────────────────────────────────────────────────

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
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY
                if (!isDragging && (Math.abs(dx) > dragThreshold || Math.abs(dy) > dragThreshold)) {
                    isDragging = true
                }
                if (isDragging) {
                    val params = view.layoutParams as WindowManager.LayoutParams
                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()
                    windowManager?.updateViewLayout(view, params)
                    return true
                }
            }
            MotionEvent.ACTION_UP -> { if (isDragging) return true }
        }
        return false
    }

    // ── Cleanup ───────────────────────────────────────────────────────

    private fun removeFloatingWindow() {
        try {
            if (floatingView != null) {
                windowManager?.removeView(floatingView)
                floatingView = null
            }
        } catch (_: Exception) { }
    }

    // ── Utility ───────────────────────────────────────────────────────

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private fun argb(a: Int, r: Int, g: Int, b: Int): Int = (a shl 24) or (r shl 16) or (g shl 8) or b
}
