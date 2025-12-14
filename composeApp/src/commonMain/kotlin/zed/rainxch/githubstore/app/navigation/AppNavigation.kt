package zed.rainxch.githubstore.app.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.compose.serialization.serializers.SnapshotStateListSerializer
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import zed.rainxch.githubstore.MainAction
import zed.rainxch.githubstore.MainState
import zed.rainxch.githubstore.app.app_state.components.RateLimitDialog
import zed.rainxch.githubstore.feature.auth.presentation.AuthenticationRoot
import zed.rainxch.githubstore.feature.details.presentation.DetailsRoot
import zed.rainxch.githubstore.feature.home.presentation.HomeRoot
import zed.rainxch.githubstore.feature.search.presentation.SearchRoot
import zed.rainxch.githubstore.feature.settings.presentation.SettingsRoot

@Composable
fun AppNavigation(
    navBackStack: SnapshotStateList<GithubStoreGraph>
) {

    NavDisplay(
        backStack = navBackStack,
        onBack = {
            navBackStack.removeLastOrNull()
        },
        entryProvider = entryProvider {
            entry<GithubStoreGraph.HomeScreen> {
                HomeRoot(
                    onNavigateToSearch = {
                        navBackStack.add(GithubStoreGraph.SearchScreen)
                    },
                    onNavigateToSettings = {
                        navBackStack.add(GithubStoreGraph.SettingsScreen)
                    },
                    onNavigateToDetails = { repo ->
                        navBackStack.add(
                            GithubStoreGraph.DetailsScreen(
                                repositoryId = repo.id.toInt()
                            )
                        )
                    }
                )
            }

            entry<GithubStoreGraph.SearchScreen> {
                SearchRoot(
                    onNavigateBack = {
                        navBackStack.removeLastOrNull()
                    },
                    onNavigateToDetails = { repo ->
                        navBackStack.add(
                            GithubStoreGraph.DetailsScreen(
                                repositoryId = repo.id.toInt()
                            )
                        )
                    }
                )
            }

            entry<GithubStoreGraph.DetailsScreen> { args ->
                DetailsRoot(
                    onNavigateBack = {
                        navBackStack.removeLastOrNull()
                    },
                    onOpenRepositoryInApp = { repoId ->
                        navBackStack.add(
                            GithubStoreGraph.DetailsScreen(
                                repositoryId = repoId
                            )
                        )
                    },
                    viewModel = koinViewModel {
                        parametersOf(args.repositoryId)
                    }
                )
            }

            entry<GithubStoreGraph.AuthenticationScreen> {
                AuthenticationRoot(
                    onNavigateToHome = {
                        navBackStack.clear()
                        navBackStack.add(GithubStoreGraph.HomeScreen)
                    }
                )
            }

            entry<GithubStoreGraph.SettingsScreen> {
                SettingsRoot(
                    onNavigateBack = {
                        navBackStack.removeLastOrNull()
                    }
                )
            }
        },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        transitionSpec = {
            slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = spring(Spring.DampingRatioLowBouncy)
            ) togetherWith slideOutHorizontally(
                targetOffsetX = { -it },
                animationSpec = spring(Spring.DampingRatioLowBouncy)
            )
        },
        popTransitionSpec = {
            slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = spring(Spring.DampingRatioLowBouncy)
            ) togetherWith slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = spring(Spring.DampingRatioLowBouncy)
            )
        },
        predictivePopTransitionSpec = {
            slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = spring(Spring.DampingRatioLowBouncy)
            ) togetherWith slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = spring(Spring.DampingRatioLowBouncy)
            )
        }
    )
}
