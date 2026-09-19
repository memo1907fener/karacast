package com.safir.iptv.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.safir.iptv.IptvApp
import com.safir.iptv.di.AppContainer
import com.safir.iptv.ui.channels.ChannelsViewModel
import com.safir.iptv.ui.guide.GuideViewModel
import com.safir.iptv.ui.login.SetupViewModel
import com.safir.iptv.ui.player.PlayerViewModel
import com.safir.iptv.ui.settings.GroupChannelsViewModel
import com.safir.iptv.ui.settings.GroupsViewModel
import com.safir.iptv.ui.settings.SettingsViewModel
import com.safir.iptv.ui.vod.MoviesViewModel
import com.safir.iptv.ui.vod.SeriesDetailViewModel
import com.safir.iptv.ui.vod.SeriesViewModel

private fun CreationExtras.container(): AppContainer =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as IptvApp).container

/** One factory for every ViewModel in the app — the graph is tiny and hand-wired. */
val AppViewModelFactory = viewModelFactory {
    initializer {
        SetupViewModel(
            container().playlistRepository,
            container().epgRepository,
            container().vodRepository
        )
    }
    initializer {
        ChannelsViewModel(
            container().playlistRepository,
            container().epgRepository,
            container().settings
        )
    }
    initializer {
        PlayerViewModel(
            container().playlistRepository,
            container().epgRepository,
            container().settings
        )
    }
    initializer {
        GuideViewModel(
            container().playlistRepository,
            container().epgRepository,
            container().settings
        )
    }
    initializer { GroupsViewModel(container().playlistRepository, container().settings) }
    initializer {
        GroupChannelsViewModel(container().playlistRepository, container().settings)
    }
    initializer {
        SettingsViewModel(
            container().playlistRepository,
            container().epgRepository,
            container().settings,
            container().vodRepository
        )
    }
    initializer { MoviesViewModel(container().vodRepository, container().settings) }
    initializer { SeriesViewModel(container().vodRepository, container().settings) }
    initializer { SeriesDetailViewModel(container().vodRepository, container().settings) }
}
