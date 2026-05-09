package com.cherin.edupsych.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
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
import androidx.glance.text.TextStyle
import com.cherin.edupsych.MainActivity
import com.cherin.edupsych.data.PaperRepository

/**
 * Home-screen widget. Adapts to three discrete sizes via Glance's
 * [SizeMode.Responsive] — the system picks the bucket that best fits the
 * actual cell allocation, so the user can shrink to a counter or expand to
 * a "title + first sentence of abstract" view without us hand-rolling
 * conditional measurement logic.
 */
class TodayWidget : GlanceAppWidget() {

    private val sizeSmall = DpSize(110.dp, 110.dp)
    private val sizeMedium = DpSize(180.dp, 130.dp)
    private val sizeLarge = DpSize(280.dp, 180.dp)

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(sizeSmall, sizeMedium, sizeLarge)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val papers = PaperRepository.load(context)
        val prefs = context.getSharedPreferences("edupsych", Context.MODE_PRIVATE)
        val paper = PaperRepository.paperForToday(context, prefs)
        val total = papers.size
        val dayNum = paper.dayIndex + 1

        provideContent {
            GlanceTheme {
                val size = LocalSize.current
                when {
                    size.width < sizeMedium.width || size.height < sizeMedium.height ->
                        SmallContent(dayNum = dayNum, total = total)
                    size.width >= sizeLarge.width && size.height >= sizeLarge.height ->
                        LargeContent(
                            title = paper.title,
                            abstract = paper.abstract,
                            citedBy = paper.citedBy,
                            year = paper.year,
                            dayNum = dayNum,
                            total = total,
                        )
                    else ->
                        MediumContent(
                            title = paper.title,
                            citedBy = paper.citedBy,
                            year = paper.year,
                            dayNum = dayNum,
                            total = total,
                        )
                }
            }
        }
    }

    /** Compact: just a big "Day N / 365" counter. Tap opens app. */
    @Composable
    private fun SmallContent(dayNum: Int, total: Int) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .padding(8.dp)
                .clickable(actionStartActivity<MainActivity>()),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Day",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = GlanceTheme.colors.onSurfaceVariant,
                    ),
                )
                Text(
                    text = "$dayNum",
                    style = TextStyle(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlanceTheme.colors.primary,
                    ),
                )
                Text(
                    text = "/ $total",
                    style = TextStyle(
                        fontSize = 10.sp,
                        color = GlanceTheme.colors.onSurfaceVariant,
                    ),
                )
            }
        }
    }

    /** Original layout: day counter + title (4 lines) + cite/year footer. */
    @Composable
    private fun MediumContent(
        title: String,
        citedBy: Int,
        year: Int,
        dayNum: Int,
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
                    text = "Day $dayNum / $total",
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

    /** Large: medium layout + first sentence of abstract for skim-reading. */
    @Composable
    private fun LargeContent(
        title: String,
        abstract: String,
        citedBy: Int,
        year: Int,
        dayNum: Int,
        total: Int,
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .padding(14.dp)
                .clickable(actionStartActivity<MainActivity>())
        ) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                Text(
                    text = "Day $dayNum / $total",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = GlanceTheme.colors.onSurfaceVariant,
                    ),
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = title,
                    maxLines = 3,
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = GlanceTheme.colors.onSurface,
                    ),
                )
                Spacer(modifier = GlanceModifier.height(6.dp))
                Text(
                    text = firstSentence(abstract),
                    maxLines = 4,
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = GlanceTheme.colors.onSurfaceVariant,
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

    private fun firstSentence(s: String): String {
        // OpenAlex abstracts are plain text. Cut at first period followed by space.
        val cut = s.indexOf(". ").takeIf { it in 20..400 } ?: 200.coerceAtMost(s.length - 1)
        return s.substring(0, cut + 1).trim()
    }
}
