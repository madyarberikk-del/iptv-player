package com.iptv.player

data class Channel(
    val name: String,
    val url: String,
    val logo: String?,
    val group: String,
)

/** Разбор плейлистов формата M3U / M3U8 (#EXTINF с атрибутами tvg-logo, group-title). */
object M3uParser {
    private val attrRegex = Regex("""([\w-]+)="([^"]*)"""")

    fun parse(text: String): List<Channel> {
        val result = mutableListOf<Channel>()
        var name: String? = null
        var logo: String? = null
        var group = ""

        for (raw in text.lineSequence()) {
            val line = raw.trim().removePrefix("﻿")
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val attrs = attrRegex.findAll(line)
                        .associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() }
                    group = attrs["group-title"].orEmpty()
                    // Название — всё после первой запятой вне кавычек
                    name = line.replace(attrRegex, "").substringAfter(',', "").trim()
                        .ifEmpty { attrs["tvg-name"] }
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) ->
                    group = line.substringAfter(':').trim()
                line.isEmpty() || line.startsWith("#") -> Unit
                else -> {
                    result += Channel(name ?: line, line, logo, group)
                    name = null
                    logo = null
                    group = ""
                }
            }
        }
        return result
    }
}
