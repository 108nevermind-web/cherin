package com.cherin.edupsych

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.compose.ui.graphics.Color
import com.cherin.edupsych.data.EnKoTranslator
import com.cherin.edupsych.data.FavoritesStore
import com.cherin.edupsych.data.Paper
import com.cherin.edupsych.data.PaperRepository
import com.cherin.edupsych.data.WeeklyRefreshWorker
import com.cherin.edupsych.notify.DailyNotificationWorker
import com.cherin.edupsych.notify.DailyNotifier
import com.cherin.edupsych.widget.FavoritesWidget
import com.cherin.edupsych.widget.WidgetUpdateWorker
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result ignored */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { WidgetUpdateWorker.schedule(applicationContext) }
        runCatching { DailyNotificationWorker.schedule(applicationContext) }
        runCatching { WeeklyRefreshWorker.schedule(applicationContext) }
        maybeRequestNotificationPermission()
        setContent {
            EduPsychTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

/**
 * App-wide theme. Follows the system day/night setting and uses Material You
 * dynamic colors on Android 12+ (so the user's wallpaper drives the palette);
 * older devices fall back to the Material 3 baseline schemes.
 *
 * The favorite-star color is hard-coded gold (#E8B33A) — it stays consistent
 * across light/dark on purpose so "is this favorited?" is recognizable
 * regardless of the surface beneath it.
 */
@Composable
fun EduPsychTheme(
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val papers = remember { PaperRepository.load(context) }
    val prefs = remember { context.getSharedPreferences("edupsych", Context.MODE_PRIVATE) }

    val today = remember { LocalDate.now(ZoneId.systemDefault()).toEpochDay() }
    val installDay = remember {
        prefs.getLong("installEpochDay", -1L).let {
            if (it < 0) {
                prefs.edit { putLong("installEpochDay", today) }
                today
            } else it
        }
    }
    val todayIdx = remember {
        (((today - installDay) % papers.size) + papers.size).toInt() % papers.size
    }

    var dayIdx by rememberSaveable { mutableIntStateOf(todayIdx) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var favorites by remember { mutableStateOf(FavoritesStore.load(context)) }

    val scope = rememberCoroutineScope()
    val toggleFavorite: (String) -> Unit = { id ->
        val updated = FavoritesStore.toggle(favorites, id)
        favorites = updated
        FavoritesStore.save(context, updated)
        // Push the change to the home-screen FavoritesWidget so a tap on the
        // star reflects there immediately, not at the next midnight refresh.
        scope.launch { FavoritesWidget().updateAll(context) }
    }

    when {
        showSettings -> SettingsScreen(
            paperCount = papers.size,
            onBack = { showSettings = false },
        )
        showHistory -> HistoryScreen(
            papers = papers,
            todayIdx = todayIdx,
            currentIdx = dayIdx,
            favorites = favorites,
            onSelect = { idx ->
                dayIdx = idx
                showHistory = false
            },
            onBack = { showHistory = false },
        )
        else -> TodayScreen(
            papers = papers,
            dayIdx = dayIdx,
            todayIdx = todayIdx,
            favorites = favorites,
            onToggleFavorite = toggleFavorite,
            onPrev = { dayIdx = (dayIdx - 1 + papers.size) % papers.size },
            onNext = { dayIdx = (dayIdx + 1) % papers.size },
            onJumpToday = { dayIdx = todayIdx },
            onShowHistory = { showHistory = true },
            onShowSettings = { showSettings = true },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TodayScreen(
    papers: List<Paper>,
    dayIdx: Int,
    todayIdx: Int,
    favorites: Set<String>,
    onToggleFavorite: (String) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onJumpToday: () -> Unit,
    onShowHistory: () -> Unit,
    onShowSettings: () -> Unit,
) {
    val context = LocalContext.current
    val paper = papers[dayIdx]
    val notificationToast = stringResource(R.string.notification_test_toast)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        // Top row: Day label (long-press = test notification) + History button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.day_label_format, dayIdx + 1, papers.size),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = {
                        DailyNotifier.postToday(context)
                        Toast.makeText(context, notificationToast, Toast.LENGTH_SHORT).show()
                    },
                ),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (dayIdx != todayIdx) {
                    TextButton(onClick = onJumpToday) {
                        Text(stringResource(R.string.go_today), fontSize = 12.sp)
                    }
                }
                TextButton(onClick = onShowHistory) {
                    Text(stringResource(R.string.open_history), fontSize = 12.sp)
                }
                TextButton(
                    onClick = onShowSettings,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.semantics {
                        contentDescription = "설정"
                    },
                ) {
                    Text(text = "⚙️", fontSize = 16.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        PaperCard(
            paper = paper,
            isFavorite = paper.id in favorites,
            onToggleFavorite = { onToggleFavorite(paper.id) },
        )

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(onClick = onPrev) {
                Text(stringResource(R.string.prev_day))
            }
            OutlinedButton(onClick = onNext) {
                Text(stringResource(R.string.next_day))
            }
        }
    }
}

@Composable
fun HistoryScreen(
    papers: List<Paper>,
    todayIdx: Int,
    currentIdx: Int,
    favorites: Set<String>,
    onSelect: (Int) -> Unit,
    onBack: () -> Unit,
) {
    var showFavoritesOnly by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val displayed = remember(showFavoritesOnly, favorites, papers, query) {
        val base = if (showFavoritesOnly) papers.filter { it.id in favorites } else papers
        val q = query.trim()
        if (q.isEmpty()) base
        else base.filter { p ->
            p.title.contains(q, ignoreCase = true) ||
                p.authors.any { it.contains(q, ignoreCase = true) }
        }
    }

    // Scroll to current paper when opening the unfiltered all-list view; any
    // filtering (favorites or search) jumps to top so the user sees results.
    LaunchedEffect(showFavoritesOnly, query.isEmpty()) {
        if (!showFavoritesOnly && query.isEmpty()) {
            listState.scrollToItem(currentIdx.coerceIn(0, papers.size - 1))
        } else {
            listState.scrollToItem(0)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.history_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.close_history))
            }
        }
        Spacer(Modifier.height(8.dp))

        // Search bar
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.search_hint), fontSize = 13.sp) },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = null)
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(
                            Icons.Filled.Clear,
                            contentDescription = stringResource(R.string.search_clear),
                        )
                    }
                }
            },
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))

        // Filter tabs (counts always reflect total population, not filtered results)
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterTab(
                label = stringResource(R.string.filter_all, papers.size),
                selected = !showFavoritesOnly,
                onClick = { showFavoritesOnly = false },
            )
            Spacer(Modifier.width(4.dp))
            FilterTab(
                label = stringResource(R.string.filter_favorites, favorites.size),
                selected = showFavoritesOnly,
                onClick = { showFavoritesOnly = true },
            )
        }
        HorizontalDivider()

        if (displayed.isEmpty()) {
            val emptyMsg = if (query.isNotEmpty()) {
                stringResource(R.string.search_empty)
            } else {
                stringResource(R.string.favorites_empty)
            }
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = emptyMsg,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
            ) {
                items(displayed, key = { it.id }) { paper ->
                    HistoryRow(
                        paper = paper,
                        isToday = paper.dayIndex == todayIdx,
                        isCurrent = paper.dayIndex == currentIdx,
                        isFavorite = paper.id in favorites,
                        onClick = { onSelect(paper.dayIndex) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

/**
 * Settings screen — replaces the cramped 4-button row that used to live in
 * the TodayScreen header. Two sections:
 *
 *   알림 — Switch for ON/OFF + tap-to-edit "알림 시간 09:00"
 *   정보 — version, paper count, source attribution
 *
 * State for notify time/enabled is hoisted locally and mirrors
 * SharedPreferences (read on first composition); writes go through
 * [DailyNotificationWorker] companion which both persists and re-enqueues.
 */
@Composable
fun SettingsScreen(
    paperCount: Int,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var hour by rememberSaveable { mutableIntStateOf(DailyNotificationWorker.savedHour(context)) }
    var minute by rememberSaveable { mutableIntStateOf(DailyNotificationWorker.savedMinute(context)) }
    var enabled by rememberSaveable { mutableStateOf(DailyNotificationWorker.savedEnabled(context)) }

    val confirmTemplate = stringResource(R.string.notify_time_changed)
    val onToast = stringResource(R.string.notify_enabled_toast)
    val offToast = stringResource(R.string.notify_disabled_toast)

    // If the user previously denied POST_NOTIFICATIONS, flipping the Switch on
    // would silently fail to deliver alarms. Re-prompt at toggle time so the
    // setting matches reality. Permission is irrelevant on Android < 13.
    val notifyPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            enabled = true
            DailyNotificationWorker.setEnabled(context, true)
            Toast.makeText(context, onToast, Toast.LENGTH_SHORT).show()
        } else {
            // Permission denied — keep the switch off so it visually matches.
            enabled = false
            DailyNotificationWorker.setEnabled(context, false)
            Toast.makeText(
                context,
                "알림 권한이 거부되었습니다. 시스템 설정에서 허용해주세요.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.close_history))
            }
        }
        Spacer(Modifier.height(16.dp))

        // 알림 section
        SectionHeader(stringResource(R.string.settings_section_notify))

        SettingRow(
            label = stringResource(R.string.settings_notify_enabled),
            trailing = {
                Switch(
                    checked = enabled,
                    onCheckedChange = { newState ->
                        if (newState) {
                            // Turning on — confirm we still have notification
                            // permission (Android 13+); if not, ask again. On
                            // older Android the permission is implicit so we
                            // skip straight to enabling.
                            val needsPerm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                            val hasPerm = !needsPerm ||
                                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                                    PackageManager.PERMISSION_GRANTED
                            if (hasPerm) {
                                enabled = true
                                DailyNotificationWorker.setEnabled(context, true)
                                Toast.makeText(context, onToast, Toast.LENGTH_SHORT).show()
                            } else {
                                notifyPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        } else {
                            enabled = false
                            DailyNotificationWorker.setEnabled(context, false)
                            Toast.makeText(context, offToast, Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            },
        )

        SettingRow(
            label = stringResource(R.string.settings_notify_time),
            onClick = if (enabled) {
                {
                    TimePickerDialog(
                        context,
                        { _, h, m ->
                            hour = h
                            minute = m
                            enabled = true
                            DailyNotificationWorker.reschedule(context, h, m)
                            Toast.makeText(
                                context,
                                String.format(confirmTemplate, h, m),
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                        hour,
                        minute,
                        false,
                    ).show()
                }
            } else null,
            trailing = {
                Text(
                    text = if (enabled)
                        stringResource(R.string.notify_time_label, hour, minute)
                    else
                        stringResource(R.string.notify_time_off_label),
                    fontSize = 14.sp,
                    color = if (enabled)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                )
            },
        )

        Spacer(Modifier.height(24.dp))

        // 정보 section
        SectionHeader(stringResource(R.string.settings_section_info))
        SettingRow(
            label = stringResource(R.string.settings_info_version),
            trailing = { ValueText(versionName) },
        )
        SettingRow(
            label = stringResource(R.string.settings_info_count),
            trailing = {
                ValueText(stringResource(R.string.settings_info_count_value, paperCount))
            },
        )
        SettingRow(
            label = stringResource(R.string.settings_info_source),
            trailing = {
                ValueText(stringResource(R.string.settings_info_source_value))
            },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 6.dp),
    )
    HorizontalDivider()
}

@Composable
private fun SettingRow(
    label: String,
    trailing: @Composable () -> Unit,
    onClick: (() -> Unit)? = null,
) {
    val rowMod = Modifier
        .fillMaxWidth()
        .let { if (onClick != null) it.clickable { onClick() } else it }
        .padding(vertical = 14.dp)

    Row(
        modifier = rowMod,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 14.sp)
        trailing()
    }
    HorizontalDivider()
}

@Composable
private fun ValueText(value: String) {
    Text(
        text = value,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    )
}

@Composable
private fun FilterTab(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

@Composable
fun HistoryRow(
    paper: Paper,
    isToday: Boolean,
    isCurrent: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.width(72.dp)) {
            Text(
                text = stringResource(R.string.day_short_format, paper.dayIndex + 1),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
            if (isToday) {
                Text(
                    text = "오늘",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = paper.title,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = 2,
                    modifier = Modifier.weight(1f),
                )
                if (isFavorite) {
                    Text(
                        text = "★",
                        fontSize = 16.sp,
                        color = Color(0xFFE8B33A),
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.cite_year_short, paper.citedBy, paper.year),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
fun PaperCard(
    paper: Paper,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var titleKo by rememberSaveable(paper.id) { mutableStateOf<String?>(null) }
    var abstractKo by rememberSaveable(paper.id) { mutableStateOf<String?>(null) }
    var translating by remember(paper.id) { mutableStateOf(false) }
    var error by remember(paper.id) { mutableStateOf<String?>(null) }
    var showKorean by rememberSaveable(paper.id) { mutableStateOf(false) }

    val translationFailedMsg = stringResource(R.string.translation_failed)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {

            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = paper.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 24.sp,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(onClick = onToggleFavorite),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (isFavorite) "★" else "☆",
                        fontSize = 24.sp,
                        color = if (isFavorite) Color(0xFFE8B33A)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    )
                }
            }
            if (showKorean && titleKo != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = titleKo!!,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary,
                    lineHeight = 22.sp,
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(
                    R.string.cited_by_format,
                    paper.citedBy,
                    paper.year,
                    paper.scorePerYear.toFloat(),
                ),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )

            if (paper.authors.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = paper.authors.joinToString(", "),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = paper.abstract,
                fontSize = 14.sp,
                lineHeight = 22.sp,
            )
            if (showKorean && abstractKo != null) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))
                Text(
                    text = abstractKo!!,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    lineHeight = 22.sp,
                )
            }

            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    enabled = !translating,
                    onClick = {
                        if (showKorean) { showKorean = false; return@Button }
                        if (titleKo != null && abstractKo != null) { showKorean = true; return@Button }
                        translating = true
                        error = null
                        scope.launch {
                            runCatching {
                                titleKo = EnKoTranslator.translate(paper.title)
                                abstractKo = EnKoTranslator.translate(paper.abstract)
                                showKorean = true
                            }.onFailure { error = translationFailedMsg }
                            translating = false
                        }
                    }
                ) {
                    Text(
                        when {
                            translating -> stringResource(R.string.translating)
                            showKorean -> stringResource(R.string.hide_korean)
                            else -> stringResource(R.string.show_korean)
                        }
                    )
                }

                Spacer(Modifier.width(12.dp))

                paper.doi?.let { doi ->
                    TextButton(onClick = { openUrl(context, "https://doi.org/$doi") }) {
                        Text(stringResource(R.string.open_doi))
                    }
                }
            }
        }
    }
}

private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    // No browser / link handler installed (rare but happens on slim AOSP builds)
    // would otherwise crash the app with ActivityNotFoundException.
    runCatching { context.startActivity(intent) }
        .onFailure {
            Toast.makeText(
                context,
                "열 수 있는 앱이 없습니다",
                Toast.LENGTH_SHORT,
            ).show()
        }
}
