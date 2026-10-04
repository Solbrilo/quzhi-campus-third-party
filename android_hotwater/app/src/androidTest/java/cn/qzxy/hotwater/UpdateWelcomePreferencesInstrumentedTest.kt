package cn.qzxy.hotwater

import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class UpdateWelcomePreferencesInstrumentedTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val directory = File(base.cacheDir, "welcome-test-${System.nanoTime()}").apply { mkdirs() }
    private val context = object : ContextWrapper(base) {
        override fun getNoBackupFilesDir(): File = directory
    }

    @After fun cleanUp() { directory.deleteRecursively() }

    @Test fun pendingSurvivesRestartAndSeenIsIndependentOfOnboardingAndAccountReset() {
        assertTrue(UpdateWelcomePreferences(context).shouldShow(28, 100, 200))
        val restarted = UpdateWelcomePreferences(context)
        assertTrue(restarted.shouldShow(29, 100, 200))
        assertTrue(restarted.markSeen())
        assertFalse(UpdateWelcomePreferences(context).shouldShow(null, 100, 200))
    }

    @Test fun freshInstallStaysExcludedAfterSameVersionReinstall() {
        assertFalse(UpdateWelcomePreferences(context).shouldShow(null, 100, 100))
        assertFalse(UpdateWelcomePreferences(context).shouldShow(null, 100, 200))
    }
}
