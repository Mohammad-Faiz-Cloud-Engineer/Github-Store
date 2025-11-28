package zed.rainxch.githubstore.feature.details.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AssetNetwork(
    val id: Long,
    val name: String,
    @SerialName("content_type") val contentType: String,
    val size: Long,
    @SerialName("browser_download_url") val downloadUrl: String,
    val uploader: OwnerNetwork
)