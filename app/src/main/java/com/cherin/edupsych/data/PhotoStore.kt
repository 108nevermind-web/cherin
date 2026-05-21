package com.cherin.edupsych.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object PhotoStore {
    private const val FILE_NAME = "photo_records.json"

    fun load(context: Context): List<PhotoRecord> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                PhotoRecord(
                    id = o.getString("id"),
                    uri = o.getString("uri"),
                    takenAt = o.optString("takenAt").ifEmpty { null },
                    latitude = if (o.isNull("latitude")) null else o.getDouble("latitude"),
                    longitude = if (o.isNull("longitude")) null else o.getDouble("longitude"),
                    address = o.optString("address").ifEmpty { null },
                    savedAt = o.getLong("savedAt"),
                )
            }
        }.getOrElse { emptyList() }
    }

    fun save(context: Context, records: List<PhotoRecord>) {
        val arr = JSONArray()
        records.forEach { r ->
            arr.put(JSONObject().apply {
                put("id", r.id)
                put("uri", r.uri)
                put("takenAt", r.takenAt ?: JSONObject.NULL)
                put("latitude", r.latitude ?: JSONObject.NULL)
                put("longitude", r.longitude ?: JSONObject.NULL)
                put("address", r.address ?: JSONObject.NULL)
                put("savedAt", r.savedAt)
            })
        }
        File(context.filesDir, FILE_NAME).writeText(arr.toString())
    }

    fun add(context: Context, record: PhotoRecord): List<PhotoRecord> {
        val updated = load(context) + record
        save(context, updated)
        return updated
    }

    fun remove(context: Context, id: String): List<PhotoRecord> {
        val updated = load(context).filter { it.id != id }
        save(context, updated)
        return updated
    }
}
