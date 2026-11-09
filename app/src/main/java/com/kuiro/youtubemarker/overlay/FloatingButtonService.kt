package com.kuiro.youtubemarker.overlay

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.*
import android.widget.Button
import android.widget.Toast
import kotlin.math.abs

class FloatingButtonService : Service() {
    private lateinit var manager: WindowManager
    private var button: Button? = null
    companion object { var running = false; private set }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_OVERLAY") { stopSelf(); return START_NOT_STICKY }
        if (button != null) return START_NOT_STICKY
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
        try {
            val notification = ServiceNotification.create(this, "플로팅 메모 버튼 사용 중", Intent(this, javaClass).setAction("STOP_OVERLAY"))
            if (Build.VERSION.SDK_INT >= 34) startForeground(101, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(101, notification)
            manager = getSystemService(WindowManager::class.java)
            val prefs = getSharedPreferences("overlay", MODE_PRIVATE)
            val params = WindowManager.LayoutParams(dp(64), dp(64), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.START
                x = prefs.getInt("x", 0); y = prefs.getInt("y", dp(160))
            }
            clamp(params)
            val view = Button(this).apply { text = "메모"; contentDescription = "음성 메모 창 열기" }
            view.setOnClickListener {
                runCatching { startActivity(Intent(this, CapturePopupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { Toast.makeText(this, "앱을 열어 녹음해 주세요.", Toast.LENGTH_LONG).show() }
            }
            var downX = 0f; var downY = 0f; var originX = 0; var originY = 0; var moved = false
            view.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; originX = params.x; originY = params.y; moved = false; true }
                    MotionEvent.ACTION_MOVE -> {
                        if (abs(event.rawX - downX) + abs(event.rawY - downY) > ViewConfiguration.get(this).scaledTouchSlop) moved = true
                        if (moved) {
                            params.x = originX + (event.rawX - downX).toInt(); params.y = originY + (event.rawY - downY).toInt(); clamp(params)
                            runCatching { manager.updateViewLayout(v, params) }
                        }; true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) v.performClick()
                        else prefs.edit().putInt("x", params.x).putInt("y", params.y).apply()
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> true
                    else -> false
                }
            }
            manager.addView(view, params); button = view; running = true
        } catch (_: Exception) { Toast.makeText(this, "플로팅 표시 실패 · 권한을 확인하세요.", Toast.LENGTH_LONG).show(); stopSelf() }
        return START_NOT_STICKY
    }
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
    private fun clamp(params: WindowManager.LayoutParams) {
        params.x = params.x.coerceIn(0, (resources.displayMetrics.widthPixels - dp(64)).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (resources.displayMetrics.heightPixels - dp(100)).coerceAtLeast(0))
    }
    override fun onDestroy() {
        button?.let { runCatching { manager.removeView(it) } }; button = null; running = false
        super.onDestroy()
    }
}
