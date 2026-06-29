package com.example.service

import com.example.engine.LargeTextProcessor

class QuickSmartCaptureActivity : BaseTextActionActivity() {
    override fun handleText(text: String) {
        LargeTextProcessor.processLargeText(this, text, "smart_capture")
    }
}
