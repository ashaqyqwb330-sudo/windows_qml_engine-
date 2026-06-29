package com.example

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun StoryCardView(
    story: StoryCard,
    modifier: Modifier = Modifier,
    onCopyClick: (String) -> Unit = {},
    onFileClick: (String) -> Unit = {}
) {
    var isExpanded by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (story.totalCount > 0) story.successCount.toFloat() / story.totalCount.toFloat() else 1f,
        label = "success_ratio"
    )

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("story_card_${story.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Row 1: Large Icon + Title + Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Large circular frame for Icon
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(GoldGlassBg, CircleShape)
                        .border(1.dp, MetallicGold.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = story.icon,
                        fontSize = 22.sp
                    )
                }

                // Title and Relative Time
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = story.title,
                        color = TextSilver,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = story.time,
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Divider Line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.6.dp)
                    .background(GlassBorder)
            )

            // Row 2: Dynamic Summary
            Text(
                text = story.summary,
                color = TextSilver,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Normal
            )

            // Row 3: Progress rate & numbers
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GlassWhite, RoundedCornerShape(12.dp))
                    .border(0.5.dp, GlassBorder, RoundedCornerShape(12.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📊 إحصائيات العمليات:",
                        color = MetallicGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "نجح: ${story.successCount}",
                            color = EmeraldGlow,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "فشل: ${story.totalCount - story.successCount}",
                            color = if (story.totalCount - story.successCount > 0) DangerRed else TextMuted,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Progress Bar
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = if (progress == 1f) EmeraldGlow else MetallicGold,
                    trackColor = GlassWhiteHeavy
                )
            }

            // Expanded Area showing Raw logs (Events)
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GlassBlack, RoundedCornerShape(12.dp))
                        .border(0.8.dp, GlassBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "📋 الأحداث التفصيلية الخام:",
                        color = TextGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    story.rawEvents.forEachIndexed { index, event ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "•",
                                color = MetallicGold,
                                fontSize = 12.sp
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = event.message,
                                    color = TextSilver,
                                    fontSize = 10.5.sp,
                                    lineHeight = 15.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                if (!event.details.isNullOrEmpty()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = event.details,
                                        color = TextMuted,
                                        fontSize = 9.sp,
                                        lineHeight = 13.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 4,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        if (index < story.rawEvents.size - 1) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(0.4.dp)
                                    .background(GlassBorder)
                                    .padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Row 4: Interactive Buttons (Copy, Details, Open File)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onCopyClick(story.summary) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GlassWhiteMedium,
                        contentColor = TextSilver
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("copy_story_summary_btn")
                ) {
                    Text(
                        text = "📋 نسخ القصة",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = { isExpanded = !isExpanded },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isExpanded) MetallicGold.copy(alpha = 0.2f) else GlassWhiteMedium,
                        contentColor = TextSilver
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("details_story_btn")
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isExpanded) "收 إخفاء التفاصيل" else "🔍 تفاصيل",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // If path exists, show "Open File" button (Task 6)
                if (!story.filePath.isNullOrBlank()) {
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = { onFileClick(story.filePath) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldGlassBg,
                            contentColor = BrightGold
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("open_file_story_btn")
                    ) {
                        Text(
                            text = "📂 فتح الملف",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
