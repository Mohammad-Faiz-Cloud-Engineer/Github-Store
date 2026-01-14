package zed.rainxch.githubstore.feature.developer_profile.domain.repository

import zed.rainxch.githubstore.feature.developer_profile.domain.model.DeveloperProfile
import zed.rainxch.githubstore.feature.developer_profile.domain.model.DeveloperRepository

interface DeveloperProfileRepository {
    suspend fun getDeveloperProfile(username: String): Result<DeveloperProfile>
    suspend fun getDeveloperRepositories(username: String): Result<List<DeveloperRepository>>
}