package com.example.service

import android.app.Activity
import android.content.Intent
import android.os.Bundle

abstract class BaseTextActionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        var text = ""
        val rawText = if (intent.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)
        } else {
            intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
                ?: intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT)
        }
        
        if (rawText != null && rawText.isNotEmpty()) {
            text = rawText
        } else {
            val uri = intent.data
            if (uri != null) {
                try {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        text = inputStream.bufferedReader().use { it.readText() }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        
        if (text.isNotEmpty()) {
            handleText(text)
        }
        finish()
    }

    abstract fun handleText(text: String)
}
