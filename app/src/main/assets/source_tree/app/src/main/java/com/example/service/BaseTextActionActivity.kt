package com.example.service

import android.app.Activity
import android.content.Intent
import android.os.Bundle

abstract class BaseTextActionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            ?: intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT)
            ?: if (intent.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT) else null
            ?: ""
        
        if (text.isNotEmpty()) {
            handleText(text)
        }
        finish()
    }

    abstract fun handleText(text: String)
}
