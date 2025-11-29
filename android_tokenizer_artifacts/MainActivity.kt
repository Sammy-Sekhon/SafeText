package com.example.safetext

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.example.safetext.app.loadVocab
class MainActivity : AppCompatActivity() {

    private lateinit var tokenizer: BertTokenizer
    private lateinit var model: SafeTextModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Load tokenizer + model
        tokenizer = BertTokenizer(
            vocab = loadVocab(this, "vocab.txt"),
            maxLength = 128
        )

        model = SafeTextModel(this)

        // 2. UI components
        val inputText = findViewById<EditText>(R.id.inputText)
        val analyzeButton = findViewById<Button>(R.id.analyzeButton)
        val outputText = findViewById<TextView>(R.id.outputText)

        // 3. Button listener
        analyzeButton.setOnClickListener {
            val userInput = inputText.text.toString().trim()

            if (userInput.isNotEmpty()) {
                val result = analyzeText(userInput)
                outputText.text = result
            } else {
                outputText.text = "Please enter text."
            }
        }
    }

    private fun analyzeText(text: String): String {
        val encoded = tokenizer.encode(text)
        val probability = model.predict(encoded.inputIds, encoded.attentionMask)

        val label = if (probability > 0.5f) "Safe" else "Unsafe"
        if (label == "Unsafe")
            return "Result: $label\nConfidence: ${"%.3f".format(1 - probability)}"
        else
            return "Result: $label\nConfidence: ${"%.3f".format(probability)}"
    }
}
