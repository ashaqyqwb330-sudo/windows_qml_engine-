package com.example.util

/**
 * قالب HTML التفاعلي المتقدم باللون الذهبي البراق لكشوفات الشجرة (TreeDoc).
 * يحتوي على المميزات الأساسية: التصفية اللحظية، لوحة التفاصيل الديناميكية، ورسم بياني تفاعلي دون مكاتب خارجية ليعمل دون إنترنت.
 */
object TreeDocHtmlTemplate {
    fun build(targetPath: String, jsonFilesData: String, renderedTreeHtml: String): String {
        return """
<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>TreeDoc - لوحة معلومات كشف شجرة المجلد</title>
    <style>
        @import url('https://fonts.googleapis.com/css2?family=Cairo:wght@300;400;600;700&display=swap');
        :root {
            --bg-color: #0b0f19;
            --card-bg: rgba(22, 30, 48, 0.7);
            --gold-primary: #D4AF37;
            --gold-bright: #FFD700;
            --text-primary: #e2e8f0;
            --text-secondary: #a0aec0;
            --border-color: rgba(212, 175, 55, 0.25);
            --border-glow: rgba(212, 175, 55, 0.15);
        }
        body {
            background-color: var(--bg-color);
            color: var(--text-primary);
            font-family: 'Cairo', sans-serif;
            margin: 0;
            padding: 0;
            direction: rtl;
            overflow-x: hidden;
        }
        .container {
            max-width: 1200px;
            margin: 0 auto;
            padding: 24px;
        }
        .header {
            background: linear-gradient(135deg, #161e30 0%, #0d1323 100%);
            border: 1px solid var(--border-color);
            border-radius: 18px;
            padding: 24px;
            margin-bottom: 24px;
            box-shadow: 0 10px 30px rgba(0, 0, 0, 0.5), inset 0 1px 0 rgba(255,255,255,0.05);
            display: flex;
            justify-content: space-between;
            align-items: center;
            flex-wrap: wrap;
            gap: 20px;
        }
        .header-title h1 {
            margin: 0;
            font-size: 26px;
            color: var(--gold-bright);
            text-shadow: 0 0 15px rgba(255, 215, 0, 0.45);
            display: flex;
            align-items: center;
            gap: 10px;
        }
        .header-title p {
            margin: 6px 0 0 0;
            font-size: 13px;
            color: var(--text-secondary);
        }
        .stats-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(110px, 1fr));
            gap: 12px;
        }
        .stats-card {
            background: rgba(17, 24, 39, 0.6);
            border: 1px solid rgba(255, 255, 255, 0.05);
            border-radius: 12px;
            padding: 12px;
            text-align: center;
            min-width: 100px;
            transition: transform 0.2s;
        }
        .stats-card:hover {
            transform: translateY(-2px);
            border-color: var(--gold-primary);
        }
        .stats-card .value {
            font-size: 20px;
            font-weight: 700;
            color: var(--gold-bright);
        }
        .stats-card .label {
            font-size: 11px;
            color: var(--text-secondary);
            margin-top: 4px;
        }
        .main-grid {
            display: grid;
            grid-template-columns: 1.2fr 0.8fr;
            gap: 24px;
        }
        @media (max-width: 900px) {
            .main-grid {
                grid-template-columns: 1fr;
            }
        }
        .card {
            background: var(--card-bg);
            border: 1px solid var(--border-color);
            border-radius: 18px;
            padding: 24px;
            box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4);
            backdrop-filter: blur(12px);
            position: relative;
        }
        .card::before {
            content: '';
            position: absolute;
            top: 0;
            left: 0;
            right: 0;
            height: 4px;
            background: linear-gradient(90deg, var(--gold-primary), var(--gold-bright), var(--gold-primary));
            border-radius: 18px 18px 0 0;
        }
        .card-title {
            font-size: 18px;
            font-weight: 700;
            color: var(--gold-bright);
            margin-top: 0;
            margin-bottom: 20px;
            display: flex;
            align-items: center;
            gap: 10px;
            border-bottom: 1px solid rgba(212, 175, 55, 0.15);
            padding-bottom: 10px;
        }
        .search-box {
            position: relative;
            margin-bottom: 20px;
        }
        .search-box input {
            width: 100%;
            padding: 12px 16px;
            background: #090d16;
            border: 1.5px solid rgba(212, 175, 55, 0.2);
            border-radius: 12px;
            color: #fff;
            font-size: 14px;
            font-family: inherit;
            box-sizing: border-box;
            transition: all 0.3s;
        }
        .search-box input:focus {
            border-color: var(--gold-bright);
            outline: none;
            box-shadow: 0 0 12px rgba(255, 215, 0, 0.25);
        }
        
        .tree-root {
            list-style: none;
            padding-right: 0;
            margin: 0;
            max-height: 600px;
            overflow-y: auto;
            scrollbar-width: thin;
            scrollbar-color: var(--gold-primary) #111;
        }
        details {
            margin-right: 14px;
            margin-top: 6px;
            transition: all 0.2s;
        }
        summary {
            display: flex;
            align-items: center;
            gap: 10px;
            padding: 8px 12px;
            background: rgba(255, 255, 255, 0.02);
            border: 1px solid rgba(255, 255, 255, 0.03);
            border-radius: 8px;
            cursor: pointer;
            font-weight: 600;
            color: var(--gold-bright);
            user-select: none;
            outline: none;
            transition: all 0.2s;
        }
        summary:hover {
            background: rgba(212, 175, 55, 0.08);
            border-color: rgba(212, 175, 55, 0.3);
        }
        summary::-webkit-details-marker {
            display: none;
        }
        summary::before {
            content: '▼';
            font-size: 10px;
            color: var(--gold-primary);
            transition: transform 0.2s;
        }
        details[open] > summary::before {
            transform: rotate(-90deg);
        }
        
        .file-item {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 10px;
            padding: 8px 12px;
            margin-right: 24px;
            margin-top: 5px;
            border-radius: 8px;
            cursor: pointer;
            color: #cbd5e0;
            border: 1px solid transparent;
            transition: all 0.2s;
        }
        .file-item:hover {
            background: rgba(255, 255, 255, 0.05);
            color: #fff;
            border-color: rgba(255, 255, 255, 0.05);
        }
        .file-item.active {
            background: rgba(212, 175, 55, 0.12);
            color: var(--gold-bright);
            border-color: rgba(212, 175, 55, 0.3);
            font-weight: 600;
        }
        .file-size-badge {
            font-size: 10px;
            color: var(--text-secondary);
            background: rgba(0,0,0,0.2);
            padding: 2px 6px;
            border-radius: 4px;
        }

        .info-panel {
            display: flex;
            flex-direction: column;
            gap: 16px;
        }
        .info-row {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 10px 0;
            border-bottom: 1px solid rgba(255, 255, 255, 0.05);
            font-size: 13px;
        }
        .info-row .label {
            color: var(--text-secondary);
        }
        .info-row .value {
            color: #fff;
            font-weight: 600;
            text-align: left;
            word-break: break-all;
        }
        
        .chart-box {
            text-align: center;
            background: rgba(9, 13, 22, 0.4);
            border-radius: 12px;
            border: 1px solid rgba(255,255,255,0.03);
            padding: 16px;
            margin-top: 15px;
        }
        .chart-legend {
            display: flex;
            flex-wrap: wrap;
            justify-content: center;
            gap: 12px;
            margin-top: 15px;
            font-size: 11px;
        }
        .legend-item {
            display: flex;
            align-items: center;
            gap: 6px;
        }
        .legend-color {
            width: 12px;
            height: 12px;
            border-radius: 3px;
        }
        
        .empty-placeholder {
            text-align: center;
            color: var(--text-secondary);
            padding: 40px 20px;
            font-size: 13px;
        }
        .empty-placeholder span {
            font-size: 32px;
            display: block;
            margin-bottom: 12px;
        }

        .hidden {
            display: none !important;
        }

        @media print {
            body {
                background: #fff !important;
                color: #000 !important;
            }
            .header, .card {
                border: 1px solid #ccc !important;
                box-shadow: none !important;
                background: #fff !important;
                page-break-inside: avoid;
            }
            .search-box, .file-item:not(.active), .tree-root summary::before {
                display: none !important;
            }
            .main-grid {
                grid-template-columns: 1fr !important;
            }
            summary {
                background: none !important;
                color: #000 !important;
                border: none !important;
            }
            .file-item {
                border: none !important;
                color: #333 !important;
                background: none !important;
            }
            .file-item.active {
                font-weight: bold;
                color: #000 !important;
            }
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <div class="header-title">
                <h1>📂 لـوحة معلومات كشف شجرة المجلد (TreeDoc)</h1>
                <p>تخطيط مسح شامل ومسار فاحص مرئي للمجلد النشط بالمنصة المتقدمة</p>
                <p style="margin-top: 6px; font-family: monospace; color:#ccc;">المجلد الحاضر: $targetPath</p>
            </div>
            
            <div class="stats-grid" id="statsGrid">
                <div class="stats-card">
                    <div id="valTotalFiles" class="value">0</div>
                    <div class="label">إجمالي الملفات</div>
                </div>
                <div class="stats-card">
                    <div id="valTotalDirs" class="value">0</div>
                    <div class="label">مجلدات فرعية</div>
                </div>
                <div class="stats-card">
                    <div id="valTotalSize" class="value">0 KB</div>
                    <div class="label">الحجم الكلي</div>
                </div>
            </div>
        </div>

        <div class="main-grid">
            <div class="card">
                <div class="card-title">🌳 خريطة تصفح شجرة المجلدات والملفات</div>
                
                <div class="search-box">
                    <input type="text" id="searchInput" placeholder="ابحث بثوانٍ عن ملف، امتداد، أو مجلد...">
                </div>

                <div id="treeContainer">
                    <ul class="tree-root">
                        $renderedTreeHtml
                    </ul>
                </div>
            </div>

            <div class="info-panel">
                <div class="card">
                    <div class="card-title">🔍 تفاصيل الملف المحدد</div>
                    <div id="detailsCardContent">
                        <div class="empty-placeholder">
                            <span>📄</span>
                            اختر ملفًا حاليًا من الشجرة لاستعراض تفاصيله الوصفية، والامتداد الفني، وتاريخ التعديل هنا فورياً.
                        </div>
                    </div>
                </div>

                <div class="card">
                    <div class="card-title">📊 توزيع الملفات حسب النوع</div>
                    <div class="chart-box">
                        <canvas id="extensionChart" width="220" height="220" style="max-width: 220px; margin: 0 auto;"></canvas>
                        <div class="chart-legend" id="chartLegend"></div>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <script>
        const rawFiles = $jsonFilesData;
        
        function calculateStats() {
            let filesCount = 0;
            let dirsCount = 0;
            let totalBytes = 0;
            
            rawFiles.forEach(f => {
                if(f.type === 'file') {
                    filesCount++;
                    let b = 0;
                    if (f.size) {
                        let matches = f.size.match(/([\d\.]+)\s*(B|KB|MB|GB)/i);
                        if (matches) {
                            let val = parseFloat(matches[1]);
                            let unit = matches[2].toUpperCase();
                            if(unit === 'KB') val *= 1024;
                            else if(unit === 'MB') val *= 1024 * 1024;
                            else if(unit === 'GB') val *= 1024 * 1024 * 1024;
                            b = val;
                        }
                    }
                    totalBytes += b;
                } else if(f.type === 'directory') {
                    dirsCount++;
                }
            });
            
            document.getElementById('valTotalFiles').innerText = filesCount;
            document.getElementById('valTotalDirs').innerText = dirsCount;
            
            let sizeStr = "0 B";
            if (totalBytes < 1024) sizeStr = totalBytes.toFixed(0) + " B";
            else if (totalBytes < 1024 * 1024) sizeStr = (totalBytes/1024).toFixed(1) + " KB";
            else sizeStr = (totalBytes/(1024*1024)).toFixed(1) + " MB";
            document.getElementById('valTotalSize').innerText = sizeStr;
        }
        
        function selectFile(element, name, path, size, mtime, ext) {
            document.querySelectorAll('.file-item').forEach(item => item.classList.remove('active'));
            element.classList.add('active');
            
            const detailedContent = `
                <div class="info-row"><span class="label">الاسم:</span><span class="value" style="color: #ffd700;">${'$'}{name}</span></div>
                <div class="info-row"><span class="label">المسار:</span><span class="value" style="font-family:monospace; font-size:11px; direction:ltr; text-align:right;">${'$'}{path}</span></div>
                <div class="info-row"><span class="label">الحجم الحالي:</span><span class="value">${'$'}{size || 'غير متوفر'}</span></div>
                <div class="info-row"><span class="label">تحديث الأخير:</span><span class="value">${'$'}{mtime || 'غير متوفر'}</span></div>
                <div class="info-row"><span class="label">الامتداد:</span><span class="value" style="font-family:monospace; background:rgba(212,175,55,0.1); padding:2px 6px; border-radius:4px; color:#ffd700;">.${'$'}{ext || 'لاشيء'}</span></div>
            `;
            document.getElementById('detailsCardContent').innerHTML = detailedContent;
        }

        document.getElementById('searchInput').addEventListener('input', function(e) {
            let q = e.target.value.toLowerCase().trim();
            let fileElements = document.querySelectorAll('.file-item');
            let detailsElements = document.querySelectorAll('details');
            
            if(q === '') {
                fileElements.forEach(f => f.classList.remove('hidden'));
                detailsElements.forEach(d => {
                    d.classList.remove('hidden');
                    d.removeAttribute('open');
                });
                return;
            }
            
            fileElements.forEach(f => {
                let text = f.innerText.toLowerCase();
                if(text.includes(q)) {
                    f.classList.remove('hidden');
                    let parent = f.closest('details');
                    while(parent) {
                        parent.setAttribute('open', 'true');
                        parent.classList.remove('hidden');
                        parent = parent.parentElement.closest('details');
                    }
                } else {
                    f.classList.add('hidden');
                }
            });
            
            detailsElements.forEach(d => {
                let summaryText = d.querySelector('summary').innerText.toLowerCase();
                let hasVisibleChildren = Array.from(d.querySelectorAll('.file-item')).some(f => !f.classList.contains('hidden'));
                
                if(summaryText.includes(q) || hasVisibleChildren) {
                    d.classList.remove('hidden');
                } else {
                    d.classList.add('hidden');
                }
            });
        });

        function drawExtensionChart() {
            let extensions = {};
            rawFiles.forEach(f => {
                if(f.type === 'file') {
                    let ext = f.ext || 'other';
                    extensions[ext] = (extensions[ext] || 0) + 1;
                }
            });
            
            let sorted = Object.entries(extensions).sort((a,b) => b[1] - a[1]);
            let limit = 4;
            let finalExts = [];
            let otherSum = 0;
            
            sorted.forEach((item, index) => {
                if(index < limit) {
                    finalExts.push({ label: item[0], count: item[1] });
                } else {
                    otherSum += item[1];
                }
            });
            if(otherSum > 0) {
                finalExts.push({ label: 'أخرى', count: otherSum });
            }
            
            const total = finalExts.reduce((acc, curr) => acc + curr.count, 0);
            if(total === 0) {
                document.getElementById('extensionChart').style.display = 'none';
                document.getElementById('chartLegend').innerHTML = '<span style="color:#a0aec0;">لا توجد ملفات لعرضها</span>';
                return;
            }
            
            const colors = ['#D4AF37', '#FFD700', '#F97316', '#38BDF8', '#A3E635', '#A0AEC0'];
            let canvas = document.getElementById('extensionChart');
            let ctx = canvas.getContext('2d');
            let centerX = canvas.width / 2;
            let centerY = canvas.height / 2;
            let radius = Math.min(centerX, centerY) - 10;
            
            let startAngle = 0;
            let legendHtml = "";
            
            finalExts.forEach((item, i) => {
                let sliceAngle = (item.count / total) * 2 * Math.PI;
                let color = colors[i % colors.length];
                
                ctx.beginPath();
                ctx.arc(centerX, centerY, radius, startAngle, startAngle + sliceAngle);
                ctx.lineTo(centerX, centerY);
                ctx.fillStyle = color;
                ctx.fill();
                
                ctx.beginPath();
                ctx.arc(centerX, centerY, radius * 0.5, 0, 2 * Math.PI);
                ctx.fillStyle = '#111827';
                ctx.fill();
                
                startAngle += sliceAngle;
                
                let percent = ((item.count / total) * 100).toFixed(0);
                legendHtml += `
                    <div class="legend-item">
                        <div class="legend-color" style="background:${'$'}{color}"></div>
                        <span>.${'$'}{item.label} (${'$'}{percent}%)</span>
                    </div>
                `;
            });
            
            document.getElementById('chartLegend').innerHTML = legendHtml;
        }

        window.addEventListener('DOMContentLoaded', () => {
            calculateStats();
            drawExtensionChart();
        });
    </script>
</body>
</html>
"""
    }
}
