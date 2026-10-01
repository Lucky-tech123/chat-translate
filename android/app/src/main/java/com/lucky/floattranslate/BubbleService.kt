package com.lucky.floattranslate

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.hypot

class BubbleService : Service() {

    companion object {
        const val ACTION_STOP = "com.lucky.floattranslate.STOP"
        const val ACTION_REFRESH = "com.lucky.floattranslate.REFRESH"
        const val ACTION_OPEN = "com.lucky.floattranslate.OPEN"
        private const val CHANNEL = "bubble"
        private const val NOTIF_ID = 7

        @Volatile var running = false

        fun start(ctx: Context, action: String? = null) {
            val i = Intent(ctx, BubbleService::class.java).setAction(action)
            ctx.startForegroundService(i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, BubbleService::class.java))
        }
    }

    private lateinit var wm: WindowManager
    private lateinit var prefs: Prefs
    private lateinit var ui: Context
    private var bubble: FrameLayout? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var trash: FrameLayout? = null
    private var trashParams: WindowManager.LayoutParams? = null
    private var sheet: TranslatorSheet? = null
    private var snapAnim: ValueAnimator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        ui = ContextThemeWrapper(this, R.style.AppTheme)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startAsForeground()
        running = true
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        addBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
        when (intent?.action) {
            ACTION_STOP -> { prefs.bubbleEnabled = false; stopSelf(); return START_NOT_STICKY }
            ACTION_REFRESH -> { removeBubble(); if (sheet == null) addBubble() }
            ACTION_OPEN -> openSheet()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        snapAnim?.cancel()
        sheet?.dismissNow()
        sheet = null
        removeTrash()
        removeBubble()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        sheet?.onConfigChanged()
        bubble?.post { placeFromPrefs() }
    }

    // ---------- foreground notification ----------

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Floating bubble", NotificationManager.IMPORTANCE_MIN).apply {
            setShowBadge(false)
        })
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, BubbleService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_sort_alphabetically)
            .setContentTitle("Float Translate is ready")
            .setContentText("Tap the bubble to translate")
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Stop bubble", stop).build())
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    // ---------- bubble ----------

    private val pad get() = dp(8)
    private val winSize get() = dp(prefs.bubbleSizeDp) + pad * 2
    private val screenW get() = resources.displayMetrics.widthPixels
    private val screenH get() = resources.displayMetrics.heightPixels

    private fun addBubble() {
        if (bubble != null) return
        val size = dp(prefs.bubbleSizeDp)
        val container = FrameLayout(ui).apply { setPadding(pad, pad, pad, pad); clipToPadding = false }
        val face = TextView(ui).apply {
            text = "文A"
            gravity = Gravity.CENTER
            setTextColor(Pal.TEXT)
            typeface = MEDIUM
            textSize = prefs.bubbleSizeDp * 0.28f
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Pal.ACCENT)
                setStroke(dp(2), 0x33FFFFFF)
            }
            elevation = dpf(5)
            contentDescription = "Open translator"
        }
        container.addView(face, FrameLayout.LayoutParams(size, size))
        container.alpha = 0.92f
        val p = WindowManager.LayoutParams(
            winSize, winSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        bubbleParams = p
        bubble = container
        placeFromPrefs(apply = false)
        container.setOnTouchListener(DragListener())
        try { wm.addView(container, p) } catch (e: Exception) { bubble = null; stopSelf() }
    }

    private fun removeBubble() {
        snapAnim?.cancel()
        bubble?.let { try { wm.removeViewImmediate(it) } catch (_: Exception) {} }
        bubble = null
    }

    private fun minY() = dp(56)
    private fun maxY() = screenH - winSize - dp(96)
    private fun edgeX(side: String) = if (side == "Left") 0 else screenW - winSize

    private fun placeFromPrefs(apply: Boolean = true) {
        val p = bubbleParams ?: return
        p.x = edgeX(prefs.bubbleSide)
        p.y = (minY() + prefs.bubbleY * (maxY() - minY())).toInt().coerceIn(minY(), maxOf(minY(), maxY()))
        if (apply) bubble?.let { try { wm.updateViewLayout(it, p) } catch (_: Exception) {} }
    }

    private inner class DragListener : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var moved = false
        private val slop = ViewConfiguration.get(this@BubbleService).scaledTouchSlop

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            val p = bubbleParams ?: return false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    snapAnim?.cancel()
                    downX = e.rawX; downY = e.rawY; startX = p.x; startY = p.y; moved = false
                    v.alpha = 1f
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!moved && hypot(dx, dy) > slop) { moved = true; showTrash() }
                    if (moved) {
                        p.x = (startX + dx).toInt().coerceIn(0, screenW - winSize)
                        p.y = (startY + dy).toInt().coerceIn(0, screenH - winSize)
                        try { wm.updateViewLayout(v, p) } catch (_: Exception) {}
                        highlightTrash(overTrash())
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.alpha = 0.92f
                    if (!moved) {
                        if (e.actionMasked == MotionEvent.ACTION_UP) openSheet()
                    } else {
                        val drop = overTrash()
                        removeTrash()
                        if (drop) { prefs.bubbleEnabled = false; stopSelf() } else snapToEdge()
                    }
                }
            }
            return true
        }
    }

    private fun snapToEdge() {
        val p = bubbleParams ?: return
        val side = if (p.x + winSize / 2 < screenW / 2) "Left" else "Right"
        val targetY = p.y.coerceIn(minY(), maxOf(minY(), maxY()))
        prefs.bubbleSide = side
        val range = (maxY() - minY()).coerceAtLeast(1)
        prefs.bubbleY = ((targetY - minY()).toFloat() / range).coerceIn(0f, 1f)
        val fromX = p.x; val fromY = p.y; val toX = edgeX(side)
        snapAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val b = bubble ?: return@addUpdateListener
                val f = it.animatedValue as Float
                p.x = (fromX + (toX - fromX) * f).toInt()
                p.y = (fromY + (targetY - fromY) * f).toInt()
                try { wm.updateViewLayout(b, p) } catch (_: Exception) { cancel() }
            }
            start()
        }
    }

    // ---------- remove target ----------

    private val trashSize get() = dp(60)

    private fun showTrash() {
        if (trash != null) return
        val t = FrameLayout(ui)
        val circle = TextView(ui).apply {
            text = "✕"
            gravity = Gravity.CENTER
            textSize = 18f
            setTextColor(Pal.TEXT)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xCC2A2A33.toInt()); setStroke(dp(1), 0x33FFFFFF) }
        }
        t.addView(circle, FrameLayout.LayoutParams(trashSize, trashSize))
        val p = WindowManager.LayoutParams(
            trashSize, trashSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; y = dp(56) }
        trash = t; trashParams = p
        try { wm.addView(t, p) } catch (_: Exception) { trash = null }
    }

    private fun overTrash(): Boolean {
        val p = bubbleParams ?: return false
        if (trash == null) return false
        val bx = p.x + winSize / 2f
        val by = p.y + winSize / 2f
        val tx = screenW / 2f
        val ty = screenH - dp(56) - trashSize / 2f
        return abs(bx - tx) < dp(64) && abs(by - ty) < dp(80)
    }

    private fun highlightTrash(on: Boolean) {
        val c = trash?.getChildAt(0) ?: return
        val s = if (on) 1.25f else 1f
        if (c.scaleX != s) c.animate().scaleX(s).scaleY(s).setDuration(100).start()
    }

    private fun removeTrash() {
        trash?.let { try { wm.removeViewImmediate(it) } catch (_: Exception) {} }
        trash = null
    }

    // ---------- sheet ----------

    private fun openSheet() {
        if (sheet != null) return
        bubble?.visibility = View.GONE
        sheet = TranslatorSheet(ui, wm) {
            sheet = null
            if (bubble == null) { if (prefs.bubbleEnabled) addBubble() } else bubble?.visibility = View.VISIBLE
        }
        sheet?.show()
    }
}
