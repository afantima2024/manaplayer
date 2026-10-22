package com.macarmusic.app.lyrics

import com.macarmusic.app.model.Track
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

enum class LyricDownloadPhase { PREPARING, DOWNLOADING, CANCELLING, COMPLETE, CANCELLED, FAILED }
enum class LyricDownloadResult { SAVED, KEPT, NO_MATCH, SEARCH_FAILED, PARTIAL_FAILURE }

data class LyricDownloadState(
    val title: String,
    val phase: LyricDownloadPhase = LyricDownloadPhase.PREPARING,
    val total: Int = 0,
    val completed: Int = 0,
    val saved: Int = 0,
    val kept: Int = 0,
    val unmatched: Int = 0,
    val failed: Int = 0,
    val current: String = "",
    val found: Int = 0,
    val error: String? = null,
) {
    val running: Boolean get() = phase == LyricDownloadPhase.PREPARING ||
        phase == LyricDownloadPhase.DOWNLOADING || phase == LyricDownloadPhase.CANCELLING
}

internal object LyricDownloadBatch {
    suspend fun run(
        tracks: List<Track>,
        initial: LyricDownloadState,
        download: suspend (Track) -> LyricDownloadResult,
        progress: (LyricDownloadState) -> Unit,
    ): LyricDownloadState {
        val unique = tracks.distinctBy { it.id }
        var state = initial.copy(phase = LyricDownloadPhase.DOWNLOADING, total = unique.size)
        var consecutiveFailures = 0
        progress(state)
        for (track in unique) {
            currentCoroutineContext().ensureActive()
            state = state.copy(current = track.title)
            progress(state)
            val result = download(track)
            state = state.copy(
                completed = state.completed + 1,
                saved = state.saved + if (result == LyricDownloadResult.SAVED) 1 else 0,
                kept = state.kept + if (result == LyricDownloadResult.KEPT) 1 else 0,
                unmatched = state.unmatched + if (result == LyricDownloadResult.NO_MATCH) 1 else 0,
                failed = state.failed + if (result == LyricDownloadResult.SEARCH_FAILED ||
                    result == LyricDownloadResult.PARTIAL_FAILURE) 1 else 0,
            )
            consecutiveFailures = when (result) {
                LyricDownloadResult.SEARCH_FAILED -> consecutiveFailures + 1
                LyricDownloadResult.KEPT -> consecutiveFailures
                else -> 0
            }
            progress(state)
            if (consecutiveFailures >= 3) return state.copy(phase = LyricDownloadPhase.FAILED)
        }
        return state.copy(phase = LyricDownloadPhase.COMPLETE, current = "")
    }
}
