package com.example.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = LensDatabase.getDatabase(context)
                val dao = database.lensDao()
                val activeLens = dao.getActiveLensWearSync()
                if (activeLens != null) {
                    val skippedSet = activeLens.skippedDates.split(",")
                        .filter { it.isNotEmpty() }
                        .toSet()

                    val allDates = DateUtils.getDaysBetween(activeLens.startDate, System.currentTimeMillis())
                    val skippedCount = allDates.count { it in skippedSet }

                    val daysElapsed = DateUtils.getDaysElapsed(activeLens.startDate, System.currentTimeMillis())
                    val daysWorn = (daysElapsed - skippedCount).coerceAtLeast(0)

                    val isOverdue = daysWorn > activeLens.durationDays
                    val remainingDays = if (isOverdue) 0 else activeLens.durationDays - daysWorn

                    val messageText = when {
                        isOverdue -> "Смена линз просрочена!"
                        remainingDays == 0 -> "Сегодня меняем линзы!"
                        remainingDays == 1 -> "Завтра меняем линзы!"
                        else -> null
                    }

                    if (messageText != null) {
                        sendNotification(context, messageText)
                    }
                }
                // Always refresh the widget daily when the alarm is triggered
                com.example.LensWidgetProvider.triggerUpdate(context)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun sendNotification(context: Context, message: String) {
        val channelId = "lens_tracker_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Напоминания о замене линз"
            val desc = "Уведомления о времени замены контактных линз"
            val channel = android.app.NotificationChannel(channelId, name, android.app.NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = desc
            }
            notificationManager.createNotificationChannel(channel)
        }

        val mainIntent = Intent(context, com.example.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            0,
            mainIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        // Find standard app icon or a fallback
        val iconRes = android.R.drawable.ic_dialog_info

        val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(iconRes)
            .setContentTitle("Замена контактных линз")
            .setContentText(message)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(4242, builder.build())
    }
}
