package com.example.safetext

import ai.onnxruntime.*
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.safetext.app.loadVocab


class InferenceEngine(private val context: Context) {

    private val env = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null
    private var initialized = false

    private lateinit var tokenizer: BertTokenizer
    private lateinit var vocab: Map<String, Int>

    /**
     * Initializes tokenizer + ONNX model in a background thread.
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (initialized) return@withContext

        // Load vocab
        vocab = loadVocab(context, "vocab.txt")

        tokenizer = BertTokenizer(
            vocab = vocab,
            maxLength = 128
        )

        // Load ONNX model from assets
        val modelBytes = context.assets.open("distilbert_int8.onnx").readBytes()

        // Create ONNX session (heavy call)
        session = env.createSession(modelBytes)

        initialized = true
    }

    /**
     * Runs text classification.
     */
    fun classify(text: String): Float {
        if (!initialized) {
            throw IllegalStateException("InferenceEngine not initialized yet!")
        }

        val encoded = tokenizer.encode(text)

        // Convert Int → Long for ONNX
        val inputIdsLong = encoded.inputIds.map { it.toLong() }.toLongArray()
        val attentionMaskLong = encoded.attentionMask.map { it.toLong() }.toLongArray()

// Wrap in 2D arrays (batch of 1)
        val inputIds2D: Array<LongArray> = arrayOf(inputIdsLong)
        val attentionMask2D: Array<LongArray> = arrayOf(attentionMaskLong)

                // This uses the overload: createTensor(OrtEnvironment, Object)
        // ONNXRuntime infers the shape and type correctly.
        val inputIdsTensor = OnnxTensor.createTensor(env, inputIds2D)
        val attentionMaskTensor = OnnxTensor.createTensor(env, attentionMask2D)



        val inputs = mapOf(
            "input_ids" to inputIdsTensor,
            "attention_mask" to attentionMaskTensor
        )

        val result = session!!.run(inputs)

        val logits = (result[0].value as Array<FloatArray>)[0][0]

        // Sigmoid
        val probability = (1f / (1f + kotlin.math.exp(-logits)))

        result.close()
        inputIdsTensor.close()
        attentionMaskTensor.close()

        return probability
    }
}
