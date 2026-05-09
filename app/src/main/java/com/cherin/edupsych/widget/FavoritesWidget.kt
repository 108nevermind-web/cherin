package com.cherin.edupsych.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.cherin.edupsych.MainActivity
import com.cherin.edupsych.data.FavoritesStore
import com.cherin.edupsych.data.PaperRepository
import java.time.LocalDate
import java.time.ZoneId

/**
 * Sibling to [TodayWidget]. Cycles through the user's favorited papers, one per
 * day, using `epochDay mod favs.size` so the widget shows a rotating "memory"
 * of starred items even after the user has read them.
 *
 * Empty state: if no favorites yet, shows a friendly nudge telling the user
 * to tap ☆ in the app. Tapping anywhere always opens the app.
 *
 * Single layout (no SizeMode.Responsive) — favorites widget is intentionally
 * simpler than the daily one to keep code duplication low.
 */
class FavoritesWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val papers = PaperRepository.load(context)
        val favSet = FavoritesStore.load(context)
        val favs = papers.filter { it.id in favSet }
        val today = LocalDate.now(ZoneId.systemDefault()).toEpochDay()

        provideContent {
            GlanceTheme {
                if (favs.isEmpty()) {
                    EmptyContent()
                } else {
                    val idx = (((today % favs.size) + favs.size) % favs.size).toInt()
                    val paper = favs[idx]
                    FavoriteContent(
                        title = paper.title,
                        citedBy = paper.citedBy,
                        year = paper.year,
                        position = idx + 1,
                        total = favs.size,
                    )
                }
            }
        }
    }

    @Composable
    private fun EmptyContent() {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "★ 즐겨찾기 비어있음\n앱에서 별을 눌러 추가하세요",
                style = TextStyle(
                    fontSize = 12.sp,
                    color = GlanceTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }

    @Composable
    private fun FavoriteContent(
        title: String,
        citedBy: Int,
        year: Int,
        position: Int,
        total: Int,
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>())
        ) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                Text(
                    text = "★ 즐겨찾기  $position / $total",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = GlanceTheme.colors.onSurfaceVariant,
                    ),
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = title,
                    maxLines = 4,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = GlanceTheme.colors.onSurface,
                    ),
                )
                Spacer(modifier = GlanceModifier.height(6.dp))
                Text(
                    text = "인용 $citedBy  ·  $year",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = GlanceTheme.colors.primary,
                    ),
                )
            }
        }
    }
}
