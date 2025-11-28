package zed.rainxch.githubstore.feature.details.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import zed.rainxch.githubstore.core.domain.model.GithubRelease
import zed.rainxch.githubstore.core.domain.model.GithubRepoSummary
import zed.rainxch.githubstore.core.domain.model.GithubUser
import zed.rainxch.githubstore.core.domain.model.GithubUserProfile
import zed.rainxch.githubstore.feature.details.data.dto.ReleaseNetwork
import zed.rainxch.githubstore.feature.details.data.dto.RepoByIdNetwork
import zed.rainxch.githubstore.feature.details.data.dto.RepoInfoNetwork
import zed.rainxch.githubstore.feature.details.data.dto.UserProfileNetwork
import zed.rainxch.githubstore.feature.details.data.mappers.toDomain
import zed.rainxch.githubstore.feature.details.data.utils.preprocessMarkdown
import zed.rainxch.githubstore.feature.details.domain.model.RepoStats
import zed.rainxch.githubstore.feature.details.domain.repository.DetailsRepository

class DetailsRepositoryImpl(
    private val github: HttpClient
) : DetailsRepository {

    override suspend fun getRepositoryById(id: Long): GithubRepoSummary {
        val repo: RepoByIdNetwork = github.get("/repositories/$id") {
            header(HttpHeaders.Accept, "application/vnd.github+json")
        }.body()

        return GithubRepoSummary(
            id = repo.id,
            name = repo.name,
            fullName = repo.fullName,
            owner = GithubUser(
                id = repo.owner.id,
                login = repo.owner.login,
                avatarUrl = repo.owner.avatarUrl,
                htmlUrl = repo.owner.htmlUrl
            ),
            description = repo.description,
            htmlUrl = repo.htmlUrl,
            stargazersCount = repo.stars,
            forksCount = repo.forks,
            language = repo.language,
            topics = repo.topics,
            releasesUrl = "https://api.github.com/repos/${repo.owner.login}/${repo.name}/releases{/id}",
            updatedAt = repo.updatedAt
        )
    }

    override suspend fun getLatestPublishedRelease(owner: String, repo: String): GithubRelease? {
        val releases: List<ReleaseNetwork> = github.get("/repos/$owner/$repo/releases") {
            header(HttpHeaders.Accept, "application/vnd.github+json")
            parameter("per_page", 10)
        }.body()

        val latest = releases
            .asSequence()
            .filter { (it.draft != true) && (it.prerelease != true) }
            .sortedByDescending { it.publishedAt ?: it.createdAt ?: "" }
            .firstOrNull()
            ?: return null


        val processedLatestRelease = latest.copy(
            body = latest.body?.replace("<details>", "")
                ?.replace("</details>", "")
                ?.replace("<summary>", "")
                ?.replace("</summary>", "")
                ?.replace("\r\n", "\n")
                ?.let { rawMarkdown ->
                    preprocessMarkdown(
                        markdown = rawMarkdown,
                        baseUrl = "https://raw.githubusercontent.com/$owner/$repo/main/"
                    )
                }
        )

        return processedLatestRelease.toDomain()
    }

    override suspend fun getReadme(owner: String, repo: String): String? {
        return try {
            val rawMarkdown =
                github
                    .get("https://raw.githubusercontent.com/$owner/$repo/master/README.md")
                    .body<String>()
            preprocessMarkdown(
                markdown = rawMarkdown,
                baseUrl = "https://raw.githubusercontent.com/$owner/$repo/master/"
            )
        } catch (_: Throwable) {
            try {
                val rawMarkdown =
                    github
                        .get("https://raw.githubusercontent.com/$owner/$repo/main/README.md")
                        .body<String>()
                preprocessMarkdown(
                    markdown = rawMarkdown,
                    baseUrl = "https://raw.githubusercontent.com/$owner/$repo/main/"
                )
            } catch (_: Throwable) {
                null
            }
        }
    }

    override suspend fun getRepoStats(owner: String, repo: String): RepoStats {
        val info: RepoInfoNetwork = github.get("/repos/$owner/$repo") {
            header(HttpHeaders.Accept, "application/vnd.github+json")
        }.body()

        return RepoStats(
            stars = info.stars,
            forks = info.forks,
            openIssues = info.openIssues,
        )
    }

    override suspend fun getUserProfile(username: String): GithubUserProfile {
        val user: UserProfileNetwork = github.get("/users/$username") {
            header(HttpHeaders.Accept, "application/vnd.github+json")
        }.body()

        return GithubUserProfile(
            id = user.id,
            login = user.login,
            name = user.name,
            bio = user.bio,
            avatarUrl = user.avatarUrl,
            htmlUrl = user.htmlUrl,
            followers = user.followers,
            following = user.following,
            publicRepos = user.publicRepos,
            location = user.location,
            company = user.company,
            blog = user.blog,
            twitterUsername = user.twitterUsername
        )
    }
}