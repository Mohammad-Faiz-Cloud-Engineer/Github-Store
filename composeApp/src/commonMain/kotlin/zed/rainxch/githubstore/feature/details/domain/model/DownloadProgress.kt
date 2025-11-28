package zed.rainxch.githubstore.feature.details.domain.model

data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long?,
    val percent: Int?,
)