package com.example

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import androidx.core.content.ContextCompat
import com.example.data.DateUtils
import com.example.data.LensDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LensWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (appWidgetId in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, appWidgetId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_UPDATE_WIDGET) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val thisWidget = ComponentName(context, LensWidgetProvider::class.java)
                    val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
                    for (appWidgetId in appWidgetIds) {
                        updateAppWidget(context, appWidgetManager, appWidgetId)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        const val ACTION_UPDATE_WIDGET = "com.example.ACTION_UPDATE_WIDGET"

        fun triggerUpdate(context: Context) {
            val intent = Intent(context, LensWidgetProvider::class.java).apply {
                action = ACTION_UPDATE_WIDGET
            }
            context.sendBroadcast(intent)
        }

        private suspend fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.lens_widget)

            // Intent to launch MainActivity when clicking the widget
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_main_container, pendingIntent)

            // Retrieve transparency preference and apply
            val sharedPrefs = context.getSharedPreferences("lens_notifications", Context.MODE_PRIVATE)
            val isTransparent = sharedPrefs.getBoolean("widget_transparent", false)
            if (isTransparent) {
                views.setInt(R.id.widget_main_container, "setBackgroundResource", R.drawable.widget_background_transparent)
            } else {
                views.setInt(R.id.widget_main_container, "setBackgroundResource", R.drawable.widget_background)
            }

            // Set rounded app icon image
            try {
                val drawable = ContextCompat.getDrawable(context, R.drawable.app_icon_downloaded)
                if (drawable != null) {
                    val bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null) {
                        drawable.bitmap
                    } else {
                        val b = Bitmap.createBitmap(
                            drawable.intrinsicWidth.coerceAtLeast(100),
                            drawable.intrinsicHeight.coerceAtLeast(100),
                            Bitmap.Config.ARGB_8888
                        )
                        val c = Canvas(b)
                        drawable.setBounds(0, 0, c.width, c.height)
                        drawable.draw(c)
                        b
                    }
                    val cornerRadius = 14f * context.resources.displayMetrics.density
                    val roundedBitmap = getRoundedCornerBitmap(bitmap, cornerRadius)
                    views.setImageViewBitmap(R.id.widget_app_icon, roundedBitmap)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
                val db = LensDatabase.getDatabase(context)
                val dao = db.lensDao()
                
                val activeLens = dao.getActiveLensWearSync()
                val stocks = dao.getAllLensStock().first()

                // Calculate total pairs in stock
                var totalPairs = 0
                stocks.forEach { stock ->
                    totalPairs += (stock.boxCount * stock.pairsPerBox) + stock.pairsInOpenBox
                }
                val pairsText = getPairsPlural(totalPairs)

                val infoText = if (activeLens != null) {
                    val skippedSet = activeLens.skippedDates.split(",")
                        .filter { it.isNotEmpty() }
                        .toSet()
                    val allDates = DateUtils.getDaysBetween(activeLens.startDate, System.currentTimeMillis())
                    val skippedCount = allDates.count { it in skippedSet }
                    val daysElapsed = DateUtils.getDaysElapsed(activeLens.startDate, System.currentTimeMillis())
                    val daysWorn = (daysElapsed - skippedCount).coerceAtLeast(0)
                    val isOverdue = daysWorn > activeLens.durationDays
                    val remainingDays = activeLens.durationDays - daysWorn

                    when {
                        isOverdue -> "смена просрочена, в\u00A0запасе $pairsText"
                        remainingDays == 0 -> "сегодня смена пары, в\u00A0запасе $pairsText"
                        remainingDays == 1 -> "последний день, в\u00A0запасе $pairsText"
                        else -> "осталось ${getDaysPlural(remainingDays)}, в\u00A0запасе $pairsText"
                    }
                } else {
                    "нет активных линз, в\u00A0запасе $pairsText"
                }

                views.setTextViewText(R.id.widget_info, infoText)
            } catch (e: Exception) {
                e.printStackTrace()
                views.setTextViewText(R.id.widget_info, "ошибка загрузки")
            } finally {
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }

        private fun getDaysPlural(days: Int): String {
            val mod10 = days % 10
            val mod100 = days % 100
            return when {
                mod100 in 11..14 -> "$days дней"
                mod10 == 1 -> "$days день"
                mod10 in 2..4 -> "$days дня"
                else -> "$days дней"
            }
        }

        private fun getPairsPlural(pairs: Int): String {
            val mod10 = pairs % 10
            val mod100 = pairs % 100
            val suffix = when {
                mod100 in 11..14 -> "пар"
                mod10 == 1 -> "пара"
                mod10 in 2..4 -> "пары"
                else -> "пар"
            }
            return "$pairs $suffix"
        }

        private fun getRoundedCornerBitmap(bitmap: Bitmap, cornerRadiusPx: Float): Bitmap {
            val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint().apply {
                isAntiAlias = true
            }
            val rect = Rect(0, 0, bitmap.width, bitmap.height)
            val rectF = RectF(rect)

            canvas.drawARGB(0, 0, 0, 0)
            paint.color = -0x1
            canvas.drawRoundRect(rectF, cornerRadiusPx, cornerRadiusPx, paint)

            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(bitmap, rect, rect, paint)

            return output
        }
    }
}
