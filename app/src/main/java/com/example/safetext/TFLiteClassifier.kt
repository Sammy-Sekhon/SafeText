package com.example.safetext

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.json.JSONObject
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class TFLiteTextClassifier(private val context: Context) {

    private var interpreter: Interpreter? = null

    private val MODEL_FILE = "SafeText_CNN_LSTM_int32.tflite"
    private val TOKENIZER_FILE = "android_tokenizer.json"

    private lateinit var wordIndex: Map<String, Int>
    private val maxLen = 100

    var isInitialized = false
        private set

    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext

        // load tokenizer
        wordIndex = loadTokenizer()

        // load model
        val model = loadModelFile()
        interpreter = Interpreter(model)

        isInitialized = true
    }

    /** Run inference */
    fun predict(text: String): Float {
        if (!isInitialized) return 0f

        // Tokenize & pad
        val sequenceInt = textToSequence(text) // IntArray

        // Convert IntArray → FloatArray
        val sequenceFloat = sequenceInt.map { it.toFloat() }.toFloatArray()

        // Wrap batch dimension
        val inputArray: Array<FloatArray> = arrayOf(sequenceFloat)  // shape [1, maxLen]

        // Output buffer
        val output = Array(1) { FloatArray(1) }

        // Run inference
        interpreter?.run(inputArray, output)

        return output[0][0]
    }



    /** Tokenization: lowercase → split → map to int → pad */
    private fun textToSequence(text: String): IntArray {
        val tokens = text.lowercase().split(" ")

        val seq = tokens.map { token ->
            wordIndex[token] ?: 2     // 2 = OOV token
        }.take(maxLen).toMutableList()

        // pad with zeros
        while (seq.size < maxLen) seq.add(0)

        return seq.toIntArray()
    }

    /** Loads tokenizer.json from assets */
    private fun loadTokenizer(): Map<String, Int> {
        val jsonText = context.assets.open(TOKENIZER_FILE).bufferedReader().use { it.readText() }
        val root = JSONObject(jsonText)

        // 1. Get the config object
        val config = root.getJSONObject("config")

        // 2. CRITICAL FIX: Get 'word_index' as a STRING, not an Object
        val wordIndexString = config.getString("word_index")

        // 3. Now parse that string into a new JSONObject
        val json = JSONObject(wordIndexString)

        val map = mutableMapOf<String, Int>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = json.getInt(key)
        }

        return map
    }


    /** Load TFLite model from assets */
    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(MODEL_FILE)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }

    fun close() {
        interpreter?.close()
    }
}
