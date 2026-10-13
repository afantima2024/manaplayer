package com.macarmusic.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LegacyPickerSmokeTest {
    @Test fun precedingApkReportsMissingPickerOnPickerlessFirmware() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("baselinePicker") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("parkedNoticeSeen", true).commit()
        fun resource(name: String, type: String) = context.resources.getIdentifier(name, type, context.packageName)
        ActivityScenario.launch<Activity>(Intent().setClassName(context, "com.macarmusic.app.ui.MainActivity")).use {
            onView(withId(resource("nav_folders", "id"))).perform(click())
            onView(withText(context.getString(resource("add_folder", "string")))).perform(click())
            onView(withText(context.getString(resource("picker_missing", "string")))).check(matches(isDisplayed()))
            val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            val directory = File(context.getExternalFilesDir(null), "test-screenshots").apply { mkdirs() }
            try {
                File(directory, "picker-baseline-missing.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            } finally {
                bitmap.recycle()
            }
        }
    }
}
