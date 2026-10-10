package com.safir.iptv.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * "Tell me when it starts" — one programme on one channel.
 *
 * Carries the channel's and the programme's names itself, so the notice can be
 * drawn without a database lookup, and still says something sensible if the
 * programme guide has been refreshed in the meantime.
 */
@Serializable
data class Reminder(
    val channelId: String,
    val channelName: String,
    val title: String,
    val startMs: Long,
    val endMs: Long
) {
    /** One reminder per programme slot; setting it twice is the same as once. */
    val key: String get() = "$channelId@$startMs"
}

/**
 * The reminders, kept on the device.
 *
 * A short list, so a JSON string in the preferences is enough — no table, no
 * migration. Anything whose programme is over is dropped on the next read, so
 * the list never grows beyond what is still ahead.
 */
class ReminderStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("karacast-reminders", Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(Reminder.serializer())

    private val _reminders = MutableStateFlow(read())
    val reminders: StateFlow<List<Reminder>> = _reminders.asStateFlow()

    fun isSet(channelId: String, startMs: Long): Boolean =
        _reminders.value.any { it.channelId == channelId && it.startMs == startMs }

    /** @return true when the reminder is now set, false when it was just removed. */
    fun toggle(reminder: Reminder): Boolean {
        val current = _reminders.value
        val exists = current.any { it.key == reminder.key }
        write(if (exists) current.filterNot { it.key == reminder.key } else current + reminder)
        return !exists
    }

    fun remove(reminder: Reminder) = write(_reminders.value.filterNot { it.key == reminder.key })

    private fun read(): List<Reminder> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        val now = System.currentTimeMillis()
        return runCatching { json.decodeFromString(serializer, raw) }
            .getOrDefault(emptyList())
            .filter { it.endMs > now }
    }

    private fun write(list: List<Reminder>) {
        val now = System.currentTimeMillis()
        val kept = list.filter { it.endMs > now }.sortedBy { it.startMs }
        prefs.edit { putString(KEY, json.encodeToString(serializer, kept)) }
        _reminders.value = kept
    }

    private companion object {
        const val KEY = "reminders"
    }
}
