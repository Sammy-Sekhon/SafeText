package com.example.safetext

class WordPieceTokenizer(
    private val vocab: Map<String, Int>,
    private val unkToken: String = "[UNK]",
    private val maxInputCharsPerWord: Int = 100
) {

    fun tokenize(token: String): List<String> {
        val chars = token.toCharArray()

        // Rare case: extremely long token, treat as unknown
        if (chars.size > maxInputCharsPerWord) {
            return listOf(unkToken)
        }

        val result = ArrayList<String>()
        var start = 0

        while (start < chars.size) {
            var end = chars.size
            var matched: String? = null

            while (start < end) {
                val sub = if (start == 0)
                    token.substring(start, end)
                else
                    "##" + token.substring(start, end)

                if (vocab.containsKey(sub)) {
                    matched = sub
                    break
                }

                end--
            }

            if (matched == null) {
                result.add(unkToken)
                break
            }

            result.add(matched)
            start = end
        }

        return result
    }
}