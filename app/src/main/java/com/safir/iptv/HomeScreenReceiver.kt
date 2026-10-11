package com.safir.iptv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The launcher asking for the home-screen row right after installation.
 *
 * Starting the process already sets the row up (see [IptvApp]); this only keeps
 * the process alive long enough for that to happen before Android reclaims it.
 */
class HomeScreenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as IptvApp
        val pending = goAsync()
        app.appScope.launch {
            delay(5_000)
            pending.finish()
        }
    }
}
