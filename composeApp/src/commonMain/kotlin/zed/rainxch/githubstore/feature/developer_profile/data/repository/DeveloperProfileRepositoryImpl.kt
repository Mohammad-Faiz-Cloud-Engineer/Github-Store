package zed.rainxch.githubstore.feature.developer_profile.data.repository

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import zed.rainxch.githubstore.core.data.local.db.dao.InstalledAppDao
import zed.rainxch.githubstore.core.domain.Platform
import zed.rainxch.githubstore.core.domain.model.PlatformType
import zed.rainxch.githubstore.core.domain.repository.FavouritesRepository
import zed.rainxch.githubstore.feature.developer_profile.data.dto.GitHubRepoResponse
import zed.rainxch.githubstore.feature.developer_profile.data.dto.GitHubUserResponse
import zed.rainxch.githubstore.feature.developer_profile.data.mappers.toDomain
import zed.rainxch.githubstore.feature.developer_profile.domain.model.DeveloperProfile
import zed.rainxch.githubstore.feature.developer_profile.domain.model.DeveloperRepository
import zed.rainxch.githubstore.feature.developer_profile.domain.repository.DeveloperProfileRepository

/**
 * Implementation of [DeveloperProfileRepository] that fetches developer profile
 * and repository data from GitHub API with pagination, filtering, and local data enrichment.
 */
class DeveloperProfileRepositoryImpl(
    private val httpClient: HttpClient,
    private val platform: Platform,
    private val installedAppsDao: InstalledAppDao,
    private val favouritesRepository: FavouritesRepository
) : DeveloperProfileRepository {
    
    companion object {
        private const val REPOS_PER_PAGE = 100
        private const val RELEASES_PER_PAGE = 10
        private const val MAX_CONCURRENT_RELEASE_CHECKS = 20
    }

    override suspend fun getDeveloperProfile(username: String): Result<DeveloperProfile> {
        return withContext(Dispatchers.IO) {
            try {
                val response = httpClient.get("/users/$username")

                if (!response.status.isSuccess()) {
                    val errorMessage = "Failed to fetch developer profile: ${response.status.description}"
                    Logger.e { errorMessage }
                    return@withContext Result.failure(Exception(errorMessage))
                }

                val userResponse: GitHubUserResponse = response.body()
                Result.success(userResponse.toDomain())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(e) { "Failed to fetch developer profile for $username" }
                Result.failure(e)
            }
        }
    }

    override suspend fun getDeveloperRepositories(username: String): Result<List<DeveloperRepository>> {
        return withContext(Dispatchers.IO) {
            try {
                // Fetch all repositories with pagination
                val allRepos = fetchAllRepositories(username)
                
                if (allRepos.isEmpty()) {
                    return@withContext Result.success(emptyList())
                }

                // Fetch favorites once for all repos
                val favoriteIds = favouritesRepository.getAllFavorites()
                    .first()
                    .mapTo(HashSet()) { it.repoId }

                // Process repositories in parallel with concurrency control
                val processedRepos = coroutineScope {
                    val semaphore = Semaphore(MAX_CONCURRENT_RELEASE_CHECKS)
                    allRepos.map { repo ->
                        async {
                            semaphore.withPermit {
                                processRepository(repo, favoriteIds)
                            }
                        }
                    }.awaitAll()
                }

                Result.success(processedRepos)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(e) { "Failed to fetch repositories for $username" }
                Result.failure(e)
            }
        }
    }
    
    /**
     * Fetches all repositories for a user with pagination.
     * Filters out archived and forked repositories.
     */
    private suspend fun fetchAllRepositories(username: String): List<GitHubRepoResponse> {
        val allRepos = mutableListOf<GitHubRepoResponse>()
        var page = 1

        while (true) {
            val response = httpClient.get("/users/$username/repos") {
                parameter("per_page", REPOS_PER_PAGE)
                parameter("page", page)
                parameter("type", "owner")
                parameter("sort", "updated")
                parameter("direction", "desc")
            }

            if (!response.status.isSuccess()) {
                val errorMessage = "Failed to fetch repositories: ${response.status.description}"
                Logger.e { errorMessage }
                throw Exception(errorMessage)
            }

            val repos: List<GitHubRepoResponse> = response.body()
            
            if (repos.isEmpty()) break

            // Filter out archived and forked repos immediately
            allRepos.addAll(repos.filter { !it.archived && !it.fork })

            if (repos.size < REPOS_PER_PAGE) break
            page++
        }

        return allRepos
    }

    /**
     * Processes a single repository by enriching it with:
     * - Installation status from local database
     * - Favorite status
     * - Release information (has releases, installable assets, latest version)
     */
    private suspend fun processRepository(
        repo: GitHubRepoResponse,
        favoriteIds: Set<Long>
    ): DeveloperRepository {
        val installedApp = installedAppsDao.getAppByRepoId(repo.id)
        val isFavorite = repo.id in favoriteIds

        val (hasReleases, hasInstallableAssets, latestVersion) = checkReleaseInfo(
            owner = repo.fullName.substringBefore('/'),
            repoName = repo.name
        )

        return repo.toDomain(
            hasReleases = hasReleases,
            hasInstallableAssets = hasInstallableAssets,
            isInstalled = installedApp != null,
            isFavorite = isFavorite,
            latestVersion = latestVersion
        )
    }

    /**
     * Checks release information for a repository.
     * Returns: (hasReleases, hasInstallableAssets, latestVersion)
     * 
     * Only considers stable releases (non-draft, non-prerelease).
     * Checks for platform-specific installable assets.
     */
    private suspend fun checkReleaseInfo(
        owner: String,
        repoName: String
    ): Triple<Boolean, Boolean, String?> {
        return try {
            val response = httpClient.get("/repos/$owner/$repoName/releases") {
                parameter("per_page", RELEASES_PER_PAGE)
            }

            if (!response.status.isSuccess()) {
                return Triple(false, false, null)
            }

            val releases: List<ReleaseNetworkModel> = response.body()
            
            if (releases.isEmpty()) {
                return Triple(false, false, null)
            }

            // Find first stable release (non-draft, non-prerelease)
            val stableRelease = releases.firstOrNull { release ->
                release.draft != true && release.prerelease != true
            }

            if (stableRelease == null) {
                return Triple(true, false, null)
            }

            // Check for platform-specific installable assets
            val hasInstallableAssets = stableRelease.assets.any { asset ->
                isInstallableAsset(asset.name)
            }

            Triple(
                true,
                hasInstallableAssets,
                if (hasInstallableAssets) stableRelease.tagName else null
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Silently fail for release checks to avoid blocking repository list
            Triple(false, false, null)
        }
    }
    
    /**
     * Determines if an asset is installable on the current platform.
     */
    private fun isInstallableAsset(assetName: String): Boolean {
        val name = assetName.lowercase()
        return when (platform.type) {
            PlatformType.ANDROID -> name.endsWith(".apk")
            PlatformType.WINDOWS -> name.endsWith(".msi") || name.endsWith(".exe")
            PlatformType.MACOS -> name.endsWith(".dmg") || name.endsWith(".pkg")
            PlatformType.LINUX -> name.endsWith(".appimage") || 
                                  name.endsWith(".deb") || 
                                  name.endsWith(".rpm")
        }
    }

    @Serializable
    private data class ReleaseNetworkModel(
        val assets: List<AssetNetworkModel>,
        val draft: Boolean? = null,
        val prerelease: Boolean? = null,
        @SerialName("tag_name") val tagName: String,
        @SerialName("published_at") val publishedAt: String? = null
    )

    @Serializable
    private data class AssetNetworkModel(
        val name: String
    )
}
