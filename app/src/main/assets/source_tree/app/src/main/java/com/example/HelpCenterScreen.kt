package com.example

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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

data class GlossaryItem(val term: String, val definition: String, val example: String)
data class FaqItem(val question: String, val answer: String)
data class ScenarioItem(val title: String, val icon: String, val steps: List<String>, val result: String)

@Composable
fun HelpCenterScreen(onNavigateBack: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabTitles = listOf("📖 القاموس", "❓ الأسئلة الشائعة", "💼 سيناريوهات", "🤖 محاكي الأتمتة")

    val glossaryItems = remember {
        listOf(
            GlossaryItem(
                term = "@builder",
                definition = "بادئة ذكية وموجه أساسي لإنشاء وتشكيل الملفات البرمجية مباشرة من النصوص داخل التطبيق تلقائياً.",
                example = "@builder:file src/main.py\n# اكتب كود Python هنا\n@builder:end"
            ),
            GlossaryItem(
                term = "@executor",
                definition = "بادئة لتشغيل وتنفيذ الأوامر والعمليات البرمجية بشكل مباشر وتراجعي داخل بيئة وهيكل المشروع.",
                example = "@executor:move --path=/Books --new-path=/Library"
            ),
            GlossaryItem(
                term = "@treedoc",
                definition = "بادئة متخصصة لبناء خرائط شجرية تفاعلية بصرية لكافة مجلدات العمل لضمان سهولة الفهم والتوثيق.",
                example = "@treedoc:generate --path=/MyProject"
            ),
            GlossaryItem(
                term = "SmartInbox",
                definition = "المستودع الذكي الافتراضي في التطبيق لتخزين النصوص المؤرشفة الملتقطة تلقائياً، والمنسقة بقوالب HTML فاخرة لتسهيل القراءة وتصفح المعلومات.",
                example = "يتم حفظ جميع النصوص المنسوخة المصنفة كنصوص تعليمية أو مقالات داخل دليل SmartInbox بتصميم السمة المحددة."
            )
        )
    }

    val faqItems = remember {
        listOf(
            FaqItem(
                question = "لماذا يتم تجاهل النصوص القصيرة المنسوخة أحياناً؟",
                answer = "يدرج التطبيق خاصية 'تجاهل النصوص القصيرة' لتجنب تكرار وحفظ القصاصات والكلمات العشوائية المنسوخة مؤقتاً بالخلفية. يمكنك إلغاء تفعيل هذا الخيار من الإعدادات للبدء بنسخ وحفظ كافة البيانات مهما كان حجمها."
            ),
            FaqItem(
                question = "كيف يمكنني مشاركة جذاذات مشروعي البرمجي مع مطور أو صديق؟",
                answer = "يمكنك النقر على زر 'تصدير حزمة بناء ذكية' من شاشات المشروع أو استعمال مخرج الحزمة المتعدد لنسخ كتل الأكواد مصنفة ومهيأة للنسخ كملف واحد متكامل."
            ),
            FaqItem(
                question = "ما فائدة الفقاعة الذهبية العائلة V2 العائمة؟",
                answer = "الفقاعة الذهبية توفر لك سهولة مطلقة ووصول سريع لكافة خدمات الأتمتة المباشرة، وحصد سجل الأحداث، وتجربة الأوامر يدوياً بضغطة زر واحدة من أي مكان على شاشة هاتفك خارج التطبيق."
            )
        )
    }

    val scenarioItems = remember {
        listOf(
            ScenarioItem(
                title = "أتمتة تصدير كتل الأكواد ومشروعات البناء المتكاملة",
                icon = "⚙️",
                steps = listOf(
                    "انسخ موجه البناء البرمجي الذي يحتوي على كتل تفعيل @builder:file المتنوعة.",
                    "سيقوم التطبيق والخدمة بالتقاط الملفات من الحافظة بالخلفية تلقائياً.",
                    "سيتم فرز وترجمة الأكواد وبناؤها فوراً داخل مجلد هيكل المشروع المنسق."
                ),
                result = "بناء فوري وهيكلي نظيف ومليء لكافة ملفات الكود بنسخة واحدة دون أي تداخل يذكر!"
            ),
            ScenarioItem(
                title = "تشغيل وفحص برمجيات الأكواد بصلاحيات المنفذ",
                icon = "💻",
                steps = listOf(
                    "قم بكتابة تعليمة التشغيل المباشر مثل: @executor:run python main.py.",
                    "سيتحقق محرك الأوامر من جاهزية البيئة الأمنية ومجلد سياق المشروع للتنفيذ.",
                    "ستظهر جميع مخرجات المنفذ وسجلات التشغيل مباشرة على شاشتك بدلاً من الواجهة التقليدية."
                ),
                result = "تقرير تشغيل سليم وحصر كامل لكافة البيانات والأخطاء البرمجية بكفاءة تامة."
            ),
            ScenarioItem(
                title = "حفظ الملاحظات التعليمية والمقالات وتنسيقها تلقائياً بالخلفية",
                icon = "📖",
                steps = listOf(
                    "تأكد من تفعيل خيار 'المعالجة التلقائية للحافظة' من شاشة الإعدادات العامة.",
                    "قم بنسخ أي مقال أو نص تعليمي طويل ومهم من متصفحك أو من رسالة واردة.",
                    "سيقوم محرك الالتقاط الذكي بالخلفية بتصنيف النص وحفظه بنسق HTML فاخر في SmartInbox."
                ),
                result = "أكوام من المقالات والفوائد المنسقة والجاهزة للقراءة في أي وقت بتصميم ثيمك المفضل!"
            ),
            ScenarioItem(
                title = "توليد الخرائط الشجرية لتنظيم ملفات مجلد عملك",
                icon = "📊",
                steps = listOf(
                    "قم بتعيين مسار المجلد الحالي الفعال للمشروع من بوابة المشاريع.",
                    "انسخ موجه الخريطة الشجرية التالي بدقة: @treedoc:generate --path=.",
                    "سيقوم محرك التوثيق ببدء قراءة هيكلية المجلد والملفات مباشرة دون توقف."
                ),
                result = "تمثيل شجري غني وتفاعلي بهيئة Markdown يسهل تخزينه ونشره ومشاركته فوراً."
            ),
            ScenarioItem(
                title = "سلامة واستمرار خدمة المراقبة تحت نظام توفير الطاقة",
                icon = "🛡️",
                steps = listOf(
                    "توجه إلى بطاقة 'الصلاحيات الشاملة ومكافحة الإغلاق' في ذيل الإدارات.",
                    "قم بطلب ومنح صلاحيات 'استثناء تحسين البطارية' من نظام تشغيل أندرويد.",
                    "سيحافظ ذلك على ديمومة خدمة المراقبة والأرشفة دون إغلاق تلقائي بسبب حماية الطاقة."
                ),
                result = "أتمتة متيقظة وطبيعية وخالية بالكامل من أي انهيار برمي أو توقف قسري في الخلفية!"
            )
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = SlateBg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .background(GlassWhite, RoundedCornerShape(10.dp))
                        .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                        .testTag("help_center_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "الرجوع",
                        tint = MetallicGold
                    )
                }

                Text(
                    text = "ℹ️ مركز المساعدة والتوثيق",
                    color = BrightGold,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Tab Selector Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GlassWhite, RoundedCornerShape(12.dp))
                    .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                tabTitles.forEachIndexed { index, title ->
                    val isSelected = selectedTab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .background(
                                if (isSelected) MetallicGold.copy(alpha = 0.25f) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedTab = index }
                            .testTag("help_tab_$index"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            color = if (isSelected) BrightGold else TextSilver,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            // Tab contents
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> {
                        // Glossary Pane
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(glossaryItems) { item ->
                                GlossaryCardComponent(item)
                            }
                        }
                    }
                    1 -> {
                        // FAQ Pane
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(faqItems) { item ->
                                FaqCardComponent(item)
                            }
                        }
                    }
                    2 -> {
                        // Scenario Pane
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(scenarioItems) { item ->
                                ScenarioCardComponent(item)
                            }
                        }
                    }
                    3 -> {
                        // Interactive Simulator Pane
                        InteractiveSimulatorPane()
                    }
                }
            }
        }
    }
}

