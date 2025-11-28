package zed.rainxch.githubstore.feature.details.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RepoByIdNetwork(
    val id: Long,
    val name: String,
    @SerialName("full_name") val fullName: String,
    val owner: OwnerNetwork,
    val description: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("stargazers_count") val stars: Int,
    @SerialName("forks_count") val forks: Int,
    val language: String? = null,
    val topics: List<String>? = null,
    @SerialName("updated_at") val updatedAt: String,
)