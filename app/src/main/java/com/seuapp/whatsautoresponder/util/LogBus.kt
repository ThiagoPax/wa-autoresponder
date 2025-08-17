package com.seuapp.whatsautoresponder.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Canal simples para a UI reagir a novas linhas de log em tempo real.
 */
object LogBus {
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val events = _events.asSharedFlow()

    fun emit(line: String) {
        _events.tryEmit(line)
    }
}
