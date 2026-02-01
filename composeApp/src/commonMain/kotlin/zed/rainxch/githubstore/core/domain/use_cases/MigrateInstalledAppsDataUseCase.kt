package zed.rainxch.githubstore.core.domain.use_cases

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.first
import zed.rainxch.githubstore.core.data.services.PackageMonitor
import zed.rainxch.githubstore.core.domain.Platform
import zed.rainxch.githubstore.core.domain.model.PlatformType
import zed.rainxch.githubstore.core.domain.repository.InstalledAppsRepository

/**
 * Use case for migrating installed apps data from legacy format to new format.
 * Removes uninstalled apps, migrates version fields, and syncs with system package manager.
 */
class MigrateInstalledAppsDataUseCase(
    private val packageMonitor: PackageMonitor,
    private val installedAppsRepository: InstalledAppsRepository,
    private val platform: Platform
) {
    /**
     * Executes the migration process.
     * 
     * @return Result indicating success or failure
     */
    suspend operator fun invoke(): Result<Unit> {
        return try {
            val installedPackageNames = packageMonitor.getAllInstalledPackageNames()
            val appsInDb = installedAppsRepository.getAllInstalledApps().first()

            appsInDb.forEach { app ->
                when {
                    // Remove apps no longer installed on system
                    app.packageName !in installedPackageNames -> {
                        installedAppsRepository.deleteInstalledApp(app.packageName)
                    }
                    
                    // Migrate apps with legacy version format
                    app.installedVersionName == null -> {
                        migrateAppVersionData(app.packageName)
                    }
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e(e) { "Failed to migrate installed apps data" }
            Result.failure(e)
        }
    }

    /**
     * Migrates version data for a single app.
     * On Android, attempts to get version from system package manager.
     * On other platforms, uses the legacy tag as version name.
     */
    private suspend fun migrateAppVersionData(packageName: String) {
        val app = installedAppsRepository.getAppByPackage(packageName) ?: return

        if (platform.type == PlatformType.ANDROID) {
            val systemInfo = packageMonitor.getInstalledPackageInfo(packageName)
            if (systemInfo != null) {
                // Use system package info
                installedAppsRepository.updateApp(
                    app.copy(
                        installedVersionName = systemInfo.versionName,
                        installedVersionCode = systemInfo.versionCode,
                        latestVersionName = systemInfo.versionName,
                        latestVersionCode = systemInfo.versionCode
                    )
                )
            } else {
                // Fallback to legacy tag
                installedAppsRepository.updateApp(
                    app.copy(
                        installedVersionName = app.installedVersion,
                        installedVersionCode = 0L,
                        latestVersionName = app.installedVersion,
                        latestVersionCode = 0L
                    )
                )
            }
        } else {
            // Desktop platforms: use legacy tag as version name
            installedAppsRepository.updateApp(
                app.copy(
                    installedVersionName = app.installedVersion,
                    installedVersionCode = 0L,
                    latestVersionName = app.installedVersion,
                    latestVersionCode = 0L
                )
            )
        }
    }
}
