package com.example.safetext

import ai.onnxruntime.*

class SafeTextModel(context: android.content.Context) {

    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        val modelBytes = context.assets.open("distilbert_int8.onnx").readBytes()
        session = env.createSession(modelBytes)
    }

    fun predict(inputIds: List<Int>, attentionMask: List<Int>): Float {
        val inputLength = inputIds.size

        // Convert to long array (required by ONNX)
        val inputIdsArray = Array(1) { LongArray(inputLength) }
        val attnArray = Array(1) { LongArray(inputLength) }

        for (i in 0 until inputLength) {
            inputIdsArray[0][i] = inputIds[i].toLong()
            attnArray[0][i] = attentionMask[i].toLong()
        }

        val inputIdsTensor = OnnxTensor.createTensor(env, inputIdsArray)
        val attnTensor = OnnxTensor.createTensor(env, attnArray)

        val inputs = mapOf(
            "input_ids" to inputIdsTensor,
            "attention_mask" to attnTensor
        )

        val output = session.run(inputs)

        // Output: logits [1,1] → get float
        val logits = (output[0].value as Array<FloatArray>)[0][0]

        // Apply sigmoid manually
        return sigmoid(logits)
    }

    private fun sigmoid(x: Float): Float {
        return (1f / (1f + kotlin.math.exp(-x)))
    }
}
