package zed.rainxch.githubstore.feature.details.presentation.components.sections

import androidx.compose.foundation.lazy.LazyListScope
import zed.rainxch.githubstore.feature.details.presentation.DetailsAction
import zed.rainxch.githubstore.feature.details.presentation.DetailsState
import zed.rainxch.githubstore.feature.details.presentation.components.AppHeader
import zed.rainxch.githubstore.feature.details.presentation.components.SmartInstallButton

fun LazyListScope.header(
    state: DetailsState,
    onAction: (DetailsAction) -> Unit
) {
    item {
        if (state.repository != null) {
            AppHeader(
                author = state.userProfile,
                release = state.latestRelease,
                repository = state.repository
            )
        }
    }

    item {
        SmartInstallButton(
            isDownloading = state.isDownloading,
            isInstalling = state.isInstalling,
            progress = state.downloadProgressPercent,
            primaryAsset = state.primaryAsset,
            state = state,
            onClick = { onAction(DetailsAction.InstallPrimary) }
        )
    }
}