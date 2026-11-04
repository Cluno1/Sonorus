/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.cluno1.sonorus.features.local.presentation.viewmodel

import kotlinx.coroutines.CancellationException

/** A failed metadata request must not escape the playback ViewModel's main-thread coroutine. */
internal suspend fun guardPublicMusicLoad(
    onFailure: (Exception) -> Unit,
    load: suspend () -> Unit,
) {
    try {
        load()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        onFailure(failure)
    }
}
