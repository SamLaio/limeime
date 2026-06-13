package net.toload.main.hd

import android.content.Context
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.idling.CountingIdlingResource
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.toload.main.hd.ui.LIMESettings
import net.toload.main.hd.ui.ProgressManager
import org.junit.Assert.assertNotNull
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgressManagerTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Test
    fun testProgressManagerShowDismissWithoutLeak() {
        Assume.assumeTrue("Skip on API 21", Build.VERSION.SDK_INT != 21)
        val idlingResource = CountingIdlingResource("ProgressManagerTest")
        IdlingRegistry.getInstance().register(idlingResource)
        try {
            ActivityScenario.launch(LIMESettings::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val manager: ProgressManager = activity.getProgressManager()!!
                    assertNotNull("ProgressManager should exist", manager)
                    idlingResource.increment()
                    activity.runOnUiThread {
                        manager.show("Testing...")
                        idlingResource.decrement()
                    }
                }
                Espresso.onIdle()
                scenario.onActivity { activity ->
                    val manager: ProgressManager = activity.getProgressManager()!!
                    idlingResource.increment()
                    activity.runOnUiThread {
                        manager.dismiss()
                        idlingResource.decrement()
                    }
                }
                Espresso.onIdle()
            }
        } finally {
            IdlingRegistry.getInstance().unregister(idlingResource)
        }
    }

    @Test
    fun testProgressManagerUpdateMessage() {
        ActivityScenario.launch(LIMESettings::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val manager: ProgressManager = activity.getProgressManager()!!
                activity.runOnUiThread {
                    manager.show("Initial message")
                }
            }
            Thread.sleep(100)
            scenario.onActivity { activity ->
                val manager: ProgressManager = activity.getProgressManager()!!
                activity.runOnUiThread {
                    manager.updateMessage("Updated message")
                }
            }
            Thread.sleep(100)
            scenario.onActivity { activity ->
                val manager: ProgressManager = activity.getProgressManager()!!
                activity.runOnUiThread {
                    manager.dismiss()
                }
            }
            Thread.sleep(100)
        }
    }

    @Test
    fun testProgressManagerUpdatePercentage() {
        ActivityScenario.launch(LIMESettings::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val manager: ProgressManager = activity.getProgressManager()!!
                activity.runOnUiThread {
                    manager.show("Downloading...")
                    manager.updateProgress(25)
                }
            }
            Thread.sleep(100)
            scenario.onActivity { activity ->
                val manager: ProgressManager = activity.getProgressManager()!!
                activity.runOnUiThread {
                    manager.updateProgress(50)
                }
            }
            Thread.sleep(100)
            scenario.onActivity { activity ->
                val manager: ProgressManager = activity.getProgressManager()!!
                activity.runOnUiThread {
                    manager.updateProgress(100)
                    manager.dismiss()
                }
            }
            Thread.sleep(100)
        }
    }

    @org.junit.Ignore("Deprecated: LIMESettings startup lifecycle (post commit 6f36521a + LIME_SETTINGS_BACKPORT MVC refactor) makes ActivityScenario.recreate() time out before reaching RESUMED. See docs/DEPCECATED_UI_TESTS.md.")
    @Test
    fun testProgressManagerSurvivesActivityRecreation() {
        Assume.assumeTrue("Skip on API 21", Build.VERSION.SDK_INT != 21)
        val idlingResource = CountingIdlingResource("ProgressManagerTest")
        IdlingRegistry.getInstance().register(idlingResource)
        try {
            ActivityScenario.launch(LIMESettings::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val manager: ProgressManager = activity.getProgressManager()!!
                    idlingResource.increment()
                    activity.runOnUiThread {
                        manager.show("Testing recreation...")
                        idlingResource.decrement()
                    }
                }
                Espresso.onIdle()
                scenario.onActivity { activity ->
                    val manager: ProgressManager = activity.getProgressManager()!!
                    idlingResource.increment()
                    activity.runOnUiThread {
                        manager.dismiss()
                        idlingResource.decrement()
                    }
                }
                Espresso.onIdle()
                scenario.recreate()
                scenario.onActivity { activity ->
                    val manager: ProgressManager = activity.getProgressManager()!!
                    assertNotNull("ProgressManager should exist after recreation", manager)
                    idlingResource.increment()
                    activity.runOnUiThread {
                        manager.show("After recreation")
                        manager.dismiss()
                        idlingResource.decrement()
                    }
                }
                Espresso.onIdle()
            }
        } finally {
            IdlingRegistry.getInstance().unregister(idlingResource)
        }
    }

    @Test
    fun testProgressManagerMultipleShowDismissCycles() {
        Assume.assumeTrue("Skip on API 21", Build.VERSION.SDK_INT != 21)
        val idlingResource = CountingIdlingResource("ProgressManagerTest")
        IdlingRegistry.getInstance().register(idlingResource)
        try {
            ActivityScenario.launch(LIMESettings::class.java).use { scenario ->
                for (i in 0 until 3) {
                    val cycle = i
                    scenario.onActivity { activity ->
                        val manager: ProgressManager = activity.getProgressManager()!!
                        idlingResource.increment()
                        activity.runOnUiThread {
                            manager.show("Cycle $cycle")
                            idlingResource.decrement()
                        }
                    }
                    Espresso.onIdle()
                    scenario.onActivity { activity ->
                        val manager: ProgressManager = activity.getProgressManager()!!
                        idlingResource.increment()
                        activity.runOnUiThread {
                            manager.dismiss()
                            idlingResource.decrement()
                        }
                    }
                    Espresso.onIdle()
                }
            }
        } finally {
            IdlingRegistry.getInstance().unregister(idlingResource)
        }
    }

    @Test
    fun testProgressManagerHandlesNullEmptyMessages() {
        ActivityScenario.launch(LIMESettings::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val manager: ProgressManager = activity.getProgressManager()!!
                activity.runOnUiThread {
                    manager.show(null)
                    manager.updateMessage("")
                    manager.dismiss()
                }
            }
            Thread.sleep(100)
        }
    }

    @Test
    fun testProgressManagerIsUIThreadSafe() {
        Assume.assumeTrue("Skip on API 21", Build.VERSION.SDK_INT != 21)
        val idlingResource = CountingIdlingResource("ProgressManagerTest")
        IdlingRegistry.getInstance().register(idlingResource)
        try {
            ActivityScenario.launch(LIMESettings::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val manager: ProgressManager = activity.getProgressManager()!!
                    idlingResource.increment()
                    Thread {
                        manager.show("From background")
                        idlingResource.decrement()
                    }.start()
                }
                Espresso.onIdle()
                scenario.onActivity { activity ->
                    val manager: ProgressManager = activity.getProgressManager()!!
                    idlingResource.increment()
                    Thread {
                        manager.dismiss()
                        idlingResource.decrement()
                    }.start()
                }
                Espresso.onIdle()
            }
        } finally {
            IdlingRegistry.getInstance().unregister(idlingResource)
        }
    }
}
