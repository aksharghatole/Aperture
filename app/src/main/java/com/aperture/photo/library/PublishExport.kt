package com.aperture.photo.library

/** Do not leave a partial MediaStore item behind if encoding or publication fails. */
internal fun <T> publishPendingExport(
    pending: T,
    write: (T) -> Unit,
    publish: (T) -> Unit,
    cleanup: (T) -> Unit
): T {
    try {
        write(pending)
        publish(pending)
        return pending
    } catch (failure: Throwable) {
        runCatching { cleanup(pending) }
        throw failure
    }
}
