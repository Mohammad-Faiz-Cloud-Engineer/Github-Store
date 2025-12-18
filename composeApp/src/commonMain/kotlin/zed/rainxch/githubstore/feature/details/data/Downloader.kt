package zed.rainxch.githubstore.feature.details.data

import kotlinx.coroutines.flow.Flow
import zed.rainxch.githubstore.feature.details.domain.model.DownloadProgress

interface Downloader {

    fun download(url: String, suggestedFileName: String? = null): Flow<DownloadProgress>

    suspend fun saveToFile(url: String, suggestedFileName: String? = null): String

    suspend fun getDownloadedFilePath(fileName: String): String?

    suspend fun cancelDownload(fileName: String): Boolean
}
