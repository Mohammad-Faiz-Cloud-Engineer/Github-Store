package zed.rainxch.githubstore.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import zed.rainxch.githubstore.feature.auth.data.DefaultTokenStore
import zed.rainxch.githubstore.feature.auth.data.DeviceTokenSuccess
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * TokenDataSource is the source of truth for auth tokens.
 * It persists tokens and exposes a StateFlow so presentation/data can react without
 * relying on in-memory singletons. The backing store is platform-specific.
 */
interface TokenDataSource {
    val tokenFlow: StateFlow<DeviceTokenSuccess?>
    suspend fun save(token: DeviceTokenSuccess)
    suspend fun load(): DeviceTokenSuccess?
    suspend fun clear()

    /** Synchronous snapshot of the latest token (may be null). */
    fun current(): DeviceTokenSuccess?
}

/**
 * Default implementation that delegates to existing platform DefaultTokenStore (persisted),
 * and mirrors updates into an in-memory StateFlow sourced from persistence at startup.
 */

@OptIn(ExperimentalAtomicApi::class)
class DefaultTokenDataSource(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : TokenDataSource {
    private val _flow = MutableStateFlow<DeviceTokenSuccess?>(null)
    override val tokenFlow: StateFlow<DeviceTokenSuccess?> = _flow
    private val isInitialized = AtomicBoolean(false)

    init {
        scope.launch {
            val token = DefaultTokenStore.load()
            _flow.value = token
            isInitialized.store(true)
        }
    }

    override suspend fun save(token: DeviceTokenSuccess) {
        DefaultTokenStore.save(token)
        _flow.value = token
    }

    override suspend fun load(): DeviceTokenSuccess? = DefaultTokenStore.load()

    override suspend fun clear() {
        DefaultTokenStore.clear()
        _flow.value = null
    }

    override fun current(): DeviceTokenSuccess? = _flow.value
}
