package zed.rainxch.githubstore.feature.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import githubstore.composeapp.generated.resources.Res
import githubstore.composeapp.generated.resources.home_failed_to_load_repositories
import githubstore.composeapp.generated.resources.no_repositories_found
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import zed.rainxch.githubstore.core.domain.Platform
import zed.rainxch.githubstore.core.domain.model.PlatformType
import zed.rainxch.githubstore.core.domain.repository.FavouritesRepository
import zed.rainxch.githubstore.core.domain.repository.InstalledAppsRepository
import zed.rainxch.githubstore.core.domain.repository.StarredRepository
import zed.rainxch.githubstore.core.domain.use_cases.SyncInstalledAppsUseCase
import zed.rainxch.githubstore.core.presentation.model.DiscoveryRepository
import zed.rainxch.githubstore.feature.home.domain.repository.HomeRepository
import zed.rainxch.githubstore.feature.home.presentation.model.HomeCategory

/**
 * ViewModel for the Home screen.
 * 
 * Manages loading trending, new, and recently updated repositories with pagination,
 * syncing installed apps status, and observing favorites and starred repos.
 */
class HomeViewModel(
    private val homeRepository: HomeRepository,
    private val installedAppsRepository: InstalledAppsRepository,
    private val platform: Platform,
    private val syncInstalledAppsUseCase: SyncInstalledAppsUseCase,
    private val favouritesRepository: FavouritesRepository,
    private val starredRepository: StarredRepository,
) : ViewModel() {

    private var hasLoadedInitialData = false
    private var currentJob: Job? = null
    private var nextPageIndex = 1

    private val _state = MutableStateFlow(HomeState())
    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                syncSystemState()
                loadPlatform()
                loadRepos(isInitial = true)
                observeRepositoryStatus()
                hasLoadedInitialData = true
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = HomeState()
        )

    /**
     * Syncs system state with database on startup.
     * Runs in background without blocking UI.
     */
    private fun syncSystemState() {
        viewModelScope.launch {
            try {
                syncInstalledAppsUseCase()
            } catch (e: Exception) {
                // Silent fail - non-critical operation
            }
        }
    }

    private fun loadPlatform() {
        _state.update {
            it.copy(isAppsSectionVisible = platform.type == PlatformType.ANDROID)
        }
    }

    /**
     * Observes installed apps, favorites, and starred repos with a single combined flow.
     */
    private fun observeRepositoryStatus() {
        viewModelScope.launch {
            combine(
                installedAppsRepository.getAllInstalledApps(),
                favouritesRepository.getAllFavorites(),
                starredRepository.getAllStarred()
            ) { installedApps, favorites, starred ->
                Triple(
                    installedApps.associateBy { it.repoId },
                    favorites.associateBy { it.repoId },
                    starred.associateBy { it.repoId }
                )
            }.collect { (installedMap, favoritesMap, starredMap) ->
                _state.update { current ->
                    current.copy(
                        repos = current.repos.map { homeRepo ->
                            val app = installedMap[homeRepo.repository.id]
                            homeRepo.copy(
                                isInstalled = app != null,
                                isUpdateAvailable = app?.isUpdateAvailable ?: false,
                                isFavourite = homeRepo.repository.id in favoritesMap,
                                isStarred = homeRepo.repository.id in starredMap
                            )
                        },
                        isUpdateAvailable = installedMap.values.any { it.isUpdateAvailable }
                    )
                }
            }
        }
    }

    /**
     * Loads repositories for the current category with pagination.
     * 
     * @param isInitial If true, resets pagination and clears existing repos
     * @param category Optional category to switch to
     */
    private fun loadRepos(isInitial: Boolean = false, category: HomeCategory? = null) {
        if (_state.value.isLoading || _state.value.isLoadingMore) {
            return
        }

        currentJob?.cancel()

        if (isInitial) {
            nextPageIndex = 1
        }

        val targetCategory = category ?: _state.value.currentCategory

        currentJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = isInitial,
                    isLoadingMore = !isInitial,
                    errorMessage = null,
                    currentCategory = targetCategory,
                    repos = if (isInitial) emptyList() else it.repos
                )
            }

            try {
                val flow = when (targetCategory) {
                    HomeCategory.TRENDING -> homeRepository.getTrendingRepositories(nextPageIndex)
                    HomeCategory.NEW -> homeRepository.getNew(nextPageIndex)
                    HomeCategory.RECENTLY_UPDATED -> homeRepository.getRecentlyUpdated(nextPageIndex)
                }

                flow.collect { paginatedRepos ->
                    this@HomeViewModel.nextPageIndex = paginatedRepos.nextPageIndex

                    // Fetch all status data in parallel
                    val installedAppsMap = installedAppsRepository
                        .getAllInstalledApps()
                        .first()
                        .associateBy { it.repoId }

                    val favoritesMap = favouritesRepository
                        .getAllFavorites()
                        .first()
                        .associateBy { it.repoId }

                    val starredReposMap = starredRepository
                        .getAllStarred()
                        .first()
                        .associateBy { it.repoId }

                    val newReposWithStatus = paginatedRepos.repos.map { repo ->
                        val app = installedAppsMap[repo.id]
                        DiscoveryRepository(
                            isInstalled = app != null,
                            isFavourite = repo.id in favoritesMap,
                            isStarred = repo.id in starredReposMap,
                            isUpdateAvailable = app?.isUpdateAvailable ?: false,
                            repository = repo
                        )
                    }

                    _state.update { currentState ->
                        val rawList = currentState.repos + newReposWithStatus
                        val uniqueList = rawList.distinctBy { it.repository.fullName }

                        currentState.copy(
                            repos = uniqueList,
                            hasMorePages = paginatedRepos.hasMore,
                            errorMessage = if (uniqueList.isEmpty() && !paginatedRepos.hasMore) {
                                getString(Res.string.no_repositories_found)
                            } else null
                        )
                    }
                }

                _state.update {
                    it.copy(isLoading = false, isLoadingMore = false)
                }

            } catch (t: Throwable) {
                if (t is CancellationException) {
                    throw t
                }

                Logger.e { "Load failed: ${t.message}" }
                _state.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        errorMessage = t.message
                            ?: getString(Res.string.home_failed_to_load_repositories)
                    )
                }
            }
        }
    }

    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.Refresh -> {
                viewModelScope.launch {
                    syncInstalledAppsUseCase()
                    nextPageIndex = 1
                    loadRepos(isInitial = true)
                }
            }

            HomeAction.Retry -> {
                nextPageIndex = 1
                loadRepos(isInitial = true)
            }

            HomeAction.LoadMore -> {
                if (!_state.value.isLoadingMore && !_state.value.isLoading && _state.value.hasMorePages) {
                    loadRepos(isInitial = false)
                }
            }

            is HomeAction.SwitchCategory -> {
                if (_state.value.currentCategory != action.category) {
                    nextPageIndex = 1
                    loadRepos(isInitial = true, category = action.category)
                }
            }

            // Navigation actions handled by composable
            is HomeAction.OnRepositoryClick,
            is HomeAction.OnRepositoryDeveloperClick,
            HomeAction.OnSearchClick,
            HomeAction.OnSettingsClick,
            HomeAction.OnAppsClick -> Unit
        }
    }

    override fun onCleared() {
        super.onCleared()
        currentJob?.cancel()
    }
}
