package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun SettingsTooltip(
    title: String,
    description: String,
    example: String? = null,
    testTag: String = "tooltip_button"
) {
    var showDialog by remember { mutableStateOf(false) }

    IconButton(
        onClick = { showDialog = true },
        modifier = Modifier
            .size(28.dp)
            .testTag(testTag)
    ) {
        Text(
            text = "💡",
            fontSize = 14.sp
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = true
            ),
            modifier = Modifier
                .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                .testTag("tooltip_dialog_${title}"),
            containerColor = CardSlateBg,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "💡 $title",
                        color = BrightGold,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = description,
                        color = TextSilver,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (!example.isNullOrEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(GlassWhite, RoundedCornerShape(8.dp))
                                .border(0.5.dp, GlassBorder, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "مثال توضيحي:",
                                    color = MetallicGold,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = example,
                                    color = TextGray,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MetallicGold),
                    modifier = Modifier.testTag("tooltip_close_button")
                ) {
                    Text("حسناً فهمت", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        )
    }
}
