package com.macarmusic.app.playback

import org.junit.Assert.*
import org.junit.Test

class StartupPlaybackTest {
    @Test fun defaultOffIsNotArmedByChangingThePreferenceLater() {
        val startup = StartupPlayback()
        startup.ActivityLaunch().onCreate(normalLaunch = true, enabled = false)
        assertFalse(startup.pending)
        assertFalse(startup.consume(enabled = true))
    }

    @Test fun normalOptedInLaunchPlaysOnlyOnceAfterRestoration() {
        val startup = StartupPlayback()
        startup.ActivityLaunch().onCreate(normalLaunch = true, enabled = true)
        assertTrue(startup.pending)
        assertTrue(startup.consume(enabled = true))
        assertFalse(startup.pending)
        assertFalse(startup.consume(enabled = true))
    }

    @Test fun recreationRetainsAnUnconsumedRequestButNeverCreatesAnother() {
        val startup = StartupPlayback()
        val activity = startup.ActivityLaunch()
        activity.onCreate(normalLaunch = true, enabled = true)
        activity.onCreate(normalLaunch = true, enabled = true)
        assertTrue(startup.consume(enabled = true))
        activity.onCreate(normalLaunch = true, enabled = true)
        assertFalse(startup.consume(enabled = true))
    }

    @Test fun deliberateLauncherRestoreInANewProcessStillResumesOnce() {
        val startup = StartupPlayback()
        val activity = startup.ActivityLaunch()
        activity.onCreate(normalLaunch = true, enabled = true)
        assertTrue("A saved activity is not proof that this process already handled the launch", startup.pending)
        assertTrue(startup.consume(enabled = true))
        activity.onCreate(normalLaunch = true, enabled = true)
        assertFalse(startup.consume(enabled = true))
    }

    @Test fun closedSettingsTaskCanRestoreWhileThePlaybackProcessSurvives() {
        val startup = StartupPlayback()
        startup.ActivityLaunch().onCreate(normalLaunch = true, enabled = true)
        assertTrue(startup.consume(enabled = true))
        startup.cancel()

        val restoredTask = startup.ActivityLaunch()
        restoredTask.onCreate(normalLaunch = true, enabled = true)
        assertTrue("A newly restored task is not a rotation of the closed activity", startup.consume(enabled = true))
        restoredTask.onCreate(normalLaunch = true, enabled = true)
        assertFalse(startup.consume(enabled = true))
    }

    @Test fun closingSettingsDuringSlowRestorationDoesNotDisableTheNextLaunch() {
        val startup = StartupPlayback()
        val previous = startup.ActivityLaunch()
        previous.onCreate(normalLaunch = true, enabled = true)
        assertTrue(startup.pending)
        startup.cancel()
        assertFalse("An abandoned launch cannot play after its delayed load finishes", startup.consume(enabled = true))

        startup.ActivityLaunch().onCreate(normalLaunch = true, enabled = true)
        assertTrue(startup.pending)
        assertTrue(startup.consume(enabled = true))
        assertFalse(startup.consume(enabled = true))
    }

    @Test fun retainedSettingsActivityDoesNotRearmWhenPreferenceChanges() {
        val startup = StartupPlayback()
        val activity = startup.ActivityLaunch()
        activity.onCreate(normalLaunch = true, enabled = false)
        activity.onCreate(normalLaunch = true, enabled = true)
        assertFalse(startup.consume(enabled = true))
    }

    @Test fun restoredTaskStillHonorsOptOutAndPassiveLaunches() {
        for (normal in listOf(false, true)) for (enabled in listOf(false, true)) {
            if (normal && enabled) continue
            val startup = StartupPlayback()
            startup.ActivityLaunch().onCreate(normalLaunch = true, enabled = true)
            assertTrue(startup.consume(enabled = true))
            startup.cancel()
            startup.ActivityLaunch().onCreate(normalLaunch = normal, enabled = enabled)
            assertFalse(startup.pending)
            assertFalse(startup.consume(enabled = true))
        }
    }

    @Test fun restoredActivityAfterManualCancellationDoesNotRearmAutoplay() {
        val startup = StartupPlayback()
        val activity = startup.ActivityLaunch()
        activity.onCreate(normalLaunch = true, enabled = true)
        startup.cancel()
        activity.onCreate(normalLaunch = true, enabled = true)
        assertFalse(startup.consume(enabled = true))
    }

    @Test fun passiveFirstActivityCannotBecomeAnAutoplayRequestOnRecreation() {
        val startup = StartupPlayback()
        val activity = startup.ActivityLaunch()
        activity.onCreate(normalLaunch = false, enabled = true)
        activity.onCreate(normalLaunch = true, enabled = true)
        assertFalse(startup.consume(enabled = true))
        startup.launch(normalLaunch = true, enabled = true)
        assertTrue(startup.consume(enabled = true))
    }

    @Test fun eachDeliberateLaunchCanResumeAnAlreadyExistingSession() {
        val startup = StartupPlayback()
        startup.launch(normalLaunch = true, enabled = true)
        assertTrue(startup.consume(enabled = true))
        startup.launch(normalLaunch = true, enabled = true)
        assertTrue(startup.consume(enabled = true))
        assertFalse(startup.consume(enabled = true))
    }

    @Test fun externalOrHistoryOpenDoesNotPreventALaterDeliberateLaunch() {
        val startup = StartupPlayback()
        startup.launch(normalLaunch = false, enabled = true)
        assertFalse(startup.consume(enabled = true))
        startup.launch(normalLaunch = true, enabled = true)
        assertTrue(startup.consume(enabled = true))
    }

    @Test fun manualActionOrLeavingTheAppCancelsPendingAutoplay() {
        val startup = StartupPlayback()
        startup.ActivityLaunch().onCreate(normalLaunch = true, enabled = true)
        startup.cancel()
        assertFalse(startup.consume(enabled = true))
    }

    @Test fun disablingTheSettingBeforeRestorationCancelsTheRequest() {
        val startup = StartupPlayback()
        startup.ActivityLaunch().onCreate(normalLaunch = true, enabled = true)
        assertFalse(startup.consume(enabled = false))
        assertFalse(startup.consume(enabled = true))
    }
}
