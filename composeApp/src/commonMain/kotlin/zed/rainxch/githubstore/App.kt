package zed.rainxch.githubstore

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import zed.rainxch.githubstore.app.navigation.AppNavigation
import zed.rainxch.githubstore.core.presentation.theme.GithubStoreTheme

@Composable
@Preview
fun App(
    onAuthenticationChecked: () -> Unit = { },
) {
    val viewModel: MainViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    GithubStoreTheme(
        appTheme = state.currentColorTheme
    ) {
        AppNavigation(
            onAuthenticationChecked = onAuthenticationChecked,
            state = state,
            onAction = viewModel::onAction
        )
    }
}