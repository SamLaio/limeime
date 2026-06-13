@file:Suppress("SENSELESS_COMPARISON", "USELESS_IS_CHECK", "PARAMETER_NAME_CHANGED_ON_OVERRIDE", "UNCHECKED_CAST", "TYPE_INTERSECTION_AS_REIFIED")

package net.toload.main.hd

import android.app.Activity
import android.app.Service
import android.content.Context
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

open class StubActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (getIntent().getBooleanExtra("showEditText", false)) {
            var editText: EditText = EditText(this)
            editText.setSingleLine(false)
            editText.setTextSize(24f)
            editText.setMinLines(4)
            setContentView(editText)
            editText.requestFocus()
            editText.postDelayed({
    var imm: InputMethodManager = getSystemService(InputMethodManager::class.java)
    if ((imm != null)) {
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }
}, 300)
        }
    }
    fun createLIMEServiceWithContext(): LIMEService {
        var service: LIMEService = LIMEService()
        try {
            var attachBaseContext: java.lang.reflect.Method = Service::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
            attachBaseContext.setAccessible(true)
            attachBaseContext.invoke(service, this)
        } catch (e: Exception) {
            throw RuntimeException("Failed to attach base context to LIMEService", e)
        }
        service.onCreate()
        return service
    }
}
