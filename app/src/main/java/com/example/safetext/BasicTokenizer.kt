package com.example.safetext

import java.text.Normalizer

class BasicTokenizer(
    private val doLowerCase: Boolean = true,
    private val stripAccents: Boolean = true
) {

    fun tokenize(text: String): List<String> {
        // 1. Clean text (remove weird unicode spaces)
        var cleaned = cleanText(text)

        // 2. Lowercase if needed
        if (doLowerCase) {
            cleaned = cleaned.lowercase()
        }

        // 3. Strip accents if needed
        if (stripAccents) {
            cleaned = stripAccents(cleaned)
        }

        // 4. Split on whitespace
        val tokens = whitespaceTokenize(cleaned)

        // 5. Split punctuation
        val splitTokens = ArrayList<String>()
        for (token in tokens) {
            splitTokens.addAll(splitPunctuation(token))
        }

        return splitTokens
    }

    // ---------------- Helper functions ----------------

    private fun cleanText(text: String): String {
        return text.map { ch ->
            when {
                ch.code == 0 || ch.code == 0xFFFD -> ' '
                isControl(ch) -> ' '
                isWhitespace(ch) -> ' '
                else -> ch
            }
        }.joinToString("")
    }

    private fun stripAccents(text: String): String {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
        return normalized.replace("\\p{Mn}+".toRegex(), "")
    }

    private fun whitespaceTokenize(text: String): List<String> {
        return text.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
    }

    private fun splitPunctuation(token: String): List<String> {
        val result = ArrayList<String>()
        var current = StringBuilder()

        for (ch in token) {
            if (isPunctuation(ch)) {
                if (current.isNotEmpty()) {
                    result.add(current.toString())
                    current = StringBuilder()
                }
                result.add(ch.toString())
            } else {
                current.append(ch)
            }
        }

        if (current.isNotEmpty()) {
            result.add(current.toString())
        }

        return result
    }

    // ---------------- Character helpers ----------------

    private fun isWhitespace(ch: Char): Boolean {
        return ch == ' ' || ch == '\n' || ch == '\t' || ch == '\r'
    }

    private fun isControl(ch: Char): Boolean {
        return (ch.code < 32 && ch != '\n' && ch != '\t' && ch != '\r') ||
                (ch.code in 127..159)
    }

    private fun isPunctuation(ch: Char): Boolean {
        val type = Character.getType(ch)
        return type == Character.CONNECTOR_PUNCTUATION.toInt()
                || type == Character.DASH_PUNCTUATION.toInt()
                || type == Character.START_PUNCTUATION.toInt()
                || type == Character.END_PUNCTUATION.toInt()
                || type == Character.OTHER_PUNCTUATION.toInt()
                || type == Character.INITIAL_QUOTE_PUNCTUATION.toInt()
                || type == Character.FINAL_QUOTE_PUNCTUATION.toInt()
    }
}
