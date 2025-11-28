package zed.rainxch.githubstore.feature.details.data

interface FileLocationsProvider {
    fun appDownloadsDir(): String
    fun setExecutableIfNeeded(path: String)
}
