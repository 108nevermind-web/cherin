package com.cherin.edupsych.data

data class PhotoRecord(
    val id: String,
    val uri: String,
    val takenAt: String?,     // formatted from EXIF, e.g. "2026년 05월 21일 14:30"
    val latitude: Double?,
    val longitude: Double?,
    val address: String?,
    val savedAt: Long,        // System.currentTimeMillis()
)
