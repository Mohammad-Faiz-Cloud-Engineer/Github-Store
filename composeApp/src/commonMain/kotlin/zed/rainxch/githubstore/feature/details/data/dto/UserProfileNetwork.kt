package zed.rainxch.githubstore.feature.details.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserProfileNetwork(
    val id: Long,
    val login: String,
    val name: String? = null,
    val bio: String? = null,
    @SerialName("avatar_url") val avatarUrl: String,
    @SerialName("html_url") val htmlUrl: String,
    val followers: Int,
    val following: Int,
    @SerialName("public_repos") val publicRepos: Int,
    val location: String? = null,
    val company: String? = null,
    val blog: String? = null,
    @SerialName("twitter_username") val twitterUsername: String? = null
)