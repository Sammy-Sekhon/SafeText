package com.example.safetext

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import com.example.safetext.PreferenceManager

class SafeTextNotificationListener : NotificationListenerService() {

    private val TAG = "SafeTextListener"
    private val CHANNEL_ID = "SafeTextAlertChannel"
    private val NOTIFICATION_ID = 1001

    // Create a CoroutineScope tied to the service's lifecycle
    private val serviceJob = Job()
    // We define the scope on the Main thread, but will switch to IO for heavy lifting
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private lateinit var inferenceEngine: InferenceEngine
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        inferenceEngine = InferenceEngine(this)

        // Pre-initialize the model in the background when the service starts
        serviceScope.launch {
            Log.d(TAG, "Initializing model in background...")
            // The engine internally handles checking if it's already initialized
            inferenceEngine.initialize()
            Log.d(TAG, "Model initialized and ready.")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancel any running jobs when the service is destroyed to prevent leaks
        serviceJob.cancel()
        Log.d(TAG, "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "Service bound")
        return super.onBind(intent)
    }

    // This function is called whenever a new notification is posted by any app
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        // Crucial: Don't analyze notifications from our own app, or we enter an infinite loop!
        if (sbn.packageName == this.packageName) return
        if (sbn.isOngoing) return

        // Extract text content
        val extras = sbn.notification.extras
        val titleStr = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val textStr = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val combinedText = "$titleStr $textStr".trim()

        if (combinedText.isEmpty()) return

        // Run classification asynchronously off the main thread
        serviceScope.launch {
            try {
                // Ensure initialization is complete before running (it returns immediately if done)
                inferenceEngine.initialize()

                // Switch to IO thread for inference
                val probability = withContext(Dispatchers.IO) {
                    inferenceEngine.classify(combinedText)
                }

                Log.d(TAG, "Saving result to preferences: $combinedText")
                PreferenceManager.saveLastNotification(applicationContext, combinedText, probability)

                val isSafe = probability > 0.5f

                if (!isSafe) {
                    Log.w(TAG, "UNSAFE message detected from ${sbn.packageName}. Confidence: ${"%.3f".format(1 - probability)}")

                    // 1. Issue our own local notification alerting the user
                    showUnsafeAlertNotification(sbn.packageName)

                    // 2. "Block" the original notification.
                    // NOTE: This removes it from the shade, but usually cannot prevent the initial
                    // heads-up pop-up banner due to OS limitations.
                    cancelNotification(sbn.key)
                } else {
                    Log.i(TAG, "Safe message detected from ${sbn.packageName}.")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error during classification", e)
            }
        }
    }

    /**
     * Helper function to build and show our own alert notification.
     */
    private fun showUnsafeAlertNotification(originApp: String) {
        // Create an Intent that opens the MainActivity if they click our alert
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        // Create the PendingIntent (immutable required for modern Android)
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            // Use a standard icon for now (make sure ic_launcher_foreground exists or change this)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("SafeText Alert")
            .setContentText("Unsafe content blocked from: $originApp")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true) // Remove our notification when clicked
        Log.d(TAG, "Attempting to post notification to manager now...")

        // Issue the notification
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    /**
     * Setup required for Android 8.0+ (Oreo) to show notifications.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "SafeText Alerts"
            val descriptionText = "Notifications when unsafe text is detected"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}
}