@Composable
fun GlossaryCardComponent(item: GlossaryItem) {
    var isExpanded by remember { mutableStateOf(false) }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("glossary_card_${item.term}")
    ) {
        Column(
            modifier = Modifier
                .clickable { isExpanded = !isExpanded }
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = item.term,
                    color = BrightGold,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = TextGray,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = item.definition,
                color = TextSilver,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Divider(color = GlassBorder.copy(alpha = 0.15f), thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "مثال الاستعمال البرمجي:",
                        color = MetallicGold,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(GlassWhite, RoundedCornerShape(8.dp))
                            .border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = item.example,
                            color = TextSilver,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            textAlign = TextAlign.Left,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FaqCardComponent(item: FaqItem) {
    var isExpanded by remember { mutableStateOf(false) }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("faq_card_${item.question.take(15)}")
    ) {
        Column(
            modifier = Modifier
                .clickable { isExpanded = !isExpanded }
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = item.question,
                    color = MetallicGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = TextGray,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Divider(color = GlassBorder.copy(alpha = 0.15f), thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = item.answer,
                        color = TextSilver,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun ScenarioCardComponent(item: ScenarioItem) {
    var isExpanded by remember { mutableStateOf(false) }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("scenario_card_${item.title.take(15)}")
    ) {
        Column(
            modifier = Modifier
                .clickable { isExpanded = !isExpanded }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(item.icon, fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = item.title,
                        color = BrightGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.weight(1f)
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = TextGray,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Divider(color = GlassBorder.copy(alpha = 0.15f), thickness = 0.5.dp)

                    Text(
                        text = "خطوات السيناريو والتطبيق العملي:",
                        color = MetallicGold,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    item.steps.forEachIndexed { sIdx, step ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "${sIdx + 1}.",
                                color = BrightGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = step,
                                color = TextSilver,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                textAlign = TextAlign.Right,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(EmeraldGlow.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            .border(0.5.dp, EmeraldGlow.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "🏆 النتيجة المتوقعة للفحص الذاتي:",
                                color = EmeraldGlow,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = item.result,
                                color = TextSilver,
                                fontSize = 9.5.sp,
                                lineHeight = 13.sp,
                                textAlign = TextAlign.Right,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InteractiveSimulatorPane() {
    var curScenario by remember { mutableStateOf(0) }
    var curStep by remember { mutableStateOf(0) }

    val scenarioNames = listOf("📦 معالج الأكواد", "⚙️ منفذ الأوامر", "📥 أرشفة الحافظة")
    val scenarioIcons = listOf("📦", "⚙️", "📥")
    val stepNames = listOf("📋 التقاط", "🧠 تحليل", "⚡ معالجة", "📁 تخزين", "🏆 اكتمال")

    val scenariosLog = listOf(
        // Builder Scenario
        listOf(
            "📋 تم التقاط نص من الحافظة بالخلفية:\n@builder:file src/index.js\nconsole.log('مرحبًا بك في المنصة الذهبية!');\n@builder:end\n\n[MONITOR] Intercepted clipboard text (78 bytes).",
            "🧠 محاكاة مرحلة تحليل النمط:\n[ANALYSIS] Detecting automation pattern: @builder\n[ANALYSIS] Successfully parsed output target: src/index.js\n[ANALYSIS] Mode inferred: Code Execution Bundle.",
            "⚡ محاكاة مرحلة المعالجة والتحويل:\n[BUILDER] Checking parent directory folder: src/\n[BUILDER] Building localized code block structure...\n[BUILDER] Selected theme: Golden Elite visual styling wrapper.",
            "📁 محاكاة مرحلة التوثيق والحفظ بالذاكرة:\n[STORAGE] Saving file to active project directory structure: /Projects/Default/src/index.js\n[STORAGE] Successfully written 72 bytes code payload.",
            "🏆 محاكاة النهاية وإخطار المستخدم:\n[NOTIFICATION] Golden Bubble animation executed successfully.\n[STATE] System updated with certificate summary report."
        ),
        // Executor Scenario
        listOf(
            "📋 تم التقاط أمر تشغيل في الخلفية:\n@executor:run node src/index.js\n\n[MONITOR] Captured terminal command instruction.",
            "🧠 محاكاة مرحلة الكشف والتحقيق الأمني:\n[ANALYSIS] Detecting command action: RUN\n[ANALYSIS] Verifying active context location: /Projects/Default/\n[ANALYSIS] Verification complete (Security clear).",
            "⚡ محاكاة تشغيل وإدارة المعالجات:\n[EXECUTOR] Initiating subprocess context for node runtime...\n[EXECUTOR] Running script asynchronously in native sandbox...",
            "📁 محاكاة حصر مخرجات وسجلات التنفيذ:\n[LOGGER] Writing command output log:\n>>> مرحبًا بك في المنصة الذهبية!\n[STORAGE] Log file written to /Projects/Default/logs/run_node_index.log",
            "🏆 محاكاة النجاح وإصدار التقرير النهائي:\n[NOTIFICATION] Command ran cleanly with Exit Code: 0\n[STATE] Active dashboard displays execution log instantly."
        ),
        // Smart Capture Scenario
        listOf(
            "📋 تم التقاط نص مقالي عام بالخلفية:\n# لغات البرمجية الرائجة\n1. Kotlin لهواتف أندرويد الحديثة.\n2. Python لتعلم الأتمتة المتقدمة.\n\n[MONITOR] Plain markdown copied to clipboard.",
            "🧠 محاكاة الأتمتة الذكية للالتقاط:\n[SMART_CAPTURE] No code commands detected.\n[SMART_CAPTURE] Classifying text block: Markdown Document.\n[SMART_CAPTURE] Auto-titling: لغات البرمجية الرائجة",
            "⚡ محاكاة تحويل النص إلى HTML فاخر:\n[CONVERTER] Parsing MD tags into clean semantic HTML sections.\n[CONVERTER] Injecting dynamically selected Theme styles: Active Light/Dark Custom scheme.",
            "📁 محاكاة الحفظ في صندوق الأرشفة الذكي:\n[STORAGE] Packaging beautifully formatted HTML file.\n[STORAGE] Saved file path: /SmartInbox/لغات_البرمجية_الرائجة.html",
            "🏆 محاكاة التقرير وتنبيه واجهة الفقاعة:\n[NOTIFICATION] In-app overlay updated.\n[STATE] Document is formatted and available in the Reader Inbox."
        )
    )

    val stepDetails = listOf(
        // Builder
        listOf(
            "تم استشعار نسخ موجه برمجية كامل يحتوي على بادئة @builder:file معينة وموجهة لملف JavaScript. بمجرد النسخ، يستقبل مراقب الخدمة النص فوريًا دون الحاجة لأي نقرة.",
            "يقوم المحرك المركزي بـ 'تحليل الكتل اللغوية البرمجية' وتحديد المسار النسبي وعزل الأكواد الفعلية عن نص الموجه التكميلي لضمان سلامة الهيكلة للمشروع.",
            "يتم استدعاء معالج كتل البناء الذي يعمل على تهيئة المسار الأبوي (توليد مجلد src إذا لم يكن موجودًا) وبدء ضخ وتشكيل المحتويات.",
            "كتابة وتوثيق كود الملف داخل ذاكرة الهاتف بمسار العمل المعتمد للمشروع لتستطيع تصفحه وتحريره أو تصديره لاحقاً بنقرة زر واحدة.",
            "تتوهج النوافذ وتتحول الفقاعة للون الذهبي اللامع معلنة إتمام بناء الأكواد، وسيصلك إشعار بنجاح العملية لضمان الثقة التامة وسلاسة الأتمتة."
        ),
        // Executor
        listOf(
            "التقاط تعليمة @executor التي كتبها المستخدم في محرر خارجي أو رسالة سريعة. يكتشف الحافظة وجود كلمة مفتاحية تشغيلية.",
            "يقوم نظام التحقق الفوري بتحليل صلاحيات الأمر المكتوب للتأكد من ملاءمته وتطابقه لمعايير السلامة وصدر مسار التشغيل الفعال.",
            "إطلاق واجهة تشغيل الأوامر بالخلفية والبدء في استنشاق مخرجات Terminal مباشرة وتسجيلها سطرًا بسطر.",
            "أرشفة وتدوين سجلات التنفيذ ومخرجات الشاشة (Console Output) كاملة في مجلد السجلات لتسهيل تتبعها وتصحيحها.",
            "اكتمال تشغيل الأمر وتنبيه واجهة المستخدم بظهور ومضة تأكيدية تفيد بانتهاء التنفيذ بدون أخطاء برمجية."
        ),
        // Smart Capture
        listOf(
            "نسخ مقال تعليمي أو موضوع من أي موقع. محرك الالتقاط الفوري مستيقظ دائمًا لرعاية النصوص الهامة وحفظها فورياً للرجوع إليها.",
            "فحص تركيبة النص، والتعرف عليه كنص Markdown منسق يحتاج لتجهيز، مع استخلاص اسم ذكي معبّر للملف تلقائيًا.",
            "تحويل النص البرمي أو التنسيقي إلى واجهة HTML فاخرة بالاعتماد على قالب السمة النشطة الفعالة حاليًا (كالهوية الذهبية أو النيون).",
            "تحزيم الملف وحفظه بتنسيق .html في مجلد SmartInbox كأرشيف دائم ومحمي من الزوال.",
            "اكتمال الأرشفة وتحديث عدد المقالات المحفوظة في صندوق الذكاء الاصطناعي، وسيتحول التقرير للون الأخضر الباعث للسلامة."
        )
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("interactive_simulator_pane"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Explanatory Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BrightGold.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .border(0.5.dp, BrightGold.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "🧠 طبقة المحاكاة والتعليم التفاعلي",
                        color = BrightGold,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "شاهد بالخطوات الفندقية والتفصيلية خطوة بخطوة كيف يتفاعل محرك المنصة وخدمة الاستماع بالخلفية مع توجيهات الأتمتة والنصوص التي تقوم بنسخها في أي مكان بهاتفك.",
                        color = TextSilver,
                        fontSize = 10.5.sp,
                        lineHeight = 15.sp,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Scenario Selector Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                scenarioNames.forEachIndexed { sIdx, name ->
                    val isSelected = curScenario == sIdx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .background(
                                if (isSelected) MetallicGold.copy(alpha = 0.22f) else GlassWhite,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else 0.8.dp,
                                color = if (isSelected) BrightGold else GlassBorder,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                curScenario = sIdx
                                curStep = 0
                            }
                            .testTag("sim_scenario_$sIdx"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = name,
                            color = if (isSelected) BrightGold else TextSilver,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Dynamic Progress Timeline Row
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    stepNames.forEachIndexed { index, name ->
                        val isCompleted = index <= curStep
                        val isActive = index == curStep
                        val color = if (isActive) BrightGold else if (isCompleted) MetallicGold else TextGray

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clickable { curStep = index }
                                .testTag("sim_step_trigger_$index")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(
                                        if (isActive) BrightGold.copy(alpha = 0.15f) else Color.Transparent,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .border(
                                        width = if (isActive) 1.5.dp else 1.dp,
                                        color = color,
                                        shape = RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    color = color,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = name,
                                color = color,
                                fontSize = 8.5.sp,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        // Step Core explanation card
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Scenario Name & Step Number
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "خطوة ${curStep + 1} من 5",
                            color = TextGray,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stepNames[curStep],
                                color = BrightGold,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = scenarioIcons[curScenario],
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Explanation Title
                    Text(
                        text = "💡 ماذا يجري الآن في الخلفية؟",
                        color = MetallicGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Explanation detail
                    Text(
                        text = stepDetails[curScenario][curStep],
                        color = TextSilver,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(2.dp))
                    Divider(color = GlassBorder.copy(alpha = 0.15f), thickness = 0.5.dp)

                    // Live Simulated Terminal Logs Console
                    Text(
                        text = "📟 سجل أحداث المعالجة الفوري المحاكي (Live Logs):",
                        color = MetallicGold,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(GlassWhite, RoundedCornerShape(8.dp))
                            .border(0.5.dp, GlassBorder, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = scenariosLog[curScenario][curStep],
                            color = Color(0xFF38BDF8),
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            textAlign = TextAlign.Left,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Theme responsive styling info
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(EmeraldGlow.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                            .border(0.5.dp, EmeraldGlow.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = EmeraldGlow,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "توضيح: يتأثر مظهر ومخرجات المعالجة والألوان مباشرة بالسمة والهوية البصرية المختارة للوثائق والمستندات في الإعدادات لتناسب قالب قراءتك.",
                                color = TextSilver,
                                fontSize = 8.5.sp,
                                lineHeight = 12.sp,
                                textAlign = TextAlign.Right,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Navigation Controller Bottom buttons
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Prev button
                Button(
                    onClick = { if (curStep > 0) curStep-- },
                    enabled = curStep > 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldGlassBg,
                        contentColor = BrightGold,
                        disabledContainerColor = GlassWhite.copy(alpha = 0.04f),
                        disabledContentColor = TextGray
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("sim_prev_button")
                ) {
                    Text("⬅️ الخطوة السابقة", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                }

                // Next or Reset button
                if (curStep < 4) {
                    Button(
                        onClick = { curStep++ },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MetallicGold,
                            contentColor = SlateBg
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("sim_next_button")
                    ) {
                        Text("الخطوة التالية ➡️", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = { curStep = 0 },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldGlow,
                            contentColor = SlateBg
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("sim_reset_button")
                    ) {
                        Text("🔄 إعادة تشغيل المحاكاة", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
