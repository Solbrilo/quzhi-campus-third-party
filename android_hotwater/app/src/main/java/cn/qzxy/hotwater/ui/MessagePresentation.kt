package cn.qzxy.hotwater.ui

/** Remove metadata only at the start; quotations of a title/date in the article remain intact. */
internal fun messageBodyWithoutRepeatedHeader(plainText: String, title: String, publishedAt: String): String {
    fun compact(text: String) = text.replace(Regex("[\\s\\u00a0\\u3000\\u200b\\ufeff]+"), "")
    fun dateKey(text: String): String = compact(text)
        .replace(Regex("^(发布时间|发布日期|发布于|时间|日期)[：:]?"), "")
        .replace('T', ' ').replace(" ", "")
        .replace('/', '-').replace("年", "-").replace("月", "-").replace("日", "")
        .replace(Regex("(\\d{2}:\\d{2}):00$"), "$1")
    val titleKey = compact(title)
    val timeKey = dateKey(publishedAt)
    val lines = plainText.trim().lines().toMutableList()
    var removedTitle = false
    var removedTime = false
    while (lines.isNotEmpty()) {
        val first = compact(lines.first())
        if (first.isEmpty()) { lines.removeAt(0); continue }
        when {
            !removedTitle && titleKey.isNotEmpty() && first == titleKey -> removedTitle = true
            !removedTime && timeKey.isNotEmpty() && dateKey(first) == timeKey -> removedTime = true
            !removedTitle && !removedTime && titleKey.isNotEmpty() && timeKey.isNotEmpty() &&
                first.startsWith(titleKey) && dateKey(first.removePrefix(titleKey)) == timeKey -> {
                removedTitle = true
                removedTime = true
            }
            else -> break
        }
        lines.removeAt(0)
    }
    return lines.joinToString("\n").trim()
}
