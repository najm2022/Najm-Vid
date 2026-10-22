package com.najm.vid

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.equalTo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DashboardTest {
    @Test fun launcherOpensDashboardAndNavigationAndPersistsQuality() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        DownloadStore.init(context);DownloadStore.clearFinished();AppPreferences.setQuality(context,"ask")
        ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java)).use { scenario ->
            onView(withTagValue(equalTo("app_root"))).check(matches(isDisplayed()))
            onView(withTagValue(equalTo("inspect_button"))).check(matches(isDisplayed()))
            capture(context,"01-home.png")
            scenario.onActivity { it.window.decorView.findViewWithTag<EditText>("url_input").setText("https://example.com/video") }
            onView(withTagValue(equalTo("nav_downloads"))).perform(click())
            onView(withText("مكتبتك")).check(matches(isDisplayed()))
            capture(context,"02-library.png")
            onView(withTagValue(equalTo("nav_settings"))).perform(click())
            onView(withText("على طريقتك")).check(matches(isDisplayed()))
            capture(context,"03-settings.png")
            onView(withTagValue(equalTo("quality_setting"))).perform(click())
            onView(withTagValue(equalTo("quality_1080"))).perform(click())
            assertEquals("1080",AppPreferences.quality(context))
            scenario.recreate()
            assertEquals("1080",AppPreferences.quality(context))
            onView(withTagValue(equalTo("nav_home"))).perform(click())
            scenario.onActivity { activity ->
                val input=activity.window.decorView.findViewWithTag<EditText>("url_input")
                assertEquals("A draft must survive navigation and activity recreation","https://example.com/video",input.text.toString())
                input.setText("not a URL")
                activity.window.decorView.findViewWithTag<android.view.View>("inspect_button").performClick()
                assertNotNull("Invalid input must have an inline error",input.error)
            }
        }
        AppPreferences.setQuality(context,"ask")
    }

    private fun capture(context: Context,name: String) {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync();SystemClock.sleep(600)
        val bitmap=instrumentation.uiAutomation.takeScreenshot()
        assertNotNull(bitmap)
        val values=android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,name)
            put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/NajmVidPreview")
            put(android.provider.MediaStore.Images.Media.IS_PENDING,1)
        }
        val uri=context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!
        context.contentResolver.openOutputStream(uri)!!.use{bitmap!!.compress(Bitmap.CompressFormat.PNG,100,it)}
        context.contentResolver.update(uri,android.content.ContentValues().apply{put(android.provider.MediaStore.Images.Media.IS_PENDING,0)},null,null)
    }
}
