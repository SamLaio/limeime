package net.toload.main.hd.voice


enum class DictationState {
    IDLE,
    LISTENING,
    PARTIAL,
    FINALIZING,
    ERROR,
    CANCELLED
}
