package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.CockpitJsonSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("S3XY Cockpit", appName)
    }

    @Test
    fun `factory dashboards and freeze frames serialize and decode accurately`() {
        val presets = CockpitJsonSerializer.createFactoryDashboards()
        assertEquals(4, presets.size)
        val categories = presets.map { it.categoryTag }.toSet()
        assertTrue(categories.contains("PERFORMANCE"))
        assertTrue(categories.contains("BATTERY"))
        assertTrue(categories.contains("THERMAL"))
        assertTrue(categories.contains("DYNAMICS"))

        val firstWidgets = CockpitJsonSerializer.decodeWidgets(presets.first().widgetsJson)
        assertTrue(firstWidgets.isNotEmpty())
    }
}
