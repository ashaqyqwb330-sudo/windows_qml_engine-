package com.example.service

import com.example.engine.LargeTextProcessor

class QuickExecuteActivity : BaseTextActionActivity() {
    override fun handleText(text: String) {
        LargeTextProcessor.processLargeText(this, text, "execute_commands")
    }
}
