package net.toload.main.hd.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import java.lang.ref.WeakReference
import net.toload.main.hd.R

/**
 * Manages progress dialog creation, display, and updates.
 */
class ProgressManager(private val context: Context) {
    private var progress: AlertDialog? = null
    private val activityRef: WeakReference<Activity> = WeakReference(context as Activity)
    private var overlayContainer: View? = null
    private var overlayBar: ProgressBar? = null
    private var overlayText: TextView? = null

    init {
        val activity = activityRef.get()
        if (activity != null) {
            overlayContainer = activity.findViewById(R.id.activity_progress_overlay)
            overlayBar = activity.findViewById(R.id.activity_progress_bar)
            overlayText = activity.findViewById(R.id.activity_progress_text)
            overlayBar?.max = 100
        }
    }

    fun show() {
        runOnUiThread {
            val overlay = overlayContainer
            if (overlay != null) {
                overlay.visibility = View.VISIBLE
                overlayBar?.isIndeterminate = true
                overlayBar?.progress = 1
                return@runOnUiThread
            }

            if (progress == null) {
                val builder = AlertDialog.Builder(context)
                builder.setCancelable(false)
                val view = LayoutInflater.from(context).inflate(R.layout.progress, null)
                builder.setView(view)
                progress = builder.create()
            }
            val dialog = progress
            if (dialog != null && !dialog.isShowing) {
                dialog.show()
            }
        }
    }

    fun show(message: String?) {
        show()
        if (message != null) {
            updateProgress(message)
        }
    }

    fun dismiss() {
        runOnUiThread {
            val overlay = overlayContainer
            if (overlay != null) {
                overlay.visibility = View.GONE
                return@runOnUiThread
            }

            val activity = activityRef.get()
            val dialog = progress
            if (activity != null && !activity.isDestroyed && dialog != null && dialog.isShowing) {
                dialog.dismiss()
            }
            progress = null
        }
    }

    fun updateProgress(value: Int) {
        runOnUiThread {
            val overlay = overlayContainer
            val bar = overlayBar
            if (bar != null && overlay != null && overlay.visibility == View.VISIBLE) {
                bar.isIndeterminate = false
                bar.progress = value
                return@runOnUiThread
            }

            val dialog = progress
            if (dialog != null && dialog.isShowing) {
                dialog.findViewById<ProgressBar>(R.id.progress_bar)?.progress = value
            }
        }
    }

    fun updateProgress(message: String?) {
        runOnUiThread {
            val overlay = overlayContainer
            val text = overlayText
            if (text != null && overlay != null && overlay.visibility == View.VISIBLE) {
                text.text = message
                return@runOnUiThread
            }

            val dialog = progress
            if (dialog != null && dialog.isShowing) {
                dialog.findViewById<TextView>(R.id.progress_text)?.text = message
            }
        }
    }

    fun updateMessage(message: String?) {
        updateProgress(message)
    }

    fun isShowing(): Boolean {
        return (overlayContainer != null && overlayContainer?.visibility == View.VISIBLE) ||
            (progress != null && progress?.isShowing == true)
    }

    private fun runOnUiThread(runnable: Runnable) {
        if (context is Activity) {
            context.runOnUiThread(runnable)
        } else {
            runnable.run()
        }
    }
}
