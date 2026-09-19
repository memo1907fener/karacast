package com.safir.iptv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.safir.iptv.di.AppContainer
import com.safir.iptv.util.CrashGuard

class IptvApp : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Installed before anything else, so that a failure while the container is
        // being built is caught too — that is exactly when a bad stored setting or
        // a corrupt database would bite.
        CrashGuard.install(this)
        container = AppContainer(this)
    }

    /**
     * Covers, on a budget.
     *
     * The single most likely way this app dies on a television box is not a bug in
     * it but a wall of film posters: four hundred covers in a grid, each decoded at
     * full size into a cache that by default helps itself to a quarter of the heap.
     * A box with a gigabyte of RAM runs out, and the crash arrives as an
     * OutOfMemoryError somewhere completely unrelated to the poster that caused it.
     *
     * So the cache is kept deliberately small, the covers are decoded at half the
     * colour depth — invisible on artwork, half the memory per image — and what is
     * held in memory is backed by a proper cache on disk, which costs nothing and
     * means scrolling back up a grid does not go near the network again.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.15)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("covers"))
                .maxSizeBytes(64L * 1024 * 1024)
                .build()
        }
        .allowRgb565(true)
        // Providers serve artwork from anything; a cover that says "do not cache"
        // would otherwise be fetched again on every single scroll.
        .respectCacheHeaders(false)
        .crossfade(false)
        .build()
}
