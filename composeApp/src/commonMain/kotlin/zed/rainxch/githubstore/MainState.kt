package zed.rainxch.githubstore

import zed.rainxch.githubstore.core.presentation.model.AppTheme
import zed.rainxch.githubstore.network.RateLimitInfo

data class MainState(
    val isCheckingAuth: Boolean = true,
    val isLoggedIn: Boolean = false,
    val rateLimitInfo: RateLimitInfo? = null,
    val showRateLimitDialog: Boolean = false,
    val currentColorTheme: AppTheme = AppTheme.OCEAN
)
