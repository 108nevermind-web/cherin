package com.cherin.edupsych.data

data class Paper(
    val dayIndex: Int,
    val id: String,
    val doi: String?,
    val title: String,
    val abstract: String,
    val year: Int,
    val citedBy: Int,
    val scorePerYear: Double,
    val authors: List<String>,
    val oaPdfUrl: String?,
)
