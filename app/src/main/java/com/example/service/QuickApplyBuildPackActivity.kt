package com.example.service

import com.example.engine.LargeTextProcessor

class QuickApplyBuildPackActivity : BaseTextActionActivity() {
    override fun handleText(text: String) {
        LargeTextProcessor.processLargeText(this, text, "apply_build_pack")
    }
}
