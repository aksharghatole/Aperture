package com.aperture.photo.library

import android.graphics.Bitmap
import android.util.LruCache
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

internal data class ThumbnailPreview(val bitmap: Bitmap?)

internal class ThumbnailCache(
    maxBytes: Int = minOf(12L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 8).toInt()
) {
    private val lock = Any()
    private val slots = Semaphore(2)
    private var revision = 0L
    private val cache = object : LruCache<File, ThumbnailPreview>(maxBytes) {
        override fun sizeOf(key: File, value: ThumbnailPreview): Int = maxOf(
            value.bitmap?.allocationByteCount ?: 0, 16 * 1024
        )
    }

    suspend fun load(key: File, render: () -> Bitmap): ThumbnailPreview {
        synchronized(lock) { cache.get(key) }?.let { return it }
        return slots.withPermit {
            withContext(Dispatchers.IO) { loadCurrent(key, render) }
        }
    }

    private suspend fun loadCurrent(key: File, render: () -> Bitmap): ThumbnailPreview {
        while (true) {
            val started = synchronized(lock) {
                cache.get(key)?.let { return it }
                revision
            }
            val image = try {
                render()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            } catch (_: OutOfMemoryError) {
                null
            }
            currentCoroutineContext().ensureActive()
            val result = ThumbnailPreview(image)
            synchronized(lock) {
                if (revision == started) {
                    // The view may still be displaying a bitmap after eviction.
                    cache.put(key, result)
                    return result
                }
            }
        }
    }

    fun invalidate(key: File) {
        synchronized(lock) {
            revision++
            cache.remove(key)
        }
    }
}
