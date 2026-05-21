package com.cherin.edupsych.photo

import android.content.Context
import android.location.Geocoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import coil.compose.AsyncImage
import com.cherin.edupsych.data.PhotoRecord
import com.cherin.edupsych.data.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

@Composable
fun PhotoScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var records by remember { mutableStateOf(PhotoStore.load(context)) }
    var loading by remember { mutableStateOf(false) }

    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        loading = true
        scope.launch {
            val record = buildRecord(context, uri)
            records = PhotoStore.add(context, record)
            loading = false
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    pickMedia.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            ) {
                Icon(Icons.Filled.Add, contentDescription = "사진 추가")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("사진 기록", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onBack) { Text("닫기") }
            }

            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            } else {
                Spacer(Modifier.height(8.dp))
            }

            HorizontalDivider()

            if (records.isEmpty() && !loading) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "사진을 추가하면 촬영 위치와 시간이 저장됩니다.\n우측 하단 + 버튼을 눌러보세요.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(records.sortedByDescending { it.savedAt }, key = { it.id }) { record ->
                        PhotoRecordRow(
                            record = record,
                            onDelete = { records = PhotoStore.remove(context, record.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoRecordRow(record: PhotoRecord, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = record.uri,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            when {
                record.address != null ->
                    Text(record.address, fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp)
                record.latitude != null && record.longitude != null ->
                    Text(
                        "%.4f, %.4f".format(record.latitude, record.longitude),
                        fontSize = 13.sp,
                    )
                else ->
                    Text(
                        "위치 정보 없음",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = record.takenAt ?: "촬영 시간 없음",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = if (record.takenAt != null) 0.6f else 0.4f
                ),
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "삭제",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private suspend fun buildRecord(context: Context, uri: Uri): PhotoRecord =
    withContext(Dispatchers.IO) {
        var takenAt: String? = null
        var latitude: Double? = null
        var longitude: Double? = null
        var address: String? = null

        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                takenAt = exif.getAttribute(ExifInterface.TAG_DATETIME)?.let { raw ->
                    // EXIF format: "YYYY:MM:DD HH:MM:SS" → "YYYY년 MM월 DD일 HH:MM"
                    runCatching {
                        val parts = raw.split(" ")
                        val dateParts = parts[0].split(":")
                        val timeParts = parts[1].split(":")
                        "${dateParts[0]}년 ${dateParts[1]}월 ${dateParts[2]}일 ${timeParts[0]}:${timeParts[1]}"
                    }.getOrNull() ?: raw
                }
                val latLon = FloatArray(2)
                if (exif.getLatLong(latLon)) {
                    latitude = latLon[0].toDouble()
                    longitude = latLon[1].toDouble()
                }
            }
        }

        if (latitude != null && longitude != null) {
            runCatching {
                @Suppress("DEPRECATION")
                val results = Geocoder(context, Locale.KOREA)
                    .getFromLocation(latitude!!, longitude!!, 1)
                address = results?.firstOrNull()?.getAddressLine(0)
            }
        }

        PhotoRecord(
            id = UUID.randomUUID().toString(),
            uri = uri.toString(),
            takenAt = takenAt,
            latitude = latitude,
            longitude = longitude,
            address = address,
            savedAt = System.currentTimeMillis(),
        )
    }
