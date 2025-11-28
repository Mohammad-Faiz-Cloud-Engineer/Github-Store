package zed.rainxch.githubstore.feature.details.data

import kotlinx.coroutines.flow.Flow
import zed.rainxch.githubstore.feature.details.domain.model.DownloadProgress


/**
 * Platform-agnostic downloader contract. Platform implementations are provided via DI.
 */
interface Downloader {
    /**
     * Streams the file from [url] into an app-controlled downloads directory.
     * Returns a Flow of progress updates and completes when fully written.
     * The final file path is returned from [saveToFile] if needed.
     */
    fun download(url: String, suggestedFileName: String? = null): Flow<DownloadProgress>

    /**
     * Convenience: downloads and returns the absolute file path when complete.
     */
    suspend fun saveToFile(url: String, suggestedFileName: String? = null): String
}
