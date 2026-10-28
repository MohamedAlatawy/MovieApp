package com.example.movieapp.push

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** In-process hints only: no replay, persistence or process-death guarantee. Workers are independent. */
@Singleton
class MovieUpdateSignal @Inject constructor() {
    private val mutable = MutableSharedFlow<Int>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events = mutable.asSharedFlow()
    internal fun publish(id: Int) { if (id > 0) mutable.tryEmit(id) }
}
