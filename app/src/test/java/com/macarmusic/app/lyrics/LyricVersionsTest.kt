package com.macarmusic.app.lyrics

import com.macarmusic.app.model.LyricCacheExpiry
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class LyricVersionsTest {
    private val now = 100L * 86_400_000
    private fun lyric(number: Int, origin: LyricOrigin = LyricOrigin.QQ, saved: Long = now) =
        CachedLyric("[00:01]Synthetic version $number\n[00:02]Second marker", origin, saved)

    @Test fun identityKeepsWordsSourceAndSourceTimingButIgnoresUserTimingAndAge() {
        val value = lyric(1)
        assertEquals(value.version().id, value.copy(offsetMs = -1000, savedAtMs = 10, name = "renamed").version().id)
        assertNotEquals(value.version().id, value.copy(origin = LyricOrigin.KUWO).version().id)
        assertNotEquals(value.version().id, lyric(2).version().id)
        assertNotEquals(value.version().id, value.copy(text = value.text.replace("00:01", "00:03")).version().id)
    }

    @Test fun legacySingleVersionsRemainAutomaticAndDoNotAcquireAnInventedBest() {
        val value = LyricCacheFiles.decode("""{"text":"[00:01]Legacy","origin":"QQ","selected":true}""".toByteArray())
        assertTrue(value.selected)
        assertTrue(value.automatic)
        assertFalse(value.offlineSelected)
        assertTrue(value.versions.isEmpty())
        assertNull(value.bestVersionId)
        assertEquals(value, value.preferred())
    }

    @Test fun mergingRetainsPreviousTextAndTimingWithoutRecursivelyEmbeddingRecords() {
        val first = lyric(1).copy(offsetMs = 500, name = "First")
        val second = LyricVersions.merge(lyric(2), first, first.version().id)
        val third = LyricVersions.merge(lyric(3), second)
        val restored = LyricCacheFiles.decode(LyricCacheFiles.encode(third))
        assertEquals(third, restored)
        assertEquals(listOf(second.version().id, first.version().id), restored.versions.map { it.id })
        assertEquals(first.text, restored.preferred().text)
        assertEquals(500L, restored.preferred().offsetMs)
        assertTrue(restored.preferred().result(cached = true).availableOffline)
    }

    @Test fun repeatedSelectionsDeduplicateAndKeepTheNewTimingForThatVersion() {
        val first = lyric(1)
        val second = LyricVersions.merge(lyric(2), first)
        val selected = LyricVersions.merge(first.copy(offsetMs = 800), second)
        assertEquals(1, selected.versions.size)
        assertEquals(second.text, selected.versions.single().text)
        assertEquals(800L, selected.offsetMs)
        assertNull(selected.bestVersionId)
    }

    @Test fun markingAnExistingBestKeepsVersionOrderAndUpdatesItsTiming() {
        val second = lyric(2)
        val third = lyric(3)
        val previous = lyric(1).copy(versions = listOf(second.version(), third.version()))
        val changed = previous.copy(versions = listOf(third.copy(offsetMs = 700).version()) + previous.versions)
        val saved = LyricVersions.merge(changed, previous, third.version().id)
        assertEquals(listOf(second.version().id, third.version().id), saved.versions.map { it.id })
        assertEquals(700L, saved.preferred().offsetMs)
        assertEquals(previous.text, saved.text)
    }

    @Test fun removingAnAlternativePreservesCurrentBestTimingAgeAndOrder() {
        val second = lyric(2).copy(offsetMs = 800, savedAtMs = now - 1000)
        val third = lyric(3)
        val fourth = lyric(4)
        val value = lyric(1).copy(offsetMs = -300, selected = true,
            versions = listOf(second.version(), third.version(), fourth.version()), bestVersionId = second.version().id)
        val removed = requireNotNull(LyricVersions.remove(value, third.version().id))
        assertEquals(value.copy(versions = listOf(second.version(), fourth.version())), removed)
        assertEquals(second.savedAtMs, removed.preferred().savedAtMs)
        assertEquals(800L, removed.preferred().offsetMs)
        assertEquals(removed, LyricCacheFiles.decode(LyricCacheFiles.encode(removed)))
    }

    @Test fun removingBestClearsOnlyItsPreferenceAndKeepsOtherVersions() {
        val second = lyric(2)
        val third = lyric(3)
        val value = lyric(1).copy(versions = listOf(second.version(), third.version()), bestVersionId = second.version().id)
        assertEquals(value.copy(versions = listOf(third.version()), bestVersionId = null),
            LyricVersions.remove(value, second.version().id))
    }

    @Test fun removingPrimaryPromotesRemainingBestWithoutRenewingAgeOrLosingHistory() {
        val second = lyric(2).copy(offsetMs = -900, savedAtMs = now - 10_000)
        val third = lyric(3)
        val history = LyricSearchHistory(contents = listOf("a".repeat(64)))
        val value = lyric(1).copy(versions = listOf(third.version(), second.version()),
            bestVersionId = second.version().id, history = history)
        val removed = requireNotNull(LyricVersions.remove(value, value.version().id))
        assertEquals(second.version(), removed.version())
        assertEquals(second.version().id, removed.bestVersionId)
        assertEquals(listOf(third.version()), removed.versions)
        assertEquals(history, removed.history)
        assertEquals(removed, LyricCacheFiles.decode(LyricCacheFiles.encode(removed)))
    }

    @Test fun removingPrimaryBestPromotesFirstRemainingAndClearsPreference() {
        val second = lyric(2, LyricOrigin.LOCAL_FILE).copy(name = "Synthetic.lrc", sourceUri = "content://fixture/lyrics")
        val value = lyric(1).let { it.copy(versions = listOf(second.version()), bestVersionId = it.version().id, automatic = false) }
        val removed = requireNotNull(LyricVersions.remove(value, value.version().id))
        assertEquals(second.version(), removed.version())
        assertNull(removed.bestVersionId)
        assertTrue(removed.versions.isEmpty())
        assertFalse(removed.automatic)
        assertTrue(removed.offlineSelected)
        assertEquals(removed, LyricCacheFiles.decode(LyricCacheFiles.encode(removed)))
    }

    @Test fun removingLastVersionDeletesTheRecordButMissingIdsAreNotSuccess() {
        val value = lyric(1).let { it.copy(bestVersionId = it.version().id) }
        assertNull(LyricVersions.remove(value, value.version().id))
        assertThrows(IOException::class.java) { LyricVersions.remove(value, lyric(2).version().id) }
    }

    @Test fun byteBudgetDropsOldAlternativesButProtectsCurrentAndBest() {
        fun large(number: Int) = lyric(number).copy(
            text = (0 until 150).joinToString("\n") { "[00:01]Synthetic $number ${"x".repeat(1400)}" },
            savedAtMs = now + number,
        )
        val best = large(1)
        val current = LyricVersions.merge(large(2), best, best.version().id)
        val next = LyricVersions.merge(large(3), current)
        assertTrue(LyricCacheFiles.encode(next).size <= LyricCacheFiles.MAX_RECORD_BYTES)
        assertEquals(listOf(best.version().id), next.versions.map { it.id })
        assertEquals(best.text, next.preferred().text)
        assertEquals(large(3).text, next.text)
    }

    @Test fun countBudgetNeverEvictsTheBestAndRemovesOldestUnprotectedVersions() {
        var record = lyric(1)
        val best = record.version().id
        for (number in 2..105) record = LyricVersions.merge(lyric(number, saved = now + number), record, best)
        assertEquals(LyricVersions.MAX_VERSIONS - 1, record.versions.size)
        assertEquals(best, record.preferred().version().id)
        assertFalse(record.versions.any { it.id == lyric(2).version().id })
        assertEquals(record, LyricCacheFiles.decode(LyricCacheFiles.encode(record)))
    }

    @Test fun temporarySelectionCannotBeEvictedWhileKeepingTheDefaultAndBest() {
        fun large(number: Int) = lyric(number).copy(
            text = (0 until 150).joinToString("\n") { "[00:01]Synthetic $number ${"x".repeat(1400)}" },
            savedAtMs = now + number,
        )
        val selected = large(1)
        val old = large(2)
        val primary = large(3)
        val previous = LyricVersions.merge(primary, old, primary.version().id)
        val incoming = previous.copy(versions = listOf(selected.version()) + previous.versions)
        val retained = LyricVersions.merge(incoming, previous, preserveVersionId = selected.version().id)
        assertEquals(primary.text, retained.text)
        assertEquals(listOf(selected.version().id), retained.versions.map { it.id })
        assertEquals(primary.version().id, retained.bestVersionId)
        assertTrue(LyricCacheFiles.encode(retained).size <= LyricCacheFiles.MAX_RECORD_BYTES)
        assertThrows(IOException::class.java) {
            LyricVersions.merge(incoming, previous, old.version().id, selected.version().id)
        }
    }

    @Test fun independentExpiryDropsOldBestWithoutDiscardingNewerLyricsOrRenewingTheirAge() {
        val old = lyric(1, saved = now - 40L * 86_400_000)
        val recent = lyric(2, saved = now - 1000)
        val record = LyricVersions.merge(recent, old, old.version().id)
        val retained = requireNotNull(LyricVersions.expire(record, LyricCacheExpiry.THIRTY_DAYS, now))
        assertNull(retained.bestVersionId)
        assertEquals(recent.savedAtMs, retained.savedAtMs)
        assertEquals(recent.text, retained.text)
        assertTrue(retained.versions.isEmpty())
        assertSame(record, LyricVersions.expire(record, LyricCacheExpiry.NEVER, now))
        assertNull(LyricVersions.expire(record, LyricCacheExpiry.THIRTY_DAYS, now + 31L * 86_400_000))
    }

    @Test fun anExpiredPrimaryCannotTurnATemporaryAlternativeIntoTheAutomaticDefault() {
        val old = lyric(1, saved = now - 40L * 86_400_000)
        val recent = lyric(2, LyricOrigin.LOCAL_FILE, now - 1000)
        val record = old.copy(versions = listOf(recent.version()))
        val retained = requireNotNull(LyricVersions.expire(record, LyricCacheExpiry.THIRTY_DAYS, now))
        assertEquals(recent.text, retained.text)
        assertFalse(retained.selected)
        assertFalse(retained.automatic)
        assertEquals(retained, LyricCacheFiles.decode(LyricCacheFiles.encode(retained)))
    }

    @Test fun invalidBestDuplicateVersionsAndOversizedBodiesAreRejected() {
        val value = lyric(1)
        assertThrows(IOException::class.java) { LyricCacheFiles.encode(value.copy(bestVersionId = "missing")) }
        assertThrows(IOException::class.java) { LyricCacheFiles.encode(value.copy(versions = listOf(value.version()))) }
        assertThrows(IOException::class.java) {
            LyricCacheFiles.encode(value.copy(versions = listOf(value.version().copy(offsetMs = 600_001))))
        }
        val invalid = LyricVersion("<html>Invalid</html>", LyricOrigin.KUWO)
        assertThrows(IOException::class.java) {
            LyricCacheFiles.encode(value.copy(versions = listOf(invalid), bestVersionId = invalid.id))
        }
    }

    @Test fun retainedLocalCopiesAreValidWithoutBecomingAutomaticSelections() {
        val value = lyric(1, LyricOrigin.LOCAL_FILE).copy(offlineSelected = true, automatic = false)
        assertFalse(value.selected)
        assertEquals(value, LyricCacheFiles.decode(LyricCacheFiles.encode(value)))
        assertTrue(value.result().availableOffline)
    }

    @Test fun offlineRetentionSurvivesSelectionsRemovalBestAndExpiryWithoutRenewingAge() {
        val first = lyric(1, saved = now - 40L * 86_400_000).copy(downloaded = true)
        val second = lyric(2)
        val merged = LyricVersions.merge(second, first, first.version().id)
        assertTrue(merged.downloaded)
        assertTrue(merged.preferred().downloaded)
        assertTrue(requireNotNull(LyricVersions.remove(merged, merged.version().id)).downloaded)
        val expired = requireNotNull(LyricVersions.expire(merged, LyricCacheExpiry.THIRTY_DAYS, now))
        assertTrue(expired.downloaded)
        assertEquals(second.savedAtMs, expired.savedAtMs)
        assertNull(LyricVersions.expire(expired, LyricCacheExpiry.THIRTY_DAYS, now + 31L * 86_400_000))
        assertEquals(expired, LyricCacheFiles.decode(LyricCacheFiles.encode(expired)))
    }
}
