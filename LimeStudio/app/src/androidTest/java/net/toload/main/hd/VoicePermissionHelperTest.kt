package net.toload.main.hd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.toload.main.hd.voice.VoicePermissionHelper
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
open class VoicePermissionHelperTest {
    private lateinit var appContext: Context
    @Before
    fun setUp() {
        appContext = InstrumentationRegistry.getInstrumentation().getTargetContext()
        PreferenceManager.getDefaultSharedPreferences(appContext).edit().remove(VoicePermissionHelper.PREF_RECORD_AUDIO_PERMISSION_PROMPTED).commit()
    }
    @Test
    fun promptedFlagDefaultsToFalseAndCanBeMarked() {
        assertFalse(VoicePermissionHelper.wasRecordAudioPermissionPrompted(appContext))
        VoicePermissionHelper.markRecordAudioPermissionPrompted(appContext)
        assertTrue(VoicePermissionHelper.wasRecordAudioPermissionPrompted(appContext))
    }
    @Test
    fun appSettingsIntentTargetsThisPackage() {
        var intent: Intent = VoicePermissionHelper.createAppSettingsIntent(appContext)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.getAction())
        assertEquals(("package:" + appContext.getPackageName()), intent.getDataString())
    }
}
