package com.safir.iptv.data.remote.m3u

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

data class M3uEntry(
    val name: String,
    val url: String,
    val tvgId: String?,
    val tvgName: String?,
    val logo: String?,
    val group: String,
    val number: Int,
    /** #EXTVLCOPT:http-user-agent — some providers only serve a specific UA. */
    val userAgent: String?
)

data class M3uPlaylist(
    val entries: List<M3uEntry>,
    /** url-tvg / x-tvg-url from the #EXTM3U header, if the playlist advertises a guide. */
    val epgUrl: String?
)

/**
 * Streaming parser for extended M3U playlists. Written line-by-line so a 60 MB
 * provider playlist never has to sit in memory as one string.
 */
object M3uParser {

    // key="value" — escaped rather than raw-quoted, because a raw string that ends
    // in a quote runs straight into its own terminator.
    private val ATTRIBUTE = Regex("([A-Za-z0-9_-]+)\\s*=\\s*\"([^\"]*)\"")
    private const val UNGROUPED = "Ohne Gruppe"

    fun parse(input: InputStream): M3uPlaylist =
        parse(BufferedReader(InputStreamReader(input, Charsets.UTF_8)))

    fun parse(text: String): M3uPlaylist = parse(text.reader().buffered())

    fun parse(reader: BufferedReader): M3uPlaylist {
        val entries = ArrayList<M3uEntry>()
        var epgUrl: String? = null

        var pendingName: String? = null
        var pendingAttrs: Map<String, String> = emptyMap()
        var pendingGroup: String? = null
        var pendingUserAgent: String? = null
        var number = 0

        reader.forEachLine { rawLine ->
            val line = rawLine.trim().removePrefix("﻿")
            when {
                line.isEmpty() -> Unit

                line.startsWith("#EXTM3U", ignoreCase = true) -> {
                    val attrs = attributes(line)
                    epgUrl = attrs["url-tvg"] ?: attrs["x-tvg-url"] ?: attrs["tvg-url"]
                }

                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    pendingAttrs = attributes(line)
                    // Everything after the last unquoted comma is the display name.
                    pendingName = line.substringAfterLast(',', "").trim()
                    pendingGroup = null
                    pendingUserAgent = null
                }

                line.startsWith("#EXTGRP", ignoreCase = true) -> {
                    pendingGroup = line.substringAfter(':', "").trim().ifBlank { null }
                }

                line.startsWith("#EXTVLCOPT", ignoreCase = true) -> {
                    val option = line.substringAfter(':', "")
                    if (option.startsWith("http-user-agent=", ignoreCase = true)) {
                        pendingUserAgent = option.substringAfter('=').trim().ifBlank { null }
                    }
                }

                line.startsWith("#") -> Unit // any other directive: ignored on purpose

                else -> {
                    val attrs = pendingAttrs
                    val tvgName = attrs["tvg-name"]?.ifBlank { null }
                    val display = pendingName?.ifBlank { null } ?: tvgName ?: line.substringAfterLast('/')
                    number += 1
                    entries += M3uEntry(
                        name = display.trim(),
                        url = line,
                        tvgId = attrs["tvg-id"]?.ifBlank { null },
                        tvgName = tvgName,
                        logo = (attrs["tvg-logo"] ?: attrs["logo"])?.ifBlank { null },
                        group = (pendingGroup ?: attrs["group-title"])?.ifBlank { null } ?: UNGROUPED,
                        number = attrs["tvg-chno"]?.toIntOrNull() ?: number,
                        userAgent = pendingUserAgent
                    )
                    pendingName = null
                    pendingAttrs = emptyMap()
                    pendingGroup = null
                    pendingUserAgent = null
                }
            }
        }

        return M3uPlaylist(entries = entries, epgUrl = epgUrl)
    }

    private fun attributes(line: String): Map<String, String> =
        ATTRIBUTE.findAll(line).associate { match ->
            match.groupValues[1].lowercase() to match.groupValues[2]
        }
}
