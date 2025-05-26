package com.halufun.dynamic_island

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Display // For API P, Q
import android.view.DisplayCutout
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowInsets // For API R+
import android.view.WindowManager
import androidx.annotation.RequiresApi
// Removed @RequiresApi here as onCreate needs to run on all supported versions
import androidx.core.app.NotificationCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat // For parsing insets

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var islandView: View
    private var isViewAdded = false // Flag to track if the view is added

    // Removed @RequiresApi(Build.VERSION_CODES.R) from onCreate
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        islandView = LayoutInflater.from(this).inflate(R.layout.view_island, null)

        val layoutFlag: Int =
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.y = getStatusBarAndCutoutHeight() // This now handles all API levels
        Log.d("OverlayService", "Calculated Y offset: ${params.y}")

        try {
            if (!isViewAdded) { // Use a simple flag
                windowManager.addView(islandView, params)
                isViewAdded = true
            }
            // The updateViewLayout logic might be needed if params change dynamically later
            // else if (isViewAdded) {
            //     windowManager.updateViewLayout(islandView, params)
            // }
        } catch (e: Exception) {
            Log.e("OverlayService", "Error adding/updating view to WindowManager", e)
            stopSelf()
            return
        }

        // Start Foreground Service
        val notification = createNotification()
        // Foreground service itself requires API 26+
        var serviceType = 0 // Default, will be used if API < Q or if a specific type isn't set for Q+

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // API 34+
            serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            Log.i("OverlayService", "Using FOREGROUND_SERVICE_TYPE_SPECIAL_USE for API ${Build.VERSION.SDK_INT}")
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33
            serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
            Log.i("OverlayService", "Using FOREGROUND_SERVICE_TYPE_MANIFEST for API ${Build.VERSION.SDK_INT}. Ensure manifest is configured.")
        } else // API 29-32 (Q, R, S, S_V2)
            // CRITICAL: Developer must choose a specific FGS type here.
            serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC // DEVELOPER: VERIFY THIS!
            Log.w("OverlayService", "API ${Build.VERSION.SDK_INT}: Using placeholder FGS type DATA_SYNC. Developer must verify.")
        // Else for API O, O_MR1, P (26,27,28), serviceType remains 0, which is fine as startForeground doesn't take type.

        // API 29+ require type in startForeground
        Log.d("OverlayService", "Starting foreground service with type: $serviceType for API ${Build.VERSION.SDK_INT}")
        startForeground(1, notification, serviceType)
        // For pre-Oreo (API < 26), startForeground is not available.
        // If you need to support pre-Oreo, the service would just run as a regular service.
        // However, overlays without foreground service are highly likely to be killed.
        // It's generally recommended to target API 26+ for reliable overlay services.
    }

    // Removed @RequiresApi(Build.VERSION_CODES.R)
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun getStatusBarAndCutoutHeight(): Int {
        var totalTopInset = 0

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // API 30+ (Android 11+)
            val windowMetrics = windowManager.currentWindowMetrics
            val insets = windowMetrics.windowInsets
            val displayCutout = insets.displayCutout // This is android.view.DisplayCutout
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars()) // Use WindowInsetsCompat for type

            totalTopInset = displayCutout?.safeInsetTop ?: statusBars.top // Prefer cutout's safe inset if available
            Log.d("OverlayService", "API 30+: totalTopInset: $totalTopInset (Cutout: ${displayCutout?.safeInsetTop}, SB: ${statusBars.top})")

        } else { // API 28, 29 (Android 9, 10)
            // For P and Q, we need to access the display from the view or windowManager.
            // Since islandView might not be attached yet when this is first called,
            // it's safer to get the default display from windowManager.
            val display: Display? = windowManager.defaultDisplay

            var cutoutInset = 0
            if (display != null) {
                val displayCutoutObj: DisplayCutout? = display.cutout
                if (displayCutoutObj != null) {
                    cutoutInset = displayCutoutObj.safeInsetTop
                }
            }

            val statusBarHeight = ViewCompat.getRootWindowInsets(View(this))?.stableInsets?.top ?: 0




            totalTopInset =
                if (cutoutInset > 0) cutoutInset else statusBarHeight // Prioritize cutout inset
            Log.d(
                "OverlayService",
                "API 28/29: totalTopInset: $totalTopInset (Cutout: $cutoutInset, SB Res: $statusBarHeight)"
            )
        }
        return totalTopInset
    }


    private fun createNotification(): Notification {
        val channelId = "island_channel_01" // Ensure channel ID is unique if you have others
        val channelName = "Dynamic Island Service" // More descriptive

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            chan.description = "Keeps the Dynamic Island feature active."
            chan.setShowBadge(false)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager // Use Context.NOTIFICATION_SERVICE
            manager.createNotificationChannel(chan)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.app_name) + " is Active") // Use string resource
            .setContentText("Tap for more information or to stop.") // Example content text
            .setSmallIcon(R.drawable.ic_launcher_foreground) // IMPORTANT: Replace with a proper small icon (silhouette style)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            // .setContentIntent(pendingIntent) // Optional: Add an intent to open your app
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isViewAdded) { // Check flag before trying to remove
            try {
                windowManager.removeView(islandView)
                isViewAdded = false // Update flag
            } catch (e: Exception) {
                Log.e("OverlayService", "Error removing view from WindowManager", e)
            }
        }
        Log.d("OverlayService", "Service destroyed.")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}