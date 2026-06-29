package com.example.service

import com.example.engine.LargeTextProcessor

class QuickConvertBeautifyActivity : BaseTextActionActivity() {
    override fun handleText(text: String) {
        LargeTextProcessor.processLargeText(this, text, "convert_beautify")
    }
}
