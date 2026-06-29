package com.example.service

import com.example.engine.LargeTextProcessor

class QuickAutoDetectActivity : BaseTextActionActivity() {
    override fun handleText(text: String) {
        LargeTextProcessor.processLargeText(this, text, "auto_detect")
    }
}
