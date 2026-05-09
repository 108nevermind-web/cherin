package com.cherin.edupsych.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * Loads papers.json once and serves the day's paper. Source priority:
 *
 *   1. internal-storage cache  (downloaded by [RemotePaperFetcher])
 *   2. APK bundled asset       (`assets/papers.json`)
 *
 * [invalidate] is called by the remote fetcher after a successful update so
 * the next [load] re-reads the cache.
 */
object PaperRepository {

    private const val CACHE_FILE = "papers_remote.json"

    private var cached: List<Paper>? = null

    fun load(context: Context): List<Paper> {
        cached?.let { return it }
        val json = readJson(context)
        val arr = JSONObject(json).getJSONArray("papers")
        val list = buildList(capacity = arr.length()) {
            for (i in 0 until arr.length()) add(arr.getJSONObject(i).toPaper())
        }
        cached = list
        return list
    }

    /** Force the next [load] to re-read from disk (used after remote refresh). */
    fun invalidate() {
        cached = null
    }

    private fun readJson(context: Context): String {
        val cacheFile = File(context.filesDir, CACHE_FILE)
        return if (cacheFile.exists() && cacheFile.length() > 0) {
            runCatching { cacheFile.readText(Charsets.UTF_8) }
                .getOrElse { readBundled(context) }
        } else {
            readBundled(context)
        }
    }

    private fun readBundled(context: Context): String =
        context.assets.open("papers.json").bufferedReader().use { it.readText() }

    fun paperForToday(context: Context, prefs: android.content.SharedPreferences): Paper {
        val papers = load(context)
        check(papers.isNotEmpty()) { "papers.json is empty or malformed" }
        val today = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
        val installDay = prefs.getLong("installEpochDay", -1L).let {
            if (it < 0) {
                prefs.edit().putLong("installEpochDay", today).apply()
                today
            } else it
        }
        val delta = today - installDay
        val idx = ((delta % papers.size) + papers.size) % papers.size
        return papers[idx.toInt()]
    }

    private fun JSONObject.toPaper(): Paper = Paper(
        dayIndex = getInt("dayIndex"),
        id = getString("id"),
        doi = optStringOrNull("doi"),
        title = getString("title"),
        abstract = getString("abstract"),
        year = getInt("year"),
        citedBy = getInt("citedBy"),
        scorePerYear = getDouble("scorePerYear"),
        authors = getJSONArray("authors").toStringList(),
        oaPdfUrl = optStringOrNull("oaPdfUrl"),
    )

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

    private fun JSONArray.toStringList(): List<String> =
        List(length()) { getString(it) }
}
