package com.safir.iptv.data.remote.epg

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.Calendar
import java.util.TimeZone

data class XmltvProgram(
    val channelId: String,
    val title: String,
    val description: String,
    val startMs: Long,
    val endMs: Long
)

/**
 * Streaming XMLTV reader. Guides from large providers routinely weigh 100 MB
 * uncompressed, so programmes are handed to a callback one at a time and never
 * collected into a list here.
 */
object XmltvParser {

    /**
     * @param keepChannelIds when non-empty, programmes for other channels are dropped
     *        immediately — this is what keeps a provider-wide guide down to a
     *        few thousand rows for a playlist of a few hundred channels.
     * @return number of programmes emitted.
     */
    fun parse(
        input: InputStream,
        keepChannelIds: Set<String>,
        onProgram: (XmltvProgram) -> Unit
    ): Int {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        var emitted = 0
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name.equals("programme", true)) {
                val channelId = parser.getAttributeValue(null, "channel").orEmpty()
                val start = parseTime(parser.getAttributeValue(null, "start"))
                val stop = parseTime(parser.getAttributeValue(null, "stop"))

                val wanted = channelId.isNotEmpty() &&
                    (keepChannelIds.isEmpty() || channelId in keepChannelIds)

                if (!wanted || start == null) {
                    skipTag(parser)
                } else {
                    var title = ""
                    var description = ""
                    var depth = 1
                    inner@ while (depth > 0) {
                        when (parser.next()) {
                            XmlPullParser.START_TAG -> {
                                val tag = parser.name
                                if (tag.equals("title", true) && title.isEmpty()) {
                                    // readText consumes the matching END_TAG itself.
                                    title = readText(parser)
                                } else if (tag.equals("desc", true) && description.isEmpty()) {
                                    description = readText(parser)
                                } else {
                                    depth++
                                }
                            }
                            XmlPullParser.END_TAG -> depth--
                            XmlPullParser.END_DOCUMENT -> break@inner
                        }
                    }
                    val end = stop ?: (start + 60 * 60 * 1000L)
                    if (end > start) {
                        onProgram(
                            XmltvProgram(
                                channelId = channelId,
                                title = title.ifBlank { "—" },
                                description = description,
                                startMs = start,
                                endMs = end
                            )
                        )
                        emitted++
                    }
                }
            }
            event = parser.next()
        }
        return emitted
    }

    /** Reads the text of the current START_TAG and consumes its END_TAG. */
    private fun readText(parser: XmlPullParser): String {
        val builder = StringBuilder()
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.TEXT -> builder.append(parser.text)
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> depth = 0
            }
        }
        return builder.toString().trim()
    }

    /** Consumes everything up to and including the END_TAG of the current element. */
    private fun skipTag(parser: XmlPullParser) {
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    /**
     * XMLTV timestamps look like `20260912203000 +0200`. The offset is optional and
     * some providers ship shorter forms, so everything is parsed positionally.
     */
    fun parseTime(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        val digits = trimmed.takeWhile { it.isDigit() }
        if (digits.length < 8) return null

        val year = digits.substring(0, 4).toIntOrNull() ?: return null
        val month = digits.substring(4, 6).toIntOrNull() ?: return null
        val day = digits.substring(6, 8).toIntOrNull() ?: return null
        val hour = digits.takeIf { it.length >= 10 }?.substring(8, 10)?.toIntOrNull() ?: 0
        val minute = digits.takeIf { it.length >= 12 }?.substring(10, 12)?.toIntOrNull() ?: 0
        val second = digits.takeIf { it.length >= 14 }?.substring(12, 14)?.toIntOrNull() ?: 0

        val offsetPart = trimmed.drop(digits.length).trim()
        val zone = when {
            offsetPart.isEmpty() -> TimeZone.getDefault()
            offsetPart.equals("Z", true) -> TimeZone.getTimeZone("UTC")
            offsetPart.first() == '+' || offsetPart.first() == '-' -> {
                val sign = offsetPart.first()
                val body = offsetPart.drop(1).filter { it.isDigit() }.padEnd(4, '0').take(4)
                TimeZone.getTimeZone("GMT$sign${body.substring(0, 2)}:${body.substring(2, 4)}")
            }
            else -> TimeZone.getTimeZone(offsetPart)
        }

        val calendar = Calendar.getInstance(zone)
        calendar.clear()
        calendar.set(year, month - 1, day, hour, minute, second)
        return calendar.timeInMillis
    }
}
