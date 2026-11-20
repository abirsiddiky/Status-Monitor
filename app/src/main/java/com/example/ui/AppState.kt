package com.example.ui

import androidx.compose.runtime.Immutable
import com.example.data.AppSettings
import com.example.data.model.ServerConfig

/**
 * Explicit startup and runtime states for the Status app:
 * - [Loading]: Initial state while the first DataStore read is in progress. The system splash screen is kept visible.
 * - [NoServer]: Emitted ONLY after DataStore has truly been read and the server list is genuinely empty.
 * - [Ready]: Emitted when one or more servers are configured, with the verified active server and loaded settings.
 */
@Immutable
sealed interface AppState {
    val settings: AppSettings get() = AppSettings()

    @Immutable
    data object Loading : AppState

    @Immutable
    data class NoServer(
        override val settings: AppSettings = AppSettings()
    ) : AppState

    @Immutable
    data class Ready(
        val servers: List<ServerConfig>,
        val activeServer: ServerConfig,
        override val settings: AppSettings = AppSettings()
    ) : AppState
}
