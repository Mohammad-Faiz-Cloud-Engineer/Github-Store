package zed.rainxch.githubstore.feature.details.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import zed.rainxch.githubstore.core.domain.Platform
import zed.rainxch.githubstore.core.domain.model.PlatformType
import zed.rainxch.githubstore.core.presentation.utils.BrowserHelper
import zed.rainxch.githubstore.feature.details.data.Downloader
import zed.rainxch.githubstore.feature.details.data.Installer
import zed.rainxch.githubstore.feature.details.domain.repository.DetailsRepository
import kotlin.time.Clock.System
import kotlin.time.ExperimentalTime

class DetailsViewModel(
    private val repositoryId: Int,
    private val detailsRepository: DetailsRepository,
    private val downloader: Downloader,
    private val installer: Installer,
    private val platform: Platform,
    private val helper: BrowserHelper
) : ViewModel() {

    private var hasLoadedInitialData = false
    private var currentDownloadJob: Job? = null
    private var currentAssetName: String? = null

    private val _state = MutableStateFlow(DetailsState())
    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                loadInitial()
                hasLoadedInitialData = true
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            DetailsState()
        )

    private val _events = Channel<DetailsEvent>()
    val events = _events.receiveAsFlow()

    private fun loadInitial() {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, errorMessage = null)

                val repo = detailsRepository.getRepositoryById(repositoryId.toLong())
                val owner = repo.owner.login
                val name = repo.name

                _state.value = _state.value.copy(repository = repo)

                val latestReleaseDeferred = async {
                    try {
                        detailsRepository.getLatestPublishedRelease(
                            owner = owner,
                            repo = name,
                            defaultBranch = repo.defaultBranch
                        )
                    } catch (t: Throwable) {
                        Logger.w { "Failed to load latest release: ${t.message}" }
                        null
                    }
                }

                val statsDeferred = async {
                    try {
                        detailsRepository.getRepoStats(owner, name)
                    } catch (t: Throwable) {
                        null
                    }
                }

                val readmeDeferred = async {
                    try {
                        detailsRepository.getReadme(
                            owner = owner,
                            repo = name,
                            defaultBranch = repo.defaultBranch
                        )
                    } catch (t: Throwable) {
                        null
                    }
                }

                val userProfileDeferred = async {
                    try {
                        detailsRepository.getUserProfile(owner)
                    } catch (t: Throwable) {
                        Logger.w { "Failed to load user profile: ${t.message}" }
                        null
                    }
                }

                val isObtainiumEnabled = platform.type == PlatformType.ANDROID
                val isAppManagerEnabled = platform.type == PlatformType.ANDROID

                val latestRelease = latestReleaseDeferred.await()
                val stats = statsDeferred.await()
                val readme = readmeDeferred.await()
                val userProfile = userProfileDeferred.await()

                val installable = latestRelease?.assets?.filter { asset ->
                    installer.isAssetInstallable(asset.name)
                }.orEmpty()

                val primary = installer.choosePrimaryAsset(installable)

                val isObtainiumAvailable = installer.isObtainiumInstalled()
                val isAppManagerAvailable = installer.isAppManagerInstalled()

                println(repo.id)

                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = null,
                    repository = repo,
                    latestRelease = latestRelease,
                    stats = stats,
                    readmeMarkdown = readme,
                    installableAssets = installable,
                    primaryAsset = primary,
                    userProfile = userProfile,
                    systemArchitecture = installer.detectSystemArchitecture(),
                    isObtainiumAvailable = isObtainiumAvailable,
                    isObtainiumEnabled = isObtainiumEnabled,
                    isAppManagerAvailable = isAppManagerAvailable,
                    isAppManagerEnabled = isAppManagerEnabled
                )
            } catch (t: Throwable) {
                Logger.e { "Details load failed: ${t.message}" }
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = t.message ?: "Failed to load details"
                )
            }
        }
    }

    fun onAction(action: DetailsAction) {
        when (action) {
            DetailsAction.Retry -> {
                hasLoadedInitialData = false
                loadInitial()
            }

            DetailsAction.InstallPrimary -> {
                val primary = _state.value.primaryAsset
                val release = _state.value.latestRelease
                if (primary != null && release != null) {
                    installAsset(
                        downloadUrl = primary.downloadUrl,
                        assetName = primary.name,
                        sizeBytes = primary.size,
                        releaseTag = release.tagName
                    )
                }
            }

            is DetailsAction.DownloadAsset -> {
                val release = _state.value.latestRelease
                downloadAsset(
                    downloadUrl = action.downloadUrl,
                    assetName = action.assetName,
                    sizeBytes = action.sizeBytes,
                    releaseTag = release?.tagName ?: ""
                )
            }

            DetailsAction.CancelCurrentDownload -> {
                currentDownloadJob?.cancel()
                currentDownloadJob = null

                val assetName = currentAssetName
                if (assetName != null) {
                    viewModelScope.launch {
                        try {
                            val deleted = downloader.cancelDownload(assetName)
                            Logger.d { "Cancel download - file deleted: $deleted" }

                            appendLog(
                                assetName = assetName,
                                size = 0L,
                                tag = _state.value.latestRelease?.tagName ?: "",
                                result = "Cancelled"
                            )
                        } catch (t: Throwable) {
                            Logger.e { "Failed to cancel download: ${t.message}" }
                        }
                    }
                }

                currentAssetName = null
                _state.value = _state.value.copy(
                    isDownloading = false,
                    downloadProgressPercent = null,
                    downloadStage = DownloadStage.IDLE
                )
            }

            DetailsAction.OpenRepoInBrowser -> {
                _state.value.repository?.htmlUrl?.let {
                    helper.openUrl(url = it)
                }
            }

            DetailsAction.OpenAuthorInBrowser -> {
                _state.value.userProfile?.htmlUrl?.let {
                    helper.openUrl(url = it)
                }
            }

            DetailsAction.OpenInObtainium -> {
                val repo = _state.value.repository
                repo?.owner?.login?.let {
                    installer.openInObtainium(
                        repoOwner = it,
                        repoName = repo.name,
                        onOpenInstaller = {
                            viewModelScope.launch {
                                _events.send(
                                    DetailsEvent.OnOpenRepositoryInApp(OBTAINIUM_REPO_ID)
                                )
                            }
                        }
                    )
                }
                _state.update {
                    it.copy(isInstallDropdownExpanded = false)
                }
            }

            DetailsAction.OpenInAppManager -> {
                viewModelScope.launch {
                    try {
                        val primary = _state.value.primaryAsset
                        val release = _state.value.latestRelease

                        if (primary != null && release != null) {
                            currentAssetName = primary.name

                            appendLog(
                                primary.name,
                                primary.size,
                                release.tagName,
                                "Preparing for AppManager"
                            )

                            _state.value = _state.value.copy(
                                downloadError = null,
                                installError = null,
                                downloadProgressPercent = null,
                                downloadStage = DownloadStage.DOWNLOADING
                            )

                            downloader.download(primary.downloadUrl, primary.name).collect { p ->
                                _state.value =
                                    _state.value.copy(downloadProgressPercent = p.percent)
                                if (p.percent == 100) {
                                    _state.value =
                                        _state.value.copy(downloadStage = DownloadStage.VERIFYING)
                                }
                            }

                            val filePath = downloader.getDownloadedFilePath(primary.name)
                                ?: throw IllegalStateException("Downloaded file not found")

                            appendLog(primary.name, primary.size, release.tagName, "Downloaded")

                            _state.value = _state.value.copy(downloadStage = DownloadStage.IDLE)
                            currentAssetName = null

                            installer.openInAppManager(
                                filePath = filePath,
                                onOpenInstaller = {
                                    viewModelScope.launch {
                                        _events.send(
                                            DetailsEvent.OnOpenRepositoryInApp(APP_MANAGER_REPO_ID)
                                        )
                                    }
                                }
                            )

                            appendLog(
                                assetName = primary.name,
                                size = primary.size,
                                tag = release.tagName,
                                result = "Opened in AppManager"
                            )
                        }
                    } catch (t: Throwable) {
                        Logger.e { "Failed to open in AppManager: ${t.message}" }
                        _state.value = _state.value.copy(
                            downloadStage = DownloadStage.IDLE,
                            installError = t.message
                        )
                        currentAssetName = null

                        _state.value.primaryAsset?.let { asset ->
                            _state.value.latestRelease?.let { release ->
                                appendLog(
                                    asset.name,
                                    asset.size,
                                    release.tagName,
                                    "Error: ${t.message}"
                                )
                            }
                        }
                    }
                }
                _state.update {
                    it.copy(isInstallDropdownExpanded = false)
                }
            }

            DetailsAction.OnNavigateBackClick -> { /* handled in UI host */
            }

            is DetailsAction.OpenAuthorInApp -> { /* handled in UI host */
            }

            DetailsAction.OnToggleInstallDropdown -> {
                _state.update {
                    it.copy(isInstallDropdownExpanded = !it.isInstallDropdownExpanded)
                }
            }
        }
    }

    private fun installAsset(
        downloadUrl: String,
        assetName: String,
        sizeBytes: Long,
        releaseTag: String
    ) {
        currentDownloadJob?.cancel()
        currentDownloadJob = viewModelScope.launch {
            try {
                currentAssetName = assetName

                appendLog(assetName, sizeBytes, releaseTag, "DownloadStarted")
                _state.value = _state.value.copy(
                    downloadError = null,
                    installError = null,
                    downloadProgressPercent = null
                )

                installer.ensurePermissionsOrThrow(
                    assetName.substringAfterLast('.', "").lowercase()
                )

                _state.value = _state.value.copy(downloadStage = DownloadStage.DOWNLOADING)

                var filePath: String? = null
                downloader.download(downloadUrl, assetName).collect { p ->
                    _state.value = _state.value.copy(downloadProgressPercent = p.percent)
                    if (p.percent == 100) {
                        _state.value = _state.value.copy(downloadStage = DownloadStage.VERIFYING)
                    }
                }

                filePath = downloader.getDownloadedFilePath(assetName)
                    ?: throw IllegalStateException("Downloaded file not found")

                appendLog(assetName, sizeBytes, releaseTag, "Downloaded")

                _state.value = _state.value.copy(downloadStage = DownloadStage.INSTALLING)
                val ext = assetName.substringAfterLast('.', "").lowercase()

                if (!installer.isSupported(ext)) {
                    throw IllegalStateException("Asset type .$ext not supported")
                }

                installer.install(filePath, ext)

                _state.value = _state.value.copy(downloadStage = DownloadStage.IDLE)
                currentAssetName = null
                appendLog(assetName, sizeBytes, releaseTag, "Installed")

            } catch (t: Throwable) {
                Logger.e { "Install failed: ${t.message}" }
                _state.value = _state.value.copy(
                    downloadStage = DownloadStage.IDLE,
                    installError = t.message
                )
                currentAssetName = null
                appendLog(assetName, sizeBytes, releaseTag, "Error: ${t.message}")
            }
        }
    }

    private fun downloadAsset(
        downloadUrl: String,
        assetName: String,
        sizeBytes: Long,
        releaseTag: String
    ) {
        currentDownloadJob?.cancel()
        currentDownloadJob = viewModelScope.launch {
            try {
                currentAssetName = assetName

                appendLog(assetName, sizeBytes, releaseTag, "DownloadStarted")
                _state.value = _state.value.copy(
                    isDownloading = true,
                    downloadError = null,
                    installError = null,
                    downloadProgressPercent = null
                )

                downloader.download(downloadUrl, assetName).collect { p ->
                    _state.value = _state.value.copy(downloadProgressPercent = p.percent)
                }

                _state.value = _state.value.copy(isDownloading = false)
                currentAssetName = null
                appendLog(assetName, sizeBytes, releaseTag, "Downloaded")

            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    isDownloading = false,
                    downloadError = t.message
                )
                currentAssetName = null
                appendLog(assetName, sizeBytes, releaseTag, "Error: ${t.message}")
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun appendLog(assetName: String, size: Long, tag: String, result: String) {
        val now = System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            .format(LocalDateTime.Format {
                year()
                char('-')
                monthNumber()
                char('-')
                day()
                char(' ')
                hour()
                char(':')
                minute()
                char(':')
                second()
            })
        val newItem = InstallLogItem(
            timeIso = now,
            assetName = assetName,
            assetSizeBytes = size,
            releaseTag = tag,
            result = result
        )
        _state.value = _state.value.copy(
            installLogs = listOf(newItem) + _state.value.installLogs
        )
    }

    override fun onCleared() {
        super.onCleared()
        currentDownloadJob?.cancel()

        currentAssetName?.let { assetName ->
            viewModelScope.launch {
                downloader.cancelDownload(assetName)
            }
        }
    }

    private companion object {
        const val OBTAINIUM_REPO_ID = 523534328
        const val APP_MANAGER_REPO_ID = 268006778
    }
}