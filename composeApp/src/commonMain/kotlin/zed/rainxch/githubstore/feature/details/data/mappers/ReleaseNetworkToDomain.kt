package zed.rainxch.githubstore.feature.details.data.mappers

import zed.rainxch.githubstore.core.domain.model.GithubAsset
import zed.rainxch.githubstore.core.domain.model.GithubRelease
import zed.rainxch.githubstore.core.domain.model.GithubUser
import zed.rainxch.githubstore.feature.details.data.dto.AssetNetwork
import zed.rainxch.githubstore.feature.details.data.dto.ReleaseNetwork

fun ReleaseNetwork.toDomain(): GithubRelease = GithubRelease(
    id = id,
    tagName = tagName,
    name = name,
    author = GithubUser(
        id = author.id,
        login = author.login,
        avatarUrl = author.avatarUrl,
        htmlUrl = author.htmlUrl
    ),
    publishedAt = publishedAt ?: createdAt ?: "",
    description = body,
    assets = assets.map { assetNetwork -> assetNetwork.toDomain() },
    tarballUrl = tarballUrl,
    zipballUrl = zipballUrl,
    htmlUrl = htmlUrl
)

private fun AssetNetwork.toDomain(): GithubAsset = GithubAsset(
    id = id,
    name = name,
    contentType = contentType,
    size = size,
    downloadUrl = downloadUrl,
    uploader = GithubUser(
        id = uploader.id,
        login = uploader.login,
        avatarUrl = uploader.avatarUrl,
        htmlUrl = uploader.htmlUrl
    )
)