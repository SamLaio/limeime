package net.toload.main.hd.ui.view


/**
 * Interface for LIMESettings view updates.
 */
interface LIMESettingsView : ViewUpdateListener {
    fun showProgress(message: String?)
    fun hideProgress()
    fun showToast(message: String?, duration: Int)
    fun navigateToFragment(position: Int)
    fun finishActivity()
    fun onProgress(percentage: Int, status: String?)
}
