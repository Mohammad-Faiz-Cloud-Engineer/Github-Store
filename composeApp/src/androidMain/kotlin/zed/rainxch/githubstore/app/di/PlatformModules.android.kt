package zed.rainxch.githubstore.app.di

import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.android.ext.koin.androidContext
import zed.rainxch.githubstore.feature.install.AndroidDownloader
import zed.rainxch.githubstore.feature.install.AndroidFileLocationsProvider
import zed.rainxch.githubstore.feature.install.AndroidInstaller
import zed.rainxch.githubstore.feature.install.Downloader
import zed.rainxch.githubstore.feature.install.FileLocationsProvider
import zed.rainxch.githubstore.feature.install.Installer

actual val platformModule: Module = module {
    single<Downloader> {
        AndroidDownloader(
            context = get(),
            files = get()
        )
    }

    single<Installer> {
        AndroidInstaller(
            context = get(),
            files = get()
        )
    }

    single<FileLocationsProvider> {
        AndroidFileLocationsProvider(context = get())
    }
}