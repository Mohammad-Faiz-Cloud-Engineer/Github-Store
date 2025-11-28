package zed.rainxch.githubstore.feature.details.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReleaseNetwork(
    val id: Long,
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val draft: Boolean? = null,
    val prerelease: Boolean? = null,
    val author: OwnerNetwork,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val body: String? = null,
    @SerialName("tarball_url") val tarballUrl: String,
    @SerialName("zipball_url") val zipballUrl: String,
    @SerialName("html_url") val htmlUrl: String,
    val assets: List<AssetNetwork>
)