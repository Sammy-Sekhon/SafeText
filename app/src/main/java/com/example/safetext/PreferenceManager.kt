package com.example.safetext


import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A simple data class to hold the retrieved data together.
 */
data class LastNotificationData(
    val text: String,
    val probability: Float,
    val timestampMs: Long
)

/**
 * Helper object to handle saving and loading the last notification from SharedPreferences.
 */
object PreferenceManager {

    private const val PREF_NAME = "SafeTextPrefs"
    private const val KEY_LAST_TEXT = "last_noti_text"
    private const val KEY_LAST_PROB = "last_noti_prob"
    private const val KEY_LAST_TIME = "last_noti_time"

    private const val KEY_USE_CNN_MODEL = "use_cnn_model"

    /**
     * Saves notification data persistently.
     */
    fun saveLastNotification(context: Context, text: String, probability: Float) {
        // Get the preference file (private mode so other apps can't read it)
        val sharedPref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        with(sharedPref.edit()) {
            putString(KEY_LAST_TEXT, text)
            putFloat(KEY_LAST_PROB, probability)
            putLong(KEY_LAST_TIME, System.currentTimeMillis())
            apply() // apply() writes data asynchronously off the main thread
        }
    }

    /**
     * Retrieves the data. Returns null if nothing has been saved yet.
     */
    fun getLastNotification(context: Context): LastNotificationData? {
        val sharedPref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        // Try to get the text string. If it's missing, return default value (null).
        val text = sharedPref.getString(KEY_LAST_TEXT, null) ?: return null
        // Get other values (defaults won't really be used since text check handles it)
        val prob = sharedPref.getFloat(KEY_LAST_PROB, 0f)
        val time = sharedPref.getLong(KEY_LAST_TIME, 0L)

        return LastNotificationData(text, prob, time)
    }

    /**
     * Helper to format the timestamp into a readable string (e.g., "10:30 AM")
     */
    fun getFormattedTime(timestampMs: Long): String {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestampMs))
    }

    /**
     * Saves the model selection choice. true = CNN, false = BERT.
     */
    fun saveUseCnnModel(context: Context, useCnn: Boolean) {
        val sharedPref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean(KEY_USE_CNN_MODEL, useCnn)
            apply()
        }
    }

    /**
     * Checks if CNN model is selected. Defaults to false (BERT) if never set.
     */
    fun isCnnModelSelected(context: Context): Boolean {
        val sharedPref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return sharedPref.getBoolean(KEY_USE_CNN_MODEL, false)
    }
}