package com.macarmusic.app.lyrics

import java.io.IOException

internal class LyricSearchFailure(val answeredRoute: Boolean, cause: IOException) : IOException(cause.message, cause)

data class OnlineLyricCandidate(
    val key: LyricCandidateId,
    val title: String,
    val artist: String,
    val durationMs: Long = 0,
    internal val accessKey: String = "",
)

data class OnlineLyricCatalog(
    val route: LyricSearchRoute,
    val candidates: List<OnlineLyricCandidate> = emptyList(),
    val failed: Boolean = false,
)

enum class OnlineLyricIssue { SEARCH_FAILED, DOWNLOAD_FAILED, NO_LYRICS }

data class OnlineLyricSearch(
    val trackId: String,
    val title: String,
    val generation: Long,
    val candidates: List<OnlineLyricCandidate> = emptyList(),
    val completed: Set<LyricSearchRoute> = emptySet(),
    val failed: Set<LyricSearchRoute> = emptySet(),
    val busyKey: LyricCandidateId? = null,
    val issue: OnlineLyricIssue? = null,
    val trackDurationMs: Long = 0,
) {
    val searching: Boolean get() = completed.size < OnlineLyricsClient.ROUTES.size

    fun append(catalog: OnlineLyricCatalog) = copy(
        candidates = (candidates + catalog.candidates).distinctBy { it.key },
        completed = completed + catalog.route,
        failed = if (catalog.failed) failed + catalog.route else failed,
    )
}
