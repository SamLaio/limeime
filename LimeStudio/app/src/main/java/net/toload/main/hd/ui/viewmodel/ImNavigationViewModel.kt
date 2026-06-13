package net.toload.main.hd.ui.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import net.toload.main.hd.data.ImConfig

class ImNavigationViewModel : ViewModel() {
    // null = no selection / show placeholder
    @JvmField
    val selectedIm: MutableLiveData<ImConfig?> = MutableLiveData(null)

    // true = show ImInstallFragment in detail pane
    @JvmField
    val showInstall: MutableLiveData<Boolean> = MutableLiveData(false)
}
