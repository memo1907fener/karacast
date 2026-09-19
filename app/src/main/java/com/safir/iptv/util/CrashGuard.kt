package com.safir.iptv.util

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Process
import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

/**
 * Keeps a crash from ending the evening.
 *
 * A television is not a phone. Nobody is going to read a stack trace, nobody is
 * going to reopen the app from a launcher two menus deep, and the person in front
 * of it may well be somebody who was handed a remote and told "this is how you
 * watch television now". So when something does go wrong — a provider sending
 * nonsense, a codec giving up, a bug of mine — the app writes down what happened
 * and starts itself again a second later. From the sofa it looks like a flicker.
 *
 * What it deliberately does *not* do is swallow the failure. The trace is kept so
 * that the next person to ask "it crashed, what happened?" has an answer, and it
 * is shown in the settings where it can be read out over the phone.
 *
 * The one thing worse than a crash is a crash loop, so after three failures in
 * quick succession it stops restarting and lets the app stay closed. Something is
 * then properly broken, and hammering the same broken thing forever helps nobody.
 */
object CrashGuard {

    /**
     * Held so the settings page can ask what happened without threading a Context
     * through three layers for one string. Set before anything else in the app.
     */
    @Volatile
    private var application: Application? = null

    fun install(app: Application) {
        application = app
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            val handled = runCatching { record(app, error) }.getOrDefault(false)
            Log.e("Karacast", "Uncaught exception on ${thread.name}", error)

            if (!handled) {
                // Could not even write the note down; hand it to whoever was here
                // before us rather than inventing a second failure on top.
                previous?.uncaughtException(thread, error)
                return@setDefaultUncaughtExceptionHandler
            }

            restart(app)
            // A breath, so the request to start the activity reaches the system
            // before this process stops existing.
            runCatching { Thread.sleep(RESTART_HANDOVER_MS) }
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }

    /** @return false when this crash follows too many others to be worth restarting. */
    private fun record(app: Application, error: Throwable): Boolean {
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val last = prefs.getLong(KEY_AT, 0L)
        val streak = if (now - last < LOOP_WINDOW_MS) prefs.getInt(KEY_STREAK, 0) + 1 else 1

        val trace = StringWriter().also { writer ->
            PrintWriter(writer).use { error.printStackTrace(it) }
        }.toString().take(MAX_TRACE_CHARS)

        prefs.edit()
            .putLong(KEY_AT, now)
            .putInt(KEY_STREAK, streak)
            .putString(KEY_MESSAGE, "${error::class.java.simpleName}: ${error.message.orEmpty()}")
            .putString(KEY_TRACE, trace)
            .apply()

        return streak < LOOP_LIMIT
    }

    /**
     * Asks for the app to be opened again, right now, from the crashing thread.
     *
     * Not from an alarm a second later, which is the textbook recipe and which does
     * not work here: by then this process is gone, the start counts as coming from
     * the background, and every Android since 10 refuses those silently. At this
     * instant the app is still the foreground app, which is the one state in which
     * the system will still honour it.
     */
    private fun restart(app: Application) {
        val intent = app.packageManager.getLaunchIntentForPackage(app.packageName) ?: return
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        )
        runCatching { app.startActivity(intent) }
    }

    /** What went wrong last time, for the settings page. Null when nothing ever has. */
    fun lastCrash(): CrashReport? {
        val prefs = application?.getSharedPreferences(PREFS, Context.MODE_PRIVATE) ?: return null
        val at = prefs.getLong(KEY_AT, 0L)
        if (at <= 0L) return null
        return CrashReport(
            atMs = at,
            message = prefs.getString(KEY_MESSAGE, null).orEmpty(),
            trace = prefs.getString(KEY_TRACE, null).orEmpty()
        )
    }

    fun forget() {
        application?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.clear()?.apply()
    }

    private const val PREFS = "karacast-crash"
    private const val KEY_AT = "at"
    private const val KEY_STREAK = "streak"
    private const val KEY_MESSAGE = "message"
    private const val KEY_TRACE = "trace"
    private const val RESTART_HANDOVER_MS = 250L
    private const val MAX_TRACE_CHARS = 4_000

    /** Two crashes inside this window count as the same trouble. */
    private const val LOOP_WINDOW_MS = 20_000L

    /** After this many in a row, stop restarting. */
    private const val LOOP_LIMIT = 3
}

data class CrashReport(
    val atMs: Long,
    val message: String,
    val trace: String
)
