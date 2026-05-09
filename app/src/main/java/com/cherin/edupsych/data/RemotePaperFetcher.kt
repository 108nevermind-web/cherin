package com.cherin.edupsych.data

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Optionally fetches a newer papers.json from a static host (GitHub Pages),
 * caches it to internal storage, and exposes a fallback chain:
 *
 *     remote(if-modified) -> cached file -> bundled APK asset
 *
 * Triggered by [WeeklyRefreshWorker]; never blocks app startup. The repository
 * just looks at [cachedFile]; if missing/empty it reads the bundled asset.
 *
 * No backend: the URL points at a static JSON file you publish manually
 * (push the same file from `assets/papers.json` to a GitHub Pages repo). If
 * the URL is left at the placeholder default, fetching is a silent no-op so
 * the app still works on a fresh install with only the bundled data.
 */
object RemotePaperFetcher {

    private const val TAG = "RemoteFetcher"
    private const val PREFS = "edupsych"
    private const val KEY_ETAG = "papers_etag"
    private const val KEY_LAST_MODIFIED = "papers_last_modified"
    private const val KEY_LAST_FETCH_MS = "papers_last_fetch_ms"
    private const val CACHE_FILE = "papers_remote.json"

    /**
     * Set this to your published static URL (e.g. GitHub Pages, S3, Netlify
     * static, etc.). Leave as `null` or empty to disable remote refresh —
     * the app will simply use the bundled assets/papers.json forever.
     */
    private const val REMOTE_URL: String = ""

    /** True if a cached remote copy exists and should be preferred. */
    fun cachedFile(context: Context): File? {
        val f = File(context.filesDir, CACHE_FILE)
        return if (f.exists() && f.length() > 0) f else null
    }

    /**
     * Try to fetch a newer papers.json. Returns true if cache changed,
     * false if 304 Not Modified, no URL configured, or any error.
     */
    fun refresh(context: Context): Boolean {
        if (REMOTE_URL.isBlank()) {
            Log.d(TAG, "REMOTE_URL not configured; using bundled assets only.")
            return false
        }

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val etag = prefs.getString(KEY_ETAG, null)
        val lastMod = prefs.getString(KEY_LAST_MODIFIED, null)

        val conn = (URL(REMOTE_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            requestMethod = "GET"
            setRequestProperty("Accept-Encoding", "gzip")
            etag?.let { setRequestProperty("If-None-Match", it) }
            lastMod?.let { setRequestProperty("If-Modified-Since", it) }
        }

        return try {
            val code = conn.responseCode
            when (code) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> {
                    Log.d(TAG, "304 Not Modified")
                    prefs.edit { putLong(KEY_LAST_FETCH_MS, System.currentTimeMillis()) }
                    false
                }
                HttpURLConnection.HTTP_OK -> {
                    val tmp = File(context.filesDir, "$CACHE_FILE.tmp")
                    val stream = if (conn.contentEncoding == "gzip")
                        java.util.zip.GZIPInputStream(conn.inputStream)
                    else conn.inputStream
                    stream.use { input ->
                        FileOutputStream(tmp).use { out -> input.copyTo(out) }
                    }
                    // atomic-ish swap
                    val target = File(context.filesDir, CACHE_FILE)
                    if (target.exists()) target.delete()
                    val renamed = tmp.renameTo(target)
                    if (!renamed) {
                        Log.w(TAG, "rename failed; copying instead")
                        tmp.inputStream().use { input ->
                            FileOutputStream(target).use { out -> input.copyTo(out) }
                        }
                        tmp.delete()
                    }
                    prefs.edit {
                        conn.getHeaderField("ETag")?.let { putString(KEY_ETAG, it) }
                        conn.getHeaderField("Last-Modified")?.let { putString(KEY_LAST_MODIFIED, it) }
                        putLong(KEY_LAST_FETCH_MS, System.currentTimeMillis())
                    }
                    PaperRepository.invalidate()
                    Log.i(TAG, "papers.json updated (${target.length()} bytes)")
                    true
                }
                else -> {
                    Log.w(TAG, "unexpected response $code")
                    false
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetch failed: ${e.message}")
            false
        } finally {
            conn.disconnect()
        }
    }
}
