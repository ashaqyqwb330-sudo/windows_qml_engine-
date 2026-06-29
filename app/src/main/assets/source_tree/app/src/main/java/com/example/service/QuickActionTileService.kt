package com.example.service

import android.content.Intent
import android.service.quicksettings.TileService

class QuickActionTileService : TileService() {

    override fun onClick() {
        super.onClick()
        try {
            val intent = Intent(this, TileActionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivityAndCollapse(intent)
        } catch (e: Exception) {
            android.util.Log.e("QuickActionTileService", "Error launching TileActionActivity", e)
        }
    }
}
