package com.example.safetext.app

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

fun loadVocab(context: Context, filename: String): Map<String, Int> {
    val vocab = mutableMapOf<String, Int>()

    context.assets.open(filename).use { inputStream ->
        BufferedReader(InputStreamReader(inputStream)).use { reader ->
            var index = 0
            var line: String?
            while (true) {
                line = reader.readLine() ?: break
                vocab[line] = index
                index++
            }
        }
    }

    return vocab
}
