package com.example.safetext

class BertTokenizer(
    vocab: Map<String, Int>,
    private val doLowerCase: Boolean = true,
    private val maxLength: Int = 128,
    private val clsToken: String = "[CLS]",
    private val sepToken: String = "[SEP]",
    private val padToken: String = "[PAD]",
    private val unkToken: String = "[UNK]"
) {
    private val basicTokenizer = BasicTokenizer(doLowerCase)
    private val wordPieceTokenizer = WordPieceTokenizer(vocab, unkToken)

    private val clsId = vocab[clsToken]!!
    private val sepId = vocab[sepToken]!!
    private val padId = vocab[padToken]!!
    private val unkId = vocab[unkToken]!!

    private val vocabMap = vocab

    fun encode(text: String): TokenizedOutput {
        // 1. Basic tokenization
        val basicTokens = basicTokenizer.tokenize(text)

        // 2. Apply WordPiece to each token
        val subwords = basicTokens.flatMap { wordPieceTokenizer.tokenize(it) }

        // 3. Add special tokens
        val tokensWithSpecials = listOf(clsToken) + subwords + listOf(sepToken)

        // 4. Convert tokens → IDs
        val tokenIds = tokensWithSpecials.map { token ->
            vocabMap[token] ?: unkId
        }.toMutableList()

        // 5. Create attention mask (1 = real token, 0 = padding)
        val attentionMask = MutableList(tokenIds.size) { 1 }

        // 6. Pad or truncate to maxLength
        if (tokenIds.size > maxLength) {
            tokenIds.subList(maxLength, tokenIds.size).clear()
            attentionMask.subList(maxLength, attentionMask.size).clear()
        } else if (tokenIds.size < maxLength) {
            val padAmount = maxLength - tokenIds.size
            repeat(padAmount) {
                tokenIds.add(padId)
                attentionMask.add(0)
            }
        }

        return TokenizedOutput(
            inputIds = tokenIds,
            attentionMask = attentionMask
        )
    }
}

data class TokenizedOutput(
    val inputIds: List<Int>,
    val attentionMask: List<Int>
)
