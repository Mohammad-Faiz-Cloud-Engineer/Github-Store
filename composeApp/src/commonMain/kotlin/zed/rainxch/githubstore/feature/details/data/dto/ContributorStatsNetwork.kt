package zed.rainxch.githubstore.feature.details.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class ContributorStatsNetwork(
    val total: Int? = null,
    val author: OwnerNetwork? = null
)