package com.example.safetext

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var inputText: EditText
    private lateinit var analyzeButton: Button
    private lateinit var outputText: TextView
    private lateinit var enableNotificationsButton: Button
    private lateinit var modelSwitch: SwitchMaterial

    // History card
    private lateinit var lastNotiCard: View
    private lateinit var lastNotiResultText: TextView
    private lateinit var lastNotiTimeText: TextView
    private lateinit var lastNotiContentText: TextView

    // Engines
    private lateinit var bertEngine: InferenceEngine
    private lateinit var tfliteEngine: TFLiteTextClassifier


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind Views
        inputText = findViewById(R.id.inputText)
        analyzeButton = findViewById(R.id.analyzeButton)
        outputText = findViewById(R.id.outputText)
        enableNotificationsButton = findViewById(R.id.enableNotificationsButton)
        modelSwitch = findViewById(R.id.modelSwitch)

        lastNotiCard = findViewById(R.id.lastNotiCard)
        lastNotiResultText = findViewById(R.id.lastNotiResultText)
        lastNotiTimeText = findViewById(R.id.lastNotiTimeText)
        lastNotiContentText = findViewById(R.id.lastNotiContentText)

        // Instantiate engines
        bertEngine = InferenceEngine(this)
        tfliteEngine = TFLiteTextClassifier(this)

        // Load saved preference for switch
        val isCnnSelected = PreferenceManager.isCnnModelSelected(this)
        modelSwitch.isChecked = isCnnSelected

        modelSwitch.setOnCheckedChangeListener { _, isChecked ->
            PreferenceManager.saveUseCnnModel(this, isChecked)
            outputText.text = ""
        }


        // Analyze button logic
        analyzeButton.setOnClickListener {
            val userInput = inputText.text.toString().trim()
            if (userInput.isEmpty()) {
                inputText.error = "Please enter text"
                return@setOnClickListener
            }

            analyzeButton.isEnabled = false
            outputText.text = "Loading model and analyzing..."
            outputText.setTextColor(getColor(android.R.color.darker_gray))

            lifecycleScope.launch {
                var probability = 0.0f
                var modelUsedName = ""

                try {
                    if (modelSwitch.isChecked) {
                        // ---- TFLITE CNN MODEL ----
                        modelUsedName = "TFLite CNN"

                        // Ensure model initialized ONCE
                        if (!tfliteEngine.isInitialized) {
                            withContext(Dispatchers.IO) { tfliteEngine.initialize() }
                        }

                        probability = withContext(Dispatchers.IO) {
                            tfliteEngine.predict(userInput)
                        }

                    } else {
                        // ---- BERT ONNX MODEL ----
                        modelUsedName = "BERT ONNX"

                        withContext(Dispatchers.IO) { bertEngine.initialize() }


                        probability = withContext(Dispatchers.IO) {
                            bertEngine.classify(userInput)
                        }
                    }

                    displayManualResult(probability, modelUsedName)

                } catch (e: Exception) {
                    outputText.text = "Error: ${e.message}"
                    outputText.setTextColor(getColor(android.R.color.holo_red_dark))
                    e.printStackTrace()

                } finally {
                    analyzeButton.isEnabled = true
                }
            }
        }


        // Notification listener settings button
        enableNotificationsButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }


    private fun displayManualResult(probSafe: Float, modelName: String) {

        var isUnsafe: Boolean
        var confidence: Float

        val probUnsafe = 1f - probSafe

        if (modelName == "BERT ONNX") {
            // BERT output = probSafe
            isUnsafe = probUnsafe >= 0.5f
            confidence = if (isUnsafe) probUnsafe else probSafe
        } else {
            // TFLite output = probUnsafe already
            isUnsafe = probUnsafe <= 0.4f
            confidence = if (isUnsafe) (1f - probUnsafe) else probUnsafe
        }

        val label = if (isUnsafe) "Unsafe" else "Safe"
        val confidenceStr = "%.1f%%".format(confidence * 100)

        outputText.text = "Result: $label\nConfidence: $confidenceStr\nModel: $modelName"

        outputText.setTextColor(
            if (isUnsafe)
                getColor(android.R.color.holo_red_dark)
            else
                getColor(android.R.color.black)
        )
    }




    override fun onResume() {
        super.onResume()
        updateLastNotificationUI()
    }


    private fun updateLastNotificationUI() {
        val data = PreferenceManager.getLastNotification(this)
        if (data == null) {
            lastNotiCard.visibility = View.GONE
            return
        }

        lastNotiCard.visibility = View.VISIBLE
        val isUnsafe = data.probability <= 0.5f
        val label = if (isUnsafe) "Unsafe" else "Safe"

        lastNotiResultText.text = label
        lastNotiContentText.text = data.text
        lastNotiTimeText.text = PreferenceManager.getFormattedTime(data.timestampMs)

        if (isUnsafe) {
            lastNotiResultText.setTextColor(getColor(android.R.color.holo_red_dark))
        } else {
            lastNotiResultText.setTextColor(getColor(android.R.color.black))
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        tfliteEngine.close() // avoids interpreter leaks
    }
}
