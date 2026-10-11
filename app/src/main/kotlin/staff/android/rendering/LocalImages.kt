package staff.android.rendering

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Trace
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

interface ProductImages {
    fun cached(key: String): Bitmap?
    suspend fun load(key: String): Bitmap?
}

/** Small, screen-owned local-asset loader. No network or application-wide unbounded cache. */
class LocalImages(context: Context) : ProductImages {
    private val assets = context.applicationContext.assets
    private val decoding = Mutex()
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    override fun cached(key: String): Bitmap? = cache.get(key)
    override suspend fun load(key: String): Bitmap? = withContext(Dispatchers.IO) {
        decoding.withLock {
            cache.get(key) ?: run {
                Trace.beginSection("CG21/decode/$key")
                try {
                    assets.open("$key.png").use {
                        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 2 })
                    }?.also { cache.put(key, it) }
                } catch (_: IOException) { null }
                finally { Trace.endSection() }
            }
        }
    }
    suspend fun prepare() {
        for (key in listOf("camera", "tea", "bag", "lamp", "mug", "headphones")) load(key)
    }
}
