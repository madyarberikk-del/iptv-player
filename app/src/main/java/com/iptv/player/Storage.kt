package com.iptv.player

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Локальное хранение: плейлист, избранное, последний канал. Всё только на телефоне. */
class Storage(context: Context) {
    private val prefs = context.getSharedPreferences("iptv", Context.MODE_PRIVATE)
    private val playlistFile = File(context.filesDir, "playlist.m3u")

    var playlistUrl: String?
        get() = prefs.getString(KEY_URL, null)
        set(value) = prefs.edit().putString(KEY_URL, value).apply()

    var lastChannelUrl: String?
        get() = prefs.getString(KEY_LAST, null)
        set(value) = prefs.edit().putString(KEY_LAST, value).apply()

    var favorites: Set<String>
        get() = prefs.getStringSet(KEY_FAV, emptySet())!!.toSet()
        set(value) = prefs.edit().putStringSet(KEY_FAV, value).apply()

    fun toggleFavorite(url: String): Boolean {
        val fav = favorites.toMutableSet()
        val added = if (url in fav) { fav.remove(url); false } else { fav.add(url); true }
        favorites = fav
        return added
    }

    fun loadCachedPlaylist(): String? =
        playlistFile.takeIf { it.exists() }?.readText()

    fun savePlaylist(text: String) = playlistFile.writeText(text)

    companion object {
        private const val KEY_URL = "playlist_url"
        private const val KEY_LAST = "last_channel"
        private const val KEY_FAV = "favorites"

        const val USER_AGENT = "IPTVPlayer/1.0 (Android)"

        /** Скачивает плейлист; вручную проходит редиректы, в т.ч. http ↔ https. */
        suspend fun download(url: String): String = withContext(Dispatchers.IO) {
            var current = url
            repeat(5) {
                val conn = URL(current).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.setRequestProperty("User-Agent", USER_AGENT)
                try {
                    when (val code = conn.responseCode) {
                        in 300..399 -> {
                            val location = conn.getHeaderField("Location")
                                ?: error("Редирект без адреса")
                            current = URL(URL(current), location).toString()
                        }
                        in 200..299 -> return@withContext conn.inputStream.bufferedReader().readText()
                        else -> error("Сервер ответил $code")
                    }
                } finally {
                    conn.disconnect()
                }
            }
            error("Слишком много перенаправлений")
        }
    }
}
