package com.example.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class UpdateService : Service() {

    companion object {
        private const val TAG = "UpdateService"
        private const val CHANNEL_ID = "update_channel"
        private const val NOTIFICATION_ID = 999
        private const val DEFAULT_URL = "https://raw.githubusercontent.com/your-repo/releases/main/update_config.json"

        fun scheduleDailyUpdateCheck(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, UpdateService::class.java)
            val pendingIntent = PendingIntent.getService(
                context,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val interval = 24 * 60 * 60 * 1000L // 24 hours
            val triggerAt = System.currentTimeMillis() + 10 * 1000L // Check after 10 seconds initially
            
            try {
                alarmManager.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    interval,
                    pendingIntent
                )
                Log.d(TAG, "Scheduled daily update check.")
            } catch (e: Exception) {
                Log.e(TAG, "Error scheduling check: ${e.message}")
            }
        }

        fun cancelDailyUpdateCheck(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, UpdateService::class.java)
            val pendingIntent = PendingIntent.getService(
                context,
                1001,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
                Log.d(TAG, "Cancelled scheduled update checks.")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Checking for updates...")
        
        val sharedPrefs = getSharedPreferences("SmartPrefs", Context.MODE_PRIVATE)
        val updateUrl = sharedPrefs.getString("update_config_url", DEFAULT_URL) ?: DEFAULT_URL

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val urlObj = URL(updateUrl)
                val conn = urlObj.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.connect()

                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val streamReader = conn.inputStream.bufferedReader()
                    val response = streamReader.use { it.readText() }
                    conn.disconnect()

                    val json = JSONObject(response)
                    val serverVersionCode = json.optInt("versionCode", 0)
                    val serverVersionName = json.optString("versionName", "1.0")
                    val downloadUrl = json.optString("downloadUrl", "")

                    Log.d(TAG, "Server version: $serverVersionCode, Current version: ${BuildConfig.VERSION_CODE}")

                    if (serverVersionCode > BuildConfig.VERSION_CODE && downloadUrl.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            showUpdateNotification(serverVersionName, downloadUrl)
                        }
                    }
                } else {
                    Log.e(TAG, "HTTP error: ${conn.responseCode}")
                    conn.disconnect()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check update: ${e.message}")
            } finally {
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun showUpdateNotification(versionName: String, downloadUrl: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "تحديثات النظام"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = "تنبيهات عند توفر إصدارات جديدة للتطبيق"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            browserIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("🚀 تحديث جديد متاح!")
            .setContentText("إصدار $versionName متاح للتحميل الآن! اضغط للتحميل.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
