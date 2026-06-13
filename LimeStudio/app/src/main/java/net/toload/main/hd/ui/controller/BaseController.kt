package net.toload.main.hd.ui.controller

import android.os.Handler
import android.os.Looper
import android.util.Log
import net.toload.main.hd.ui.view.LIMESettingsView
import net.toload.main.hd.ui.view.ViewUpdateListener

/**
 * Abstract base class for all controllers.
 */
abstract class BaseController {
    @JvmField
    protected val mainHandler: Handler = Handler(Looper.getMainLooper())

    protected fun handleError(view: ViewUpdateListener?, message: String?, exception: Exception?) {
        val safeMessage = message ?: ""
        if (exception != null) {
            Log.e(TAG, safeMessage, exception)
        } else {
            Log.e(TAG, safeMessage)
        }

        if (view != null) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                view.onError(safeMessage)
            } else {
                mainHandler.post { view.onError(safeMessage) }
            }
        }
    }

    protected fun updateProgress(view: LIMESettingsView?, percentage: Int, status: String?) {
        if (view != null) {
            mainHandler.post { view.onProgress(percentage, status) }
        }
    }

    protected fun showProgress(view: LIMESettingsView?, message: String?) {
        if (view != null) {
            mainHandler.post { view.showProgress(message) }
        }
    }

    protected fun hideProgress(view: LIMESettingsView?) {
        if (view != null) {
            mainHandler.post { view.hideProgress() }
        }
    }

    protected fun showToast(view: LIMESettingsView?, message: String?, duration: Int) {
        if (view != null) {
            mainHandler.post { view.showToast(message, duration) }
        }
    }

    fun showProgress(view: Any?, message: String?) {
        if (view is LIMESettingsView) {
            showProgress(view, message)
        }
    }

    fun hideProgress(view: Any?) {
        if (view is LIMESettingsView) {
            hideProgress(view)
        }
    }

    fun showToast(view: Any?, message: String?, duration: Int) {
        if (view is LIMESettingsView) {
            showToast(view, message, duration)
        }
    }

    companion object {
        protected const val TAG = "BaseController"
    }
}
