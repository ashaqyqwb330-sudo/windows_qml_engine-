package com.example

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIPromptHubScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("دليل البدء", "@builder", "@executor", "@treedoc", "القوالب", "نصائح للمساعد")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "🤖 مركز توجيه المساعدات الذكية",
                        color = BrightGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "للخلف",
                            tint = MetallicGold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SlateBg)
            )
        },
        containerColor = SlateBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Elegant Scrollable Tab Row
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = SlateBg,
                contentColor = BrightGold,
                edgePadding = 12.dp,
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.Indicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = BrightGold
                        )
                    }
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == index) BrightGold else TextSilver
                            )
                        },
                        modifier = Modifier.testTag("tab_prompt_$index")
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (selectedTab) {
                    0 -> showQuickStartGuide(context, clipboardManager)
                    1 -> showBuilderGuide(context, clipboardManager)
                    2 -> showExecutorGuide(context, clipboardManager)
                    3 -> showTreeDocGuide(context, clipboardManager)
                    4 -> showTemplatesGuide(context, clipboardManager)
                    5 -> showSystemPromptsGuide(context, clipboardManager)
                }
            }
        }
    }
}

// ==========================================
// 1. QUICK START GUIDE TAB
// ==========================================
private fun androidx.compose.foundation.lazy.LazyListScope.showQuickStartGuide(
    context: android.content.Context,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager
) {
    item {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardSlateBg),
            border = BorderStroke(1.dp, GlassBorder),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📖", fontSize = 24.sp)
                    Text(
                        text = "دليل البدء السريع لـ رفيق الذكاء الاصطناعي",
                        color = BrightGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "تطبيق \"المنصة الذهبية\" هو رفيقك الذكي الاستثنائي. يتيح لك تحويل أي نص، كود، أو تعليمات تنسخها من أي مساعد خارجي (مثل ChatGPT أو DeepSeek أو Gemini) إلى ملفات حقيقية ومجلدات وتقارير شجرية تفاعلية على هاتفك، دون مغادرة تطبيق المحادثة.",
                    color = TextSilver,
                    fontSize = 11.5.sp,
                    lineHeight = 16.5.sp
                )
            }
        }
    }

    item {
        Text(
            text = "⚡ كيف تبدأ في 3 خطوات بسيطة:",
            color = BrightGold,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }

    item {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StepItem(
                number = "1",
                title = "انسخ النص أو الكود الذكي",
                description = "اطلب من المساعد توليد كود أو مجلدات مسبوقة بـ @builder أو @executor، ثم قم بنسخها بالكامل إلى حافظة هاتفك."
            )
            StepItem(
                number = "2",
                title = "الالتقاط التلقائي والذكي",
                description = "بمجرد النسخ، ستقوم المنصة الذهبية بالتقاط النص عبر الخلفية أو الكرة العائمة (Golden Bubble) أو لوحة مفاتيح IME مدمجة."
            )
            StepItem(
                number = "3",
                title = "التنفيذ والمعاينة الفورية",
                description = "يتم فوراً بناء الملفات أو تشغيل الأوامر في مجلد المشروع، ويمكنك فتحها، تعديلها، أو معاينتها بكبسة زر واحدة من السجلات!"
            )
        }
    }

    item {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0x153B82F6)),
            border = BorderStroke(1.dp, Color(0x303B82F6)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "💡 نصيحة احترافية للسرعة الكلية:",
                    color = Color(0xFF60A5FA),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "قم بتفعيل \"الكرة العائمة\" من القائمة الرئيسية، لتتمكن من مراقبة البناء وتجربة الأكواد بشكل فوري ودون الحاجة للتنقل بين التطبيقات!",
                    color = TextSilver,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

// ==========================================
// 2. @BUILDER GUIDE TAB
// ==========================================
private fun androidx.compose.foundation.lazy.LazyListScope.showBuilderGuide(
    context: android.content.Context,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager
) {
    item {
        GuideHeaderCard(
            title = "أداة البناء @builder",
            description = "يستخدم وسم @builder لإنشاء الملفات البرمجية أو تحديثها وتعديلها تلقائياً بالكامل في المجلد النشط بمجرد نسخ الكود."
        )
    }

    item {
        Text(
            text = "✍️ نحو التوجيه وصياغة الوسم (Syntax):",
            color = BrightGold,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }

    item {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111122)),
            border = BorderStroke(1.dp, GlassBorder)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "[تعليق_اللغة] @builder:file [اسم_الملف]\n[كود البرنامج كاملاً دون اختصار]\n[تعليق_اللغة] @builder:end",
                    color = BrightGold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }

    item {
        Text(
            text = "📋 أمثلة تطبيقية متدرجة المستويات:",
            color = BrightGold,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }

    item {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = "🟢 مبتدئ: إنشاء ملف نصي بسيط", color = EmeraldGlow, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            val beginnerCode = """
                // @builder:file info.txt
                أهلاً بك في تطبيق المنصة الذهبية.
                تم إنشاء هذا الملف تلقائياً وبنجاح تام!
                // @builder:end
            """.trimIndent()
            CodeBlock(beginnerCode) {
                clipboardManager.setText(AnnotatedString(beginnerCode))
                Toast.makeText(context, "تم نسخ المثال المبتدئ للحافظة!", Toast.LENGTH_SHORT).show()
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(text = "🟡 متوسط: إنشاء وتحديث أكواد لغات الويب", color = BrightGold, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            val mediumCode = """
                <!-- @builder:file index.html -->
                <!DOCTYPE html>
                <html lang="ar" dir="rtl">
                <head>
                    <meta charset="UTF-8">
                    <title>المنصة التعليمية</title>
                    <style>
                        body { background: #0F172A; color: #F8FAFC; text-align: center; padding-top: 50px; }
                        h1 { color: #F59E0B; }
                    </style>
                </head>
                <body>
                    <h1>مرحباً بك في صفحتك الخاصة</h1>
                    <p>هذه الصفحة تم بناؤها بالكامل برفق عبر هاتفك المحمول!</p>
                </body>
                </html>
                <!-- @builder:end -->
            """.trimIndent()
            CodeBlock(mediumCode) {
                clipboardManager.setText(AnnotatedString(mediumCode))
                Toast.makeText(context, "تم نسخ المثال المتوسط للحافظة!", Toast.LENGTH_SHORT).show()
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(text = "🔴 متقدم: كود بايثون متقدم مع المعالجة", color = DangerRed, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            val advancedCode = """
                # @builder:file data_processor.py
                import json

                def analyze_data():
                    data = {"status": "success", "platform": "Golden Platform", "version": 2.0}
                    print("جاري معالجة وتحليل البيانات...")
                    print(f"اسم المنصة: {data['platform']}")
                    return json.dumps(data, indent=4)

                if __name__ == "__main__":
                    result = analyze_data()
                    print(result)
                # @builder:end
            """.trimIndent()
            CodeBlock(advancedCode) {
                clipboardManager.setText(AnnotatedString(advancedCode))
                Toast.makeText(context, "تم نسخ المثال المتقدم للحافظة!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    item {
        Text(
            text = "🎬 سيناريوهات واقعية تفاعلية:",
            color = BrightGold,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }

    item {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ExpandableCard(title = "👨‍🏫 سيناريو المعلم: إنشاء صفحة درس تفاعلية") {
                Text(
                    text = "مثال: عندما تريد إنشاء درس لطلابك حول الجدول الدوري، يمكنك أن تطلب من الذكاء الاصطناعي:\n" +
                            "\"قم بإنشاء صفحة ويب HTML تفاعلية تشرح الجدول الدوري مستخدماً كتل @builder وبخلفية داكنة أنيقة وملونة.\"\n\n" +
                            "سيقوم المساعد بإرسال الكود مغلفاً بوسم @builder، وعند نسخك للمحادثة، سيقوم تطبيقنا ببناء الملف فورا لتتمكن من عرضه للطلاب عبر زر \"👁️ معاينة\" بالفقاعة!",
                    color = TextSilver,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }

            ExpandableCard(title = "💻 سيناريو المطور: مشاركة الأكواد وتجربتها") {
                Text(
                    text = "مثال: بدلاً من نقل الكود يدوياً وسطر بسطر، اطلب من المساعد توليد ملفات الأكواد مغلفة بوسوم البناء @builder.\n" +
                            "بمجرد ضغطة واحدة على زر \"نسخ\" في ChatGPT، سيكتشف تطبيقنا الملفات ويبنيها في مجلد مشروعك في أقل من ثانية، لتجد الأكواد مرتبة ومحدثة على الفور!",
                    color = TextSilver,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }

    item {
        Text(
            text = "⚠️ أخطاء شائعة جداً وكيف تتجنبها:",
            color = DangerRed,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }

    item {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardSlateBg),
            border = BorderStroke(1.dp, Color(0x30EF4444)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BulletErrorItem(
                    error = "نسيان وسم الإغلاق @builder:end",
                    fix = "تأكد دائماً أن ينتهي كود الملف بسطر إغلاق مستقل تماماً يحتوي على @builder:end لتتمكن المنصة من حفظ الملف بشكل سليم."
                )
                BulletErrorItem(
                    error = "إرسال أكواد مبتورة أو تعليقات استبدال",
                    fix = "الذكاء الاصطناعي قد يكتب أحياناً '// ... باقي الأكواد هنا ...'. اطلب منه دائماً إرسال الكود كاملاً لعدم حذف الأكواد القديمة بالخطأ."
                )
            }
        }
    }
}

// ==========================================
// 3. @EXECUTOR GUIDE TAB
// ==========================================
private fun androidx.compose.foundation.lazy.LazyListScope.showExecutorGuide(
    context: android.content.Context,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager
) {
    item {
        GuideHeaderCard(
            title = "محرك الأوامر @executor",
            description = "يسمح وسم @executor للمساعد الخارجي بإصدار أوامر إدارة ملفات مباشرة ومنظمة على هاتفك، لتنظيم المشاريع بضغطة واحدة."
        )
    }

    item {
        Text(
            text = "✍️ نحو التوجيه وصياغة الأمر (Syntax):",
            color = BrightGold,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }

    item {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111122)),
            border = BorderStroke(1.dp, GlassBorder)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "الطريقة الأولى (الأوامر السريعة المباشرة):",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "@executor:[اسم_الأمر] --param1=value1 --param2=value2",
                    color = BrightGold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "الطريقة الثانية (استخدام بنية JSON للتمرير المعقد):",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "@executor:[اسم_الأمر]\n{\n  \"param1\": \"value1\",\n  \"param2\": \"value2\"\n}",
                    color = BrightGold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }

    item {
        Text(
            text = "📋 قائمة الـ 23 أمراً المعتمدة للمنفذ الذكي:",
            color = BrightGold,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }

    // Comprehensive list of all 23 commands with descriptions
    item {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardSlateBg),
            border = BorderStroke(1.dp, GlassBorder),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CommandListItem(num = "1", name = "scan", desc = "يفحص ويحلل محتويات المجلد المحدد لتتبع أي تعديلات طرأت.")
                CommandListItem(num = "2", name = "list", desc = "يعرض قائمة تفصيلية بالملفات والمجلدات مع الحجم وتاريخ التعديل.")
                CommandListItem(num = "3", name = "filter", desc = "فرز وتصفية الملفات بناءً على الامتداد أو الحجم أو تاريخ التعديل.")
                CommandListItem(num = "4", name = "rename", desc = "يغير اسم ملف أو مجلد معين بأمان تام ودون إتلاف محتواه.")
                CommandListItem(num = "5", name = "move", desc = "ينقل ملفاً أو مجلداً من مساره الحالي إلى أي مكان في المشروع.")
                CommandListItem(num = "6", name = "copy-safe", desc = "ينسخ ملفاً لمسار جديد مع حماية الملف الأصلي وتجنب الاستبدال المفاجئ.")
                CommandListItem(num = "7", name = "delete", desc = "يحذف ملفاً أو مجلداً نهائياً من القرص.")
                CommandListItem(num = "8", name = "duplicates", desc = "يبحث عن الملفات المكررة في مشروعك ويقترح التخلص منها لتوفير مساحة.")
                CommandListItem(num = "9", name = "project", desc = "يدير شؤون المشروع الحالي، كلمات الدلالة، والمجلدات الفعالة.")
                CommandListItem(num = "10", name = "mkdir", desc = "ينشئ مجلداً (Directory) فرعياً جديداً بالمسار المطلوب.")
                CommandListItem(num = "11", name = "file", desc = "عملية سريعة ومبسطة للتعامل مع خصائص الملفات وإنشائها.")
                CommandListItem(num = "12", name = "template", desc = "يطبق قالباً تفاعلياً للمشروع بناءً على ملف تهيئة JSON معتمد.")
                CommandListItem(num = "13", name = "extract-title", desc = "يستخرج العناوين والبيانات الوصفية الأساسية من ملفات HTML والنصوص.")
                CommandListItem(num = "14", name = "read-metadata", desc = "يقرأ التفاصيل الفنية والخصائص الخفية لملفات الكود والمستندات.")
                CommandListItem(num = "15", name = "report", desc = "يولد تقريراً تجميعياً شاملاً حول أحجام وتوزيع الملفات بالمشروع.")
                CommandListItem(num = "16", name = "chart", desc = "يرسم إحصائيات بيانية لنسب استخدام الامتدادات والأحجام.")
                CommandListItem(num = "17", name = "export", desc = "يصدر الملفات والمشروع الحالي في ملف ZIP مضغوط جاهز للمشاركة.")
                CommandListItem(num = "18", name = "open", desc = "يفتح ملفاً محدداً بالمعاين الداخلي للتطبيق أو عبر تطبيق خارجي مناسب.")
                CommandListItem(num = "19", name = "clipboard", desc = "يقوم بحفظ وإخراج محتويات الحافظة مباشرة لملف مرتب.")
                CommandListItem(num = "20", name = "notify", desc = "يرسل إشعاراً للنظام لتنبيه المستخدم باكتمال العمليات الخلفية.")
                CommandListItem(num = "21", name = "preview", desc = "يفتح واجهة معاينة تفاعلية فورية لصفحات HTML أو المستندات.")
                CommandListItem(num = "22", name = "ai", desc = "يفعل الذكاء الاصطناعي الداخلي لإعادة الصياغة أو تلخيص ملفات معينة.")
                CommandListItem(num = "23", name = "selftest", desc = "يقوم بعمل فحص ذاتي سريع للمنصة والأدوات للتأكد من جاهزيتها.")
            }
        }
    }

    item {
        Text(
            text = "🎬 سيناريوهات تنظيمية واقعية للمستخدم:",
            color = BrightGold,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }

    item {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ExpandableCard(title = "🧹 سيناريو الترتيب: فرز وتنظيف الملفات المبعثرة") {
                Text(
                    text = "مثال: عندما يتراكم لديك ملفات مبعثرة، يمكنك نسخ هذا الأمر لإرشاد التطبيق لإنشاء مجلد خاص بالأنماط ونقل الملفات إليه فوراً:\n\n" +
                            "@executor:mkdir --path=css_styles\n" +
                            "@executor:move\n" +
                            "{\n" +
                            "  \"path\": \"style.css\",\n" +
                            "  \"dest\": \"css_styles/style.css\"\n" +
                            "}",
                    color = TextSilver,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 15.sp
                )
            }

            ExpandableCard(title = "📦 سيناريو الأرشفة: تصدير مشروعك كملف ZIP") {
                Text(
                    text = "مثال: عندما تكمل عملك على موقعك وتريد مشاركته كملف مضغوط، يمكنك تنفيذ هذا الأمر البسيط:\n\n" +
                            "@executor:export --format=zip\n\n" +
                            "سيقوم التطبيق بتجميع كافة الملفات والمجلدات وتصديرها كملف ZIP متاح في مدير الملفات لتشاركه مع أصدقائك أو معلمك بسهولة تامة!",
                    color = TextSilver,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

// ==========================================
// 4. @TREEDOC GUIDE TAB
// ==========================================
private fun androidx.compose.foundation.lazy.LazyListScope.showTreeDocGuide(
    context: android.content.Context,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager
) {
    item {
        GuideHeaderCard(
            title = "أداة التقارير الشجرية @treedoc",
            description = "يستخدم أمر @treedoc لإنشاء رسم تخطيطي شجري يوضح بنية وعلاقات الملفات والمجلدات في مشروعك بكفاءة عالية."
        )
    }

    item {
        Text(
            text = "✍️ صياغة التوجيه (Syntax):",
            color = BrightGold,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }

    item {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111122)),
            border = BorderStroke(1.dp, GlassBorder)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "@treedoc --format=[txt | html | json]",
                    color = BrightGold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "الخيارات المتاحة:\n" +
                            "• txt: رسم شجري نصي بسيط ومريح للعين والنسخ.\n" +
                            "• html: صفحة تقرير ويب تفاعلية مذهلة تعرض توزيع الأحجام كأشكال بيانية.\n" +
                            "• json: تمثيل هيكلي خالص يفهمه المساعد البرمجي بدقة تامة.",
                    color = TextSilver,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }

    item {
        Text(
            text = "👀 مثال على التقرير الشجري النصي الناتح:",
            color = BrightGold,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }

    item {
        val treeExample = """
            📁 المشروع الحالي: منصة_التعلم
            ├── 📁 lessons (مجلد الدروس)
            │   ├── 📄 chemistry.html (34 KB)
            │   └── 📄 physics.html (41 KB)
            ├── 📁 css (مجلد الأنماط)
            │   └── 📄 main.css (12 KB)
            └── 📄 index.html (8 KB)
        """.trimIndent()
        CodeBlock(treeExample) {
            clipboardManager.setText(AnnotatedString(treeExample))
            Toast.makeText(context, "تم نسخ رسم الشجرة التوضيحي!", Toast.LENGTH_SHORT).show()
        }
    }

    item {
        Text(
            text = "🎬 سيناريوهات عملية مفيدة جداً:",
            color = BrightGold,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }

    item {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ExpandableCard(title = "🧠 إعطاء المساعد نظرة كاملة على مشروعك") {
                Text(
                    text = "عندما تبدأ محادثة جديدة وتريد من المساعد أن يفهم أين وصلت وكيف تبدو بنية ملفاتك الحالية، انسخ أمر @treedoc --format=txt، وبمجرد تنفيذه، قم بنسخ النتيجة للمساعد الذكي ليفهم هيكلة مشروعك ويقدم لك توجيهات مثالية!",
                    color = TextSilver,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }

            ExpandableCard(title = "📊 توليد صفحة ويب تبرز توزيع المساحات") {
                Text(
                    text = "انسخ أمر @treedoc --format=html، وسيولد التطبيق تلقائياً ملف ويب تفاعلي وجذاب يحتوي على رسومات دائرية توضح أكبر الملفات استهلاكاً للمساحة في مشروعك مع أزرار سريعة لتصفحها ومعاينتها بروعة!",
                    color = TextSilver,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

// ==========================================
// 5. TEMPLATES GUIDE TAB
// ==========================================
private fun androidx.compose.foundation.lazy.LazyListScope.showTemplatesGuide(
    context: android.content.Context,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager
) {
    item {
        GuideHeaderCard(
            title = "نظام قوالب المشاريع الموحدة",
            description = "يتيح لك نظام القوالب بناء هياكل مشاريع ومجلدات متكاملة بضغطة واحدة عبر ملف إعداد بسيط بصيغة JSON."
        )
    }

    item {
        Text(
            text = "✍️ صياغة بنية القالب (JSON Structure):",
            color = BrightGold,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }

    item {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111122)),
            border = BorderStroke(1.dp, GlassBorder)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "يجب أن يحتوي كود JSON على:\n" +
                            "• projectName: الاسم الكلي لمشروعك.\n" +
                            "• template_version: إصدار التهيئة.\n" +
                            "• folders: مصفوفة تحتوي على المجلدات، مساراتها النسبية، الامتدادات المقبولة فيها، والكلمات الدالة (Keywords) لفرز وحفظ الملفات الملتقطة تلقائياً بداخلها!",
                    color = TextSilver,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }

    item {
        Text(
            text = "📋 مثال متكامل لقالب مشروع جاهز للنسخ والبناء:",
            color = BrightGold,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }

    item {
        val jsonTemplate = """
            {
              "projectName": "منصة_الرياضيات_التفاعلية",
              "template_version": "2.0",
              "folders": [
                {
                  "name": "الدروس والفيديوهات",
                  "path": "lessons",
                  "fileTypes": ["html", "txt"],
                  "keywords": ["حساب", "جبر", "هندسة"]
                },
                {
                  "name": "الاختبارات والتقييمات",
                  "path": "quizzes",
                  "fileTypes": ["json", "xml"],
                  "keywords": ["سؤال", "درجة", "اختبار"]
                },
                {
                  "name": "الوسائط والصور",
                  "path": "assets",
                  "fileTypes": ["png", "jpg", "svg"],
                  "keywords": ["رسم", "شكل", "توضيح"]
                }
              ]
            }
        """.trimIndent()
        CodeBlock(jsonTemplate) {
            clipboardManager.setText(AnnotatedString(jsonTemplate))
            Toast.makeText(context, "تم نسخ قالب التهيئة بنجاح!", Toast.LENGTH_SHORT).show()
        }
    }

    item {
        Text(
            text = "🎬 سيناريو البناء والتصنيف الذاتي:",
            color = BrightGold,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }

    item {
        ExpandableCard(title = "🏫 سيناريو إنشاء بيئة مقرر دراسي أو تدريبي") {
            Text(
                text = "بمجرد نسخ قالب الـ JSON أعلاه، سيقوم التطبيق بتهيئة 3 مجلدات (lessons, quizzes, assets). وفي المستقبل، أي نص تنسخه من المساعد الذكي يحتوي على كلمة \"جبر\" أو \"هندسة\"، سيقوم محرك الالتقاط الذكي بحفظه تلقائياً كملف داخل مجلد \"lessons\" دون أي تدخل منك! هذا هو قمة التنظيم الذاتي للملفات.",
                color = TextSilver,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
    }
}

// ==========================================
// 6. SYSTEM PROMPTS GUIDE TAB (نصائح للمساعد)
// ==========================================
private fun androidx.compose.foundation.lazy.LazyListScope.showSystemPromptsGuide(
    context: android.content.Context,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager
) {
    item {
        GuideHeaderCard(
            title = "📜 التلقين البرمجي للمساعدين (System Prompts)",
            description = "انسخ النص التلقيني المعتمد ومرره للمساعد في بداية المحادثة، ليعرف كيف يتفاعل مع هاتفك ويولد لك ملفات وأوامر جاهزة للتنفيذ التلقائي."
        )
    }

    item {
        Text(
            text = "🌍 النص التلقيني العام (لأي مساعد ذكي):",
            color = BrightGold,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }

    val generalPrompt = """
        أنت مساعد برمجي وتدريسي ذكي تخدم مستخدم تطبيق "المنصة الذهبية".
        المستخدم يعمل على بيئة هاتف ذكي، والتطبيق يراقب الحافظة والملفات.
        مهمتك هي صياغة الملفات البرمجية والمستندات بأسلوب منظم باستخدام وسوم البناء والتحكم المحددة للتطبيق:
        
        1. لإنشاء أو تعديل أي ملف، يجب تغليفه بالكامل هكذا:
        // @builder:file [المسار النسبي للملف]
        [محتوى الملف الكامل دون اختصارات]
        // @builder:end
        
        2. لإصدار أوامر تنظيم ملفات، اكتب في سطر منفصل مستقل:
        @executor:[الأمر] --[المعامل]=[القيمة]
        أو كبنية JSON مثل:
        @executor:move
        {"path": "source.txt", "dest": "dest.txt"}
        
        الأوامر المدعومة تشمل: scan, list, filter, rename, move, copy-safe, delete, duplicates, project, mkdir, file, template, extract-title, read-metadata, report, chart, export, open, clipboard, notify, preview, ai, selftest.
        
        3. لطلب بنية وتخطيط المجلدات، استخدم:
        @treedoc --format=[txt أو html أو json]
        
        اكتب مخرجاتك بوضوح وتجنب الاختصارات لتسهيل المعالجة البرمجية التلقائية.
    """.trimIndent()

    item {
        CodeBlock(generalPrompt) {
            clipboardManager.setText(AnnotatedString(generalPrompt))
            Toast.makeText(context, "تم نسخ التلقين العام بنجاح!", Toast.LENGTH_SHORT).show()
        }
    }

    item {
        Text(
            text = "🎯 نصوص تلقينية مخصصة ومحسنة حسب المساعد الذكي:",
            color = BrightGold,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }

    item {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ExpandableCard(title = "💬 نص تلقين مخصص لـ ChatGPT (محسن ومختصر)") {
                val chatGptPrompt = "أنت مساعد ChatGPT المتوافق مع المنصة الذهبية. يرجى توليد ردودك البرمجية والتعليمية مباشرة داخل كتل @builder:file و @builder:end، وتجنب المقدمات والشروحات النظرية الطويلة خارج الأكواد لتسهيل عملية النسخ والمعالجة الفورية بكفاءة وسرعة."
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = chatGptPrompt, color = TextSilver, fontSize = 11.sp, lineHeight = 16.sp)
                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(chatGptPrompt))
                            Toast.makeText(context, "تم نسخ تلقين ChatGPT!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = SlateBg),
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("📋 نسخ التلقين المخصص لـ ChatGPT", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            ExpandableCard(title = "🐳 نص تلقين مخصص لـ DeepSeek (قوة الاستدلال الهيكلي)") {
                val deepSeekPrompt = "أنت مساعد DeepSeek الخبير في التفكير المنطقي والهيكلة للمنصة الذهبية. وظيفتك تحليل وتصميم المشاريع، واستخدام وسوم البناء @builder لإنشاء الملفات، والاعتماد بكثافة على أوامر @executor لتنظيم وتطهير وترتيب المجلدات وحذف التكرار، وتوفير التوضيحات الشجرية بواسطة @treedoc."
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = deepSeekPrompt, color = TextSilver, fontSize = 11.sp, lineHeight = 16.sp)
                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(deepSeekPrompt))
                            Toast.makeText(context, "تم نسخ تلقين DeepSeek!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = SlateBg),
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("📋 نسخ التلقين المخصص لـ DeepSeek", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            ExpandableCard(title = "✨ نص تلقين مخصص لـ Gemini (قدرة فهم سياق الدروس الطويل)") {
                val geminiPrompt = "أنت مساعد Gemini للمنصة الذهبية. نظراً لقدرتك الهائلة في استيعاب سياق الدروس الطويلة والترجمة، قم بصياغة دروس ومقالات تعليمية متكاملة وغنية بالوسائط التوضيحية داخل كتل @builder:file بصيغة HTML لتظهر للطلاب كصفحة ويب منسقة وساحرة بالكامل."
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = geminiPrompt, color = TextSilver, fontSize = 11.sp, lineHeight = 16.sp)
                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(geminiPrompt))
                            Toast.makeText(context, "تم نسخ تلقين Gemini!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = SlateBg),
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("📋 نسخ التلقين المخصص لـ Gemini", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ==========================================
// REUSABLE HELPER UI COMPONENTS
// ==========================================

@Composable
fun GuideHeaderCard(title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardSlateBg),
        border = BorderStroke(1.dp, GlassBorder),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = BrightGold,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = title,
                    color = BrightGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    color = TextSilver,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
fun StepItem(number: String, title: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(MetallicGold, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                color = SlateBg,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = BrightGold,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                color = TextSilver,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
fun ExpandableCard(
    title: String,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardSlateBg),
        border = BorderStroke(1.dp, GlassBorder),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = BrightGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (expanded) "🔼" else "🔽",
                    fontSize = 12.sp
                )
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                content()
            }
        }
    }
}

@Composable
fun CodeBlock(
    code: String,
    onCopy: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.8.dp, GlassBorder, RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111122)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "مثال تطبيقي للنسخ والاستخدام:",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = onCopy,
                    colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = SlateBg),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(24.dp)
                ) {
                    Text("📋 نسخ", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = code,
                color = TextSilver,
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 14.5.sp
            )
        }
    }
}

@Composable
fun BulletErrorItem(error: String, fix: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text("❌", fontSize = 12.sp)
        Column {
            Text(
                text = error,
                color = DangerRed,
                fontWeight = FontWeight.Bold,
                fontSize = 11.5.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "💡 الحل: $fix",
                color = TextSilver,
                fontSize = 10.5.sp,
                lineHeight = 14.5.sp
            )
        }
    }
}

@Composable
fun CommandListItem(num: String, name: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(Color(0xFF1E293B), RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = num,
                color = BrightGold,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = BrightGold,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.5.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                color = TextSilver,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}
