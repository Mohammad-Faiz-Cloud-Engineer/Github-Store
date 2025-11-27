package zed.rainxch.githubstore.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import zed.rainxch.githubstore.MainViewModel
import zed.rainxch.githubstore.feature.auth.presentation.AuthenticationRoot
import zed.rainxch.githubstore.feature.details.presentation.DetailsRoot
import zed.rainxch.githubstore.feature.home.presentation.HomeRoot
import zed.rainxch.githubstore.feature.search.presentation.SearchRoot

@Composable
fun AppNavigation(
    onAuthenticationChecked: () -> Unit = { },
    mainViewModel: MainViewModel = koinViewModel()
) {
    val navHostController = rememberNavController()
    val state by mainViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.isCheckingAuth) {
        if (!state.isCheckingAuth) {
            onAuthenticationChecked()
        }
    }

    if (state.isCheckingAuth) {
        Box(modifier = Modifier.fillMaxSize())
        return
    }

    NavHost(
        navController = navHostController,
        startDestination = if (state.isLoggedIn) {
            GithubStoreGraph.HomeScreen
        } else GithubStoreGraph.AuthenticationScreen
    ) {
        composable<GithubStoreGraph.HomeScreen> {
            HomeRoot(
                onNavigateToSearch = {
                    navHostController.navigate(GithubStoreGraph.SearchScreen)
                },
                onNavigateToDetails = { repo ->
                    navHostController.navigate(
                        GithubStoreGraph.DetailsScreen(
                            repositoryId = repo.id.toInt()
                        )
                    )
                }
            )
        }

        composable<GithubStoreGraph.SearchScreen> {
            SearchRoot(
                onNavigateBack = {
                    navHostController.navigateUp()
                },
                onNavigateToDetails = { repo ->
                    navHostController.navigate(
                        GithubStoreGraph.DetailsScreen(
                            repositoryId = repo.id.toInt()
                        )
                    )
                }
            )
        }

        composable<GithubStoreGraph.DetailsScreen> { backStackEntry ->
            val args = backStackEntry.toRoute<GithubStoreGraph.DetailsScreen>()

            DetailsRoot(
                onNavigateBack = {
                    navHostController.navigateUp()
                },
                viewModel = koinViewModel {
                    parametersOf(args.repositoryId)
                }
            )
        }

        composable<GithubStoreGraph.AuthenticationScreen> {
            AuthenticationRoot(
                onNavigateToHome = {
                    navHostController.navigate(GithubStoreGraph.HomeScreen) {
                        popUpTo(GithubStoreGraph.AuthenticationScreen) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}