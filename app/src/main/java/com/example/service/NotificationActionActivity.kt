package com.example.service

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import com.example.engine.LargeTextProcessor

class NotificationActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val actionType = intent?.getStringExtra("action_type") ?: "smart_capture"
        
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = if (clipboard.hasPrimaryClip()) {
            clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
        } else {
            ""
        }
        
        if (text.isBlank()) {
            Toast.makeText(this, "⚠️ الحافظة فارغة! لا يوجد نص لمعالجته.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        
        // Execute the action using LargeTextProcessor (which delegates to UniversalActionHandler and handles large texts too!)
        LargeTextProcessor.processLargeText(this, text, actionType)
        
        finish()
    }
}
