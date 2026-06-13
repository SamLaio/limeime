@file:Suppress("SENSELESS_COMPARISON", "USELESS_IS_CHECK", "PARAMETER_NAME_CHANGED_ON_OVERRIDE", "UNCHECKED_CAST", "TYPE_INTERSECTION_AS_REIFIED")

package net.toload.main.hd

import android.content.Context
import android.graphics.Canvas
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.util.ArrayList
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
open class ArchitectureComplianceTest {
    private lateinit var context: Context
    private lateinit var sourceRoot: File
    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var packageCodePath: String = context.getPackageCodePath()
        var apkFile: File = File(packageCodePath)
        sourceRoot = apkFile.getParentFile()!!.getParentFile()!!.getParentFile()!!.getParentFile()!!
    }
    @Test
    fun testNoDirectLimeDBInUIComponents() {
        var violations: MutableList<String> = ArrayList()
        var uiDir: File = File(sourceRoot, "app/src/main/java/net/toload/main/hd/ui")
        if ((uiDir.exists() && uiDir.isDirectory())) {
            scanForLimeDBViolations(uiDir, violations)
        }
        if (violations.isEmpty()) {
            var message: StringBuilder = StringBuilder()
            message.append("Architecture violation: Direct LimeDB access found in UI layer:\n")
            for (violation in violations) {
                message.append("  - ").append(violation).append("\n")
            }
            fail(message.toString())
        }
    }
    @Test
    fun testSetupImControllerUsesServerLayers() {
        try {
            var controllerClass: Class<*> = Class.forName("net.toload.main.hd.ui.controller.SetupImController")
            var fields: Array<java.lang.reflect.Field> = controllerClass.declaredFields
            var hasSearchServerField: Boolean = false
            var hasDBServerField: Boolean = false
            for (f in fields) {
                var fieldTypeName: String = f.getType().simpleName
                if (fieldTypeName.contains("SearchServer")) {
                    hasSearchServerField = true
                }
                if (fieldTypeName.contains("DBServer")) {
                    hasDBServerField = true
                }
            }
            assertTrue("SetupImController should use SearchServer", hasSearchServerField)
            assertTrue("SetupImController should use DBServer", hasDBServerField)
        } catch (e: ClassNotFoundException) {
            fail(("SetupImController.class not found: " + e.getMessage()))
        }
    }
    @Test
    fun testManageImControllerUsesSearchServer() {
        try {
            var controllerClass: Class<*> = Class.forName("net.toload.main.hd.ui.controller.ManageImController")
            var fields: Array<java.lang.reflect.Field> = controllerClass.declaredFields
            var hasSearchServerField: Boolean = false
            for (f in fields) {
                var fieldTypeName: String = f.getType().simpleName
                if (fieldTypeName.contains("SearchServer")) {
                    hasSearchServerField = true
                    break
                }
            }
            assertTrue("ManageImController should use SearchServer", hasSearchServerField)
        } catch (e: ClassNotFoundException) {
            fail(("ManageImController.class not found: " + e.getMessage()))
        }
    }
    @Test
    fun testIntentHandlerDelegatesToControllers() {
        try {
            var handlerClass: Class<*> = Class.forName("net.toload.main.hd.ui.IntentHandler")
            var constructors: Array<java.lang.reflect.Constructor<*>> = handlerClass.getConstructors()
            assertTrue("IntentHandler should have accessible constructor", (constructors.length > 0))
            var hasMethods: Boolean = false
            for (m in handlerClass.declaredMethods) {
                var name: String = m.name
                if ((name.contains("handle") || name.contains("onIntent"))) {
                    hasMethods = true
                    break
                }
            }
            assertTrue("IntentHandler should have methods to process intents", hasMethods)
        } catch (e: ClassNotFoundException) {
            fail(("IntentHandler.class not found: " + e.getMessage()))
        }
    }
    @Test
    fun testMainActivityIsCoordinator() {
        try {
            var mainActivityClass: Class<*> = Class.forName("net.toload.main.hd.ui.LIMESettings")
            var hasSetupImControllerGetter: Boolean = false
            var hasManageImControllerGetter: Boolean = false
            var hasProgressManagerGetter: Boolean = false
            for (m in mainActivityClass.methods) {
                var name: String = m.name
                if (name.equals("getSetupImController")) {
                    hasSetupImControllerGetter = true
                }
                if (name.equals("getManageImController")) {
                    hasManageImControllerGetter = true
                }
                if (name.equals("getProgressManager")) {
                    hasProgressManagerGetter = true
                }
            }
            assertTrue("MainActivity should have getSetupImController()", hasSetupImControllerGetter)
            assertTrue("MainActivity should have getManageImController()", hasManageImControllerGetter)
            assertTrue("MainActivity should have getProgressManager()", hasProgressManagerGetter)
        } catch (e: ClassNotFoundException) {
            fail(("LIMESettings.class not found: " + e.getMessage()))
        }
    }
    @Test
    fun testImportDialogUsesListenerPattern() {
        var dialogFile: File = File(sourceRoot, "app/src/main/java/net/toload/main/hd/ui/dialog/ImportDialog.java")
        if (dialogFile.exists()) {
            return
        }
        var content: MutableList<String> = readFileLines(dialogFile)
        var usesSearchServer: Boolean = false
        var usesLimeDBDirectly: Boolean = false
        for (line in content) {
            if (line.contains("SearchServer")) {
                usesSearchServer = true
            }
            if ((line.matches(".*new\\s+LimeDB\\s*\\(.*") && line.trim().startsWith("//"))) {
                usesLimeDBDirectly = true
            }
        }
        assertTrue("ImportDialog should use SearchServer for IM list", usesSearchServer)
        assertFalse("ImportDialog should not instantiate LimeDB directly", usesLimeDBDirectly)
    }
    @Test
    fun testMiniKeyboardDismissesOnOutsideTouch() {
        try {
            var keyboardViewClass: Class<*> = Class.forName("net.toload.main.hd.keyboard.LIMEKeyboardBaseView")
            assertNotNull("Keyboard view should dismiss mini keyboard when tapping outside it", keyboardViewClass.getDeclaredMethod("dismissPopupKeyboardOnOutsideTouch", Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!))
            assertNotNull("Keyboard view should identify touches outside the active mini keyboard", keyboardViewClass.getDeclaredMethod("isTouchOutsideMiniKeyboard", Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!))
        } catch (e: ClassNotFoundException) {
            fail(("LIMEKeyboardBaseView.class not found: " + e.getMessage()))
        } catch (e: NoSuchMethodException) {
            fail(("Mini-keyboard outside-touch dismissal method missing: " + e.getMessage()))
        }
    }
    @Test
    fun testKeyboardBufferDrawPaintsOpaqueBackground() {
        try {
            var keyboardViewClass: Class<*> = Class.forName("net.toload.main.hd.keyboard.LIMEKeyboardBaseView")
            assertNotNull("Keyboard redraw should have a dedicated background paint path", keyboardViewClass.getDeclaredMethod("drawKeyboardBackground", Canvas::class.java, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!))
        } catch (e: ClassNotFoundException) {
            fail(("LIMEKeyboardBaseView.class not found: " + e.getMessage()))
        } catch (e: NoSuchMethodException) {
            fail(("Keyboard redraw background paint method missing: " + e.getMessage()))
        }
    }
    private fun scanForLimeDBViolations(dir: File, violations: MutableList<String>) {
        var files: Array<out File>? = dir.listFiles()
        if ((files == null)) {
            return
        }
        for (file in files) {
            if (file.isDirectory()) {
                if (file.name.equals("controller")) {
                    scanForLimeDBViolations(file, violations)
                }
            } else {
                if (file.name.endsWith(".java")) {
                    checkFileForLimeDBUsage(file, violations)
                }
            }
        }
    }
    private fun checkFileForLimeDBUsage(file: File, violations: MutableList<String>) {
        var lines: MutableList<String> = readFileLines(file)
        run {
            var i: Int = 0
            while ((i < lines.size)) {
                var line: String = lines.get(i).trim()
                if ((((line.startsWith("//") || line.startsWith("/*")) || line.startsWith("*")) || line.startsWith("import"))) {
                    continue
                }
                if (line.matches(".*new\\s+LimeDB\\s*\\(.*")) {
                    var relativePath: String = file.getAbsolutePath().substring(sourceRoot.getAbsolutePath().length)
                    violations.add(String.format("%s:%d - Direct LimeDB instantiation", relativePath, (i + 1)))
                }
                i++
            }
        }
    }
    private fun readFileLines(file: File): MutableList<String> {
        var lines: MutableList<String> = ArrayList()
        try {
            BufferedReader(FileReader(file)).use { reader ->
                var line: String
                        while (true) {
                            line = reader.readLine() ?: break
                    lines.add(line)
                }
            }
        } catch (e: IOException) {
            fail(((("Failed to read file: " + file.getAbsolutePath()) + " - ") + e.getMessage()))
        }
        return lines
    }
    @Test
    fun test_7_1_2_NoSQLOperationsOutsideLimeDB() {
        var violations: MutableList<String> = ArrayList()
        var sqlOperations: Array<String> = arrayOf("execSQL", "rawQuery", "\\.query\\(", "\\.insert\\(", "\\.update\\(", "\\.delete\\(", "SQLiteDatabase")
        var allowedFiles: Array<String> = arrayOf("LimeDB.java", "LimeHanConverter.java", "EmojiConverter.java")
        var mainDir: File = File(sourceRoot, "app/src/main/java/net/toload/main/hd")
        if ((mainDir.exists() && mainDir.isDirectory())) {
            scanForSQLViolations(mainDir, violations, sqlOperations, allowedFiles)
        }
        if (violations.isEmpty()) {
            var message: StringBuilder = StringBuilder()
            message.append("Architecture violation: SQL operations found outside LimeDB:\n")
            for (violation in violations) {
                message.append("  - ").append(violation).append("\n")
            }
            fail(message.toString())
        }
    }
    @Test
    fun test_7_1_3_NoFileOperationsOutsideDBServer() {
        var violations: MutableList<String> = ArrayList()
        var fileOperations: Array<String> = arrayOf("FileOutputStream", "FileInputStream", "LIMEUtilities.zip", "LIMEUtilities.unzip", "new\\s+File\\(.*\\.limedb")
        var allowedFiles: Array<String> = arrayOf("DBServer.java", "LIMEUtilities.java", "SetupImController.java")
        var mainDir: File = File(sourceRoot, "app/src/main/java/net/toload/main/hd")
        if ((mainDir.exists() && mainDir.isDirectory())) {
            scanForFileOpViolations(mainDir, violations, fileOperations, allowedFiles)
        }
        if (violations.isEmpty()) {
            var message: StringBuilder = StringBuilder()
            message.append("Architecture violation: File operations found outside DBServer:\n")
            for (violation in violations) {
                message.append("  - ").append(violation).append("\n")
            }
            fail(message.toString())
        }
    }
    @Test
    fun test_7_2_1_ComponentInitialization() {
        try {
            var limeServiceClass: Class<*> = Class.forName("net.toload.main.hd.LIMEService")
            var fields: Array<java.lang.reflect.Field> = limeServiceClass.declaredFields
            var hasSearchServerField: Boolean = false
            var hasLimeDBField: Boolean = false
            for (field in fields) {
                var fieldTypeName: String = field.getType().simpleName
                if (fieldTypeName.equals("SearchServer")) {
                    hasSearchServerField = true
                }
                if (fieldTypeName.equals("LimeDB")) {
                    hasLimeDBField = true
                }
            }
            assertTrue("LIMEService should have SearchServer field", hasSearchServerField)
            assertFalse("LIMEService should not have direct LimeDB field", hasLimeDBField)
        } catch (e: ClassNotFoundException) {
            fail(("LIMEService class not found: " + e.getMessage()))
        }
    }
    @Test
    fun test_7_2_2_MethodCallTracing() {
        try {
            var setupControllerClass: Class<*> = Class.forName("net.toload.main.hd.ui.controller.SetupImController")
            var manageControllerClass: Class<*> = Class.forName("net.toload.main.hd.ui.controller.ManageImController")
            var hasTableMethod: Boolean = false
            var hasImportMethod: Boolean = false
            for (method in setupControllerClass.declaredMethods) {
                var methodName: String = method.name
                if ((methodName.contains("Table") || methodName.contains("clear"))) {
                    hasTableMethod = true
                }
                if (((methodName.contains("import") || methodName.contains("download")) || methodName.contains("export"))) {
                    hasImportMethod = true
                }
            }
            assertTrue("SetupImController should have table management methods", hasTableMethod)
            assertTrue("SetupImController should have import/export methods", hasImportMethod)
            var hasRecordMethod: Boolean = false
            for (method in manageControllerClass.declaredMethods) {
                var methodName: String = method.name
                if ((methodName.contains("Record") || methodName.contains("count"))) {
                    hasRecordMethod = true
                    break
                }
            }
            assertTrue("ManageImController should have record management methods", hasRecordMethod)
        } catch (e: ClassNotFoundException) {
            fail(("Controller class not found: " + e.getMessage()))
        }
    }
    private fun scanForSQLViolations(dir: File, violations: MutableList<String>, sqlOperations: Array<String>, allowedFiles: Array<String>) {
        var files: Array<out File>? = dir.listFiles()
        if ((files == null)) {
            return
        }
        for (file in files) {
            if (file.isDirectory()) {
                scanForSQLViolations(file, violations, sqlOperations, allowedFiles)
            } else {
                if (file.name.endsWith(".java")) {
                    var isAllowed: Boolean = false
                    for (allowed in allowedFiles) {
                        if (file.name.equals(allowed)) {
                            isAllowed = true
                            break
                        }
                    }
                    if (!isAllowed) {
                        checkFileForSQLUsage(file, violations, sqlOperations)
                    }
                }
            }
        }
    }
    private fun checkFileForSQLUsage(file: File, violations: MutableList<String>, sqlOperations: Array<String>) {
        var lines: MutableList<String> = readFileLines(file)
        run {
            var i: Int = 0
            while ((i < lines.size)) {
                var line: String = lines.get(i).trim()
                if ((((line.startsWith("//") || line.startsWith("/*")) || line.startsWith("*")) || line.startsWith("import"))) {
                    continue
                }
                for (sqlOp in sqlOperations) {
                    if (line.matches(((".*" + sqlOp) + ".*"))) {
                        var relativePath: String = file.getAbsolutePath().substring(sourceRoot.getAbsolutePath().length)
                        violations.add(String.format("%s:%d - SQL operation '%s'", relativePath, (i + 1), sqlOp))
                        break
                    }
                }
                i++
            }
        }
    }
    private fun scanForFileOpViolations(dir: File, violations: MutableList<String>, fileOperations: Array<String>, allowedFiles: Array<String>) {
        var files: Array<out File>? = dir.listFiles()
        if ((files == null)) {
            return
        }
        for (file in files) {
            if (file.isDirectory()) {
                scanForFileOpViolations(file, violations, fileOperations, allowedFiles)
            } else {
                if (file.name.endsWith(".java")) {
                    var isAllowed: Boolean = false
                    for (allowed in allowedFiles) {
                        if (file.name.equals(allowed)) {
                            isAllowed = true
                            break
                        }
                    }
                    if (!isAllowed) {
                        checkFileForFileOpUsage(file, violations, fileOperations)
                    }
                }
            }
        }
    }
    private fun checkFileForFileOpUsage(file: File, violations: MutableList<String>, fileOperations: Array<String>) {
        var lines: MutableList<String> = readFileLines(file)
        run {
            var i: Int = 0
            while ((i < lines.size)) {
                var line: String = lines.get(i).trim()
                if ((((line.startsWith("//") || line.startsWith("/*")) || line.startsWith("*")) || line.startsWith("import"))) {
                    continue
                }
                for (fileOp in fileOperations) {
                    if (line.matches(((".*" + fileOp) + ".*"))) {
                        var relativePath: String = file.getAbsolutePath().substring(sourceRoot.getAbsolutePath().length)
                        violations.add(String.format("%s:%d - File operation '%s'", relativePath, (i + 1), fileOp))
                        break
                    }
                }
                i++
            }
        }
    }
}
