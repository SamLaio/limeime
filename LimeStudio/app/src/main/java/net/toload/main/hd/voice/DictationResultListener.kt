package net.toload.main.hd.voice


interface DictationResultListener {
    fun onDictationStateChanged(state: DictationState?)
    fun onDictationPartialText(text: String?)
    fun onDictationFinalText(text: String?)
    fun onDictationError(errorCode: Int, shouldFallback: Boolean)
    fun onDictationCancelled()
}
