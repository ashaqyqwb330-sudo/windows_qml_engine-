package com.example.service

import com.example.engine.LargeTextProcessor

class QuickBuildPackActivity : BaseTextActionActivity() {
    override fun handleText(text: String) {
        LargeTextProcessor.processLargeText(this, text, "build_pack")
    }
}
