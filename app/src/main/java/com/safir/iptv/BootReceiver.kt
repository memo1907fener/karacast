package com.safir.iptv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.safir.iptv.data.prefs.SettingsStore

/**
 * Brings the app up by itself once the box has finished booting — for a TV box that
 * exists only to run this, that is the difference between "switch on and watch" and
 * "switch on, find the launcher, find the app". Off by default; a receiver that
 * launched uninvited would be a nuisance on a phone.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }
        if (!SettingsStore(context).autoStartOnBoot) return

        val launch = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(launch) }
    }
}
