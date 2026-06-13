@file:Suppress("SENSELESS_COMPARISON", "USELESS_IS_CHECK", "PARAMETER_NAME_CHANGED_ON_OVERRIDE", "UNCHECKED_CAST", "TYPE_INTERSECTION_AS_REIFIED")

package net.toload.main.hd

import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import net.toload.main.hd.ui.LIMESettings
import net.toload.main.hd.ui.IntentHandler
import org.junit.FixMethodOrder
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@LargeTest
open class IntentHandlerTest {
    @Test(timeout = 60000)
    fun processTextPlainIntent_doesNotCrash() {
        ActivityScenario.launch(LIMESettings::class.java).use { scenario ->
                scenario.onActivity({ activity: LIMESettings ->
        try {
            var tmp: File = File(activity.getCacheDir(), "test_import.lime")
            FileOutputStream(tmp).use { fos ->
                    fos.write("a\tb\n".toByteArray(StandardCharsets.UTF_8))
            }
            var intent: Intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(Uri.fromFile(tmp), "text/plain")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            var handler: IntentHandler = IntentHandler(activity, activity.getSetupImController())
            handler.processIntent(intent)
            activity.getSupportFragmentManager().executePendingTransactions()
            var dialog: androidx.fragment.app.Fragment = activity.getSupportFragmentManager().findFragmentByTag("ImportDialog")!!
            if ((dialog is net.toload.main.hd.ui.dialog.ImportDialog)) {
                (dialog as net.toload.main.hd.ui.dialog.ImportDialog)
            }
            assertTrue("IntentHandler should process text/plain without crashing", true)
        } catch (e: Exception) {
            throw AssertionError("IntentHandler crashed processing text/plain intent", e)
        }
    })
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
    }
    @Test
    fun processTextPlainCinIntent_doesNotCrash() {
        ActivityScenario.launch(LIMESettings::class.java).use { scenario ->
                scenario.onActivity({ activity: LIMESettings ->
        try {
            var tmp: File = File(activity.getCacheDir(), "test_import.cin")
            FileOutputStream(tmp).use { fos ->
                    fos.write("%keyname begin\na b\n%keyname end\n".toByteArray(StandardCharsets.UTF_8))
            }
            var intent: Intent = Intent(Intent.ACTION_SEND)
            intent.setType("text/plain")
            intent.putExtra(Intent.EXTRA_TEXT, tmp.getAbsolutePath())
            var handler: IntentHandler = IntentHandler(activity, activity.getSetupImController())
            handler.processIntent(intent)
            assertTrue("IntentHandler should process text/plain .cin without crashing", true)
        } catch (e: Exception) {
            throw AssertionError("IntentHandler crashed processing text/plain .cin intent", e)
        }
    })
        }
    }
    @Test
    fun processZipIntent_doesNotCrash() {
        ActivityScenario.launch(LIMESettings::class.java).use { scenario ->
                scenario.onActivity({ activity: LIMESettings ->
        try {
            var tmp: File = File(activity.getCacheDir(), "array.limedb")
            FileOutputStream(tmp).use { fos ->
                    fos.write(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
            }
            var intent: Intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(Uri.fromFile(tmp), "application/zip")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            var handler: IntentHandler = IntentHandler(activity, activity.getSetupImController())
            handler.processIntent(intent)
            assertTrue("IntentHandler should process zip without crashing", true)
        } catch (e: Exception) {
            throw AssertionError("IntentHandler crashed processing zip intent", e)
        }
    })
        }
    }
    @Test
    fun processViewIntentWithInvalidScheme_gracefullyFails() {
        ActivityScenario.launch(LIMESettings::class.java).use { scenario ->
                scenario.onActivity({ activity: LIMESettings ->
        try {
            var tmp: File = File(activity.getCacheDir(), "bad_scheme.lime")
            FileOutputStream(tmp).use { fos ->
                    fos.write("a\tb\n".toByteArray(StandardCharsets.UTF_8))
            }
            var intent: Intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(Uri.fromFile(tmp), "text/plain")
            intent.setData(Uri.parse(("invalid://" + tmp.name)))
            var handler: IntentHandler = IntentHandler(activity, activity.getSetupImController())
            handler.processIntent(intent)
            assertTrue("IntentHandler should ignore invalid schemes without crashing", true)
        } catch (e: Exception) {
            throw AssertionError("IntentHandler crashed on invalid scheme", e)
        }
    })
        }
    }
    @Test
    fun processViewIntentWithFileSchemeAndLimeExtension_doesNotCrash() {
        ActivityScenario.launch(LIMESettings::class.java).use { scenario ->
                scenario.onActivity({ activity: LIMESettings ->
        try {
            var tmpDir: File = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!
            var tmp: File = File((if ((tmpDir == null)) activity.getCacheDir() else tmpDir), "shared.lime")
            FileOutputStream(tmp).use { fos ->
                    fos.write("c\td\n".toByteArray(StandardCharsets.UTF_8))
            }
            var intent: Intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(Uri.fromFile(tmp), "text/plain")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            var handler: IntentHandler = IntentHandler(activity, activity.getSetupImController())
            handler.processIntent(intent)
            assertTrue("IntentHandler should process file:// .lime without crashing", true)
        } catch (e: Exception) {
            throw AssertionError("IntentHandler crashed processing file:// .lime", e)
        }
    })
        }
    }
}
