package com.iptv.player

/** Распознавание ссылок YouTube и извлечение ID видео. */
object YouTube {
    private val hostRegex = Regex("""^(https?://)?([\w-]+\.)*(youtube\.com|youtu\.be|youtube-nocookie\.com)(/|$)""", RegexOption.IGNORE_CASE)
    private val idPatterns = listOf(
        Regex("""youtu\.be/([\w-]{11})"""),
        Regex("""youtube(?:-nocookie)?\.com/(?:watch\?(?:.*&)?v=|embed/|live/|shorts/|v/)([\w-]{11})"""),
    )

    fun isYouTube(url: String) = hostRegex.containsMatchIn(url.trim())

    fun videoId(url: String): String? =
        idPatterns.firstNotNullOfOrNull { it.find(url)?.groupValues?.get(1) }
}
