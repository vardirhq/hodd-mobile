package no.vardir.hodd

import android.Manifest
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.ScanOptions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ScannerOrientationTest {
    @get:Rule val cameraPermission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @Test fun scannerOpensInPortraitOnAPortraitPhone() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(Configuration.ORIENTATION_PORTRAIT, context.resources.configuration.orientation)
        val intent = ScanOptions().setOrientationLocked(false).setBeepEnabled(false).createScanIntent(context)
        ActivityScenario.launch<CaptureActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(Configuration.ORIENTATION_PORTRAIT, activity.resources.configuration.orientation)
            }
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.waitForIdleSync()
            val directory = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
            File(directory, "05-scanner-portrait.png").outputStream().use { output ->
                checkNotNull(instrumentation.uiAutomation.takeScreenshot()).compress(Bitmap.CompressFormat.PNG, 100, output)
            }
        }
    }
}
