package com.example.safetext

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.example.safetext.app.loadVocab

class MainActivity : AppCompatActivity() {

    private lateinit var tokenizer: BertTokenizer
    private lateinit var model: SafeTextModel
    private lateinit var enableNotificationListenerButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. UI components
        val inputText = findViewById<EditText>(R.id.inputText)
        val analyzeButton = findViewById<Button>(R.id.analyzeButton)
        val outputText = findViewById<TextView>(R.id.outputText)
        enableNotificationListenerButton = findViewById(R.id.enableNotificationsButton) // Find the new button

        // 2. Load tokenizer + model
        tokenizer = BertTokenizer(
            vocab = loadVocab(this, "vocab.txt"),
            maxLength = 128
        )
        model = SafeTextModel(this)

        // 3. Analyze Button Listener (Existing)
        analyzeButton.setOnClickListener {
            val userInput = inputText.text.toString().trim()
            if (userInput.isNotEmpty()) {
                val result = analyzeText(userInput)
                outputText.text = result
            } else {
                outputText.text = "Please enter text."
            }
        }

        // 4. Enable Service Button Listener (NEW)
        // This button takes the user directly to the Notification Access settings screen
        enableNotificationListenerButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        // 5. Initial Check (Optional: you can keep the dialog if you want)
        if (!isNotificationServiceEnabled()) {
            buildNotificationServiceAlertDialog()
        }
    }

    /**
     * onResume is called when the user returns to the app (e.g., coming back from Settings).
     * We check permission status here to update the UI.
     */
    override fun onResume() {
        super.onResume()
        updateServiceButtonState()
    }

    private fun updateServiceButtonState() {
        if (isNotificationServiceEnabled()) {
            enableNotificationListenerButton.text = "Notification Access Granted ✅"
            enableNotificationListenerButton.isEnabled = false
        } else {
            enableNotificationListenerButton.text = "Enable Notification Access ⚠️"
            enableNotificationListenerButton.isEnabled = true
        }
    }

    private fun analyzeText(text: String): String {
        // (Your existing implementation)
        val encoded = tokenizer.encode(text)
        val probability = model.predict(encoded.inputIds, encoded.attentionMask)
        val label = if (probability > 0.5f) "Safe" else "Unsafe"
        if (label == "Unsafe")
            return "Result: $label\nConfidence: ${"%.3f".format(1 - probability)}"
        else
            return "Result: $label\nConfidence: ${"%.3f".format(probability)}"
    }

    // ... keep your existing isNotificationServiceEnabled and buildNotificationServiceAlertDialog functions ...
    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (!TextUtils.isEmpty(flat)) {
            val names = flat.split(":").toTypedArray()
            for (name in names) {
                val cn = ComponentName.unflattenFromString(name)
                if (cn != null) {
                    if (TextUtils.equals(pkgName, cn.packageName)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    /**
     * Builds an alert dialog asking the user to grant permission.
     */
    private fun buildNotificationServiceAlertDialog() {
        val alertDialogBuilder = AlertDialog.Builder(this)
        alertDialogBuilder.setTitle("Notification Permission Needed")
        alertDialogBuilder.setMessage("For the app to classify incoming notifications, please grant notification access in Settings.")
        alertDialogBuilder.setPositiveButton("Go to Settings") { dialog, id ->
            // Intent to open the specific settings screen
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        alertDialogBuilder.setNegativeButton("Cancel") { dialog, id ->
            // If you want to close the app if they decline:
            // finish()
        }
        val alert = alertDialogBuilder.create()
        alert.show()
    }
}