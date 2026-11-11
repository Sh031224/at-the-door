package app.atthedoor.domain.model

/** 시설·입구 이름 뒤에 붙는 조사를 받침에 맞게 고른다. 예: "후문".eulReul() == "후문을", "로비".euroRo() == "로비로" */
object Josa {
    private fun jong(word: String): Int? {
        val ch = word.trimEnd().lastOrNull { it.isLetterOrDigit() } ?: return null
        if (ch !in '가'..'힣') return null
        return (ch - '가') % 28
    }

    private fun pick(word: String, withBatchim: String, withoutBatchim: String): String =
        word + if ((jong(word) ?: 0) != 0) withBatchim else withoutBatchim

    fun eulReul(w: String) = pick(w, "을", "를")
    fun iGa(w: String) = pick(w, "이", "가")
    fun eunNeun(w: String) = pick(w, "은", "는")
    fun gwaWa(w: String) = pick(w, "과", "와")

    /** ‘으로/로’: 받침이 없거나 ㄹ 받침이면 ‘로’ */
    fun euroRo(w: String): String {
        val j = jong(w) ?: 0
        return w + if (j == 0 || j == 8) "로" else "으로"
    }
}
