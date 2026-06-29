package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.StyleDetector
import com.example.ui.theme.*

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StyleBankScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    var stylesList by remember { mutableStateOf(listOf<StyleDetector.StyleEntry>()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("الكل") }

    // Dialog state for adding style manually
    var showAddDialog by remember { mutableStateOf(false) }
    var newStyleName by remember { mutableStateOf("") }
    var newStyleSelector by remember { mutableStateOf("") }
    var newStyleRules by remember { mutableStateOf("") }
    var newStyleCategory by remember { mutableStateOf("عام") }

    val categories = listOf("الكل", "أزرار", "بطاقات", "نصوص", "عام")

    // Load styles on launch and when dialog closes
    LaunchedEffect(showAddDialog) {
        stylesList = StyleDetector.loadStyles(context)
    }

    // Filter styles based on search and category
    val filteredStyles = remember(stylesList, searchQuery, selectedCategory) {
        stylesList.filter {
            val matchesSearch = it.name.contains(searchQuery, ignoreCase = true) || 
                                it.selector.contains(searchQuery, ignoreCase = true) ||
                                it.rules.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedCategory == "الكل" || it.category == selectedCategory
            matchesSearch && matchesCategory
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("style_bank_back_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "عودة",
                            tint = MetallicGold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "🏛️ بنك الأنماط الفاخر",
                            color = MetallicGold,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "إدارة وتعديل أنماط CSS المخصصة لمخرجاتك",
                            color = TextGray,
                            fontSize = 11.sp
                        )
                    }
                }

                IconButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier
                        .background(GoldGlassBg, RoundedCornerShape(12.dp))
                        .testTag("add_style_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "إضافة نمط", tint = MetallicGold)
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("بحث عن نمط أو محدد...", color = TextMuted, fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MetallicGold, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "مسح", tint = TextMuted)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .testTag("style_bank_search"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MetallicGold,
                    unfocusedBorderColor = GlassBorder,
                    focusedContainerColor = GlassBlack,
                    unfocusedContainerColor = GlassBlack
                ),
                shape = RoundedCornerShape(12.dp)
            )

            // Category Filter Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                categories.forEach { cat ->
                    val isSelected = cat == selectedCategory
                    Box(
                        modifier = Modifier
                            .background(
                                if (isSelected) MetallicGold else GlassBorder,
                                RoundedCornerShape(20.dp)
                            )
                            .clickable { selectedCategory = cat }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = cat,
                            color = if (isSelected) SlateBg else TextSilver,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // List of Styles
            if (filteredStyles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🏛️", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "لا توجد أنماط متطابقة حالياً",
                            color = TextMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(filteredStyles) { style ->
                        StyleCard(
                            style = style,
                            onApply = {
                                val sp = context.getSharedPreferences("SmartCapturePrefs", Context.MODE_PRIVATE)
                                sp.edit().putString("custom_css", style.rules).apply()
                                Toast.makeText(context, "🎨 تم تطبيق النمط كنمط CSS مخصص افتراضي لمستنداتك!", Toast.LENGTH_SHORT).show()
                            },
                            onCopy = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("CSS Style", "${style.selector} {\n  ${style.rules}\n}")
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "📋 تم نسخ كود CSS للحافظة!", Toast.LENGTH_SHORT).show()
                            },
                            onDelete = {
                                StyleDetector.deleteStyle(context, style.selector)
                                stylesList = StyleDetector.loadStyles(context)
                                Toast.makeText(context, "🗑️ تم حذف النمط بنجاح", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }

        // Add Style Dialog
        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("🏛️ إضافة نمط مخصص جديد", color = MetallicGold, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = newStyleName,
                            onValueChange = { newStyleName = it },
                            label = { Text("اسم النمط (مثال: زر نيون برّاق)") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MetallicGold,
                                unfocusedBorderColor = GlassBorder
                            )
                        )

                        OutlinedTextField(
                            value = newStyleSelector,
                            onValueChange = { newStyleSelector = it },
                            label = { Text("المحدد (مثال: .btn-neon)") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MetallicGold,
                                unfocusedBorderColor = GlassBorder
                            )
                        )

                        OutlinedTextField(
                            value = newStyleRules,
                            onValueChange = { newStyleRules = it },
                            label = { Text("قواعد الـ CSS (مثال: color: red;)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MetallicGold,
                                unfocusedBorderColor = GlassBorder
                            )
                        )

                        // Category Dropdown Selection (represented visually as row of chips)
                        Column {
                            Text("الفئة المستهدفة:", color = TextSilver, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("أزرار", "بطاقات", "نصوص", "عام").forEach { cat ->
                                    val isSel = cat == newStyleCategory
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (isSel) MetallicGold else GlassBorder,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable { newStyleCategory = cat }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = cat,
                                            color = if (isSel) SlateBg else TextSilver,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newStyleName.isBlank() || newStyleSelector.isBlank() || newStyleRules.isBlank()) {
                                Toast.makeText(context, "الرجاء تعبئة جميع الحقول المطلوبة", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val entry = StyleDetector.StyleEntry(
                                name = newStyleName,
                                selector = newStyleSelector,
                                rules = newStyleRules,
                                category = newStyleCategory,
                                createdAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
                            )
                            StyleDetector.addStyle(context, entry)
                            showAddDialog = false
                            newStyleName = ""
                            newStyleSelector = ""
                            newStyleRules = ""
                            newStyleCategory = "عام"
                            Toast.makeText(context, "🏛️ تم تسجيل النمط بنجاح!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = SlateBg)
                    ) {
                        Text("حفظ النمط")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text("إلغاء", color = TextSilver)
                    }
                },
                containerColor = SlateBg,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

@Composable
fun StyleCard(
    style: StyleDetector.StyleEntry,
    onApply: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .background(CardSlateBg, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = style.name,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = style.selector,
                        color = MetallicGold,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .background(GoldGlassBg, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(text = style.category, color = MetallicGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Rules Body Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GlassBlack, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = style.rules,
                    color = TextSilver,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    maxLines = 4
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "أضيف في: ${style.createdAt}",
                    color = TextMuted,
                    fontSize = 9.sp
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = DangerRed.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                    }

                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "نسخ", tint = TextSilver, modifier = Modifier.size(16.dp))
                    }

                    Button(
                        onClick = onApply,
                        modifier = Modifier.height(32.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = SlateBg),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = SlateBg, modifier = Modifier.size(12.dp))
                            Text("تطبيق المظهر", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
