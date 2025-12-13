package zed.rainxch.githubstore.app

import zed.rainxch.githubstore.network.RateLimitInfo

data class AppState(
    val rateLimitInfo: RateLimitInfo? = null,
    val showRateLimitDialog: Boolean = false,
    val isAuthenticated: Boolean = false
)
