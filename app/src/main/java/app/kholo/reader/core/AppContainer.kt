package app.kholo.reader.core

import app.kholo.reader.core.log.RingBufferTree

/** Manual DI: app ke saare shared objects yahin bante hain. Hilt ka startup kharcha nahi. */
class AppContainer(
    val logBuffer: RingBufferTree,
    val dispatchers: AppDispatchers = AppDispatchers(),
)
