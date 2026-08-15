package com.example.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateUtils {
    private val dbDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayDateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

    fun getTodayString(): String {
        return dbDateFormat.format(Date())
    }

    fun formatDateToDisplay(timeInMillis: Long): String {
        return displayDateFormat.format(Date(timeInMillis))
    }

    fun formatDateToDb(timeInMillis: Long): String {
        return dbDateFormat.format(Date(timeInMillis))
    }

    fun parseDbDate(dateStr: String): Long {
        return try {
            dbDateFormat.parse(dateStr)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Returns a list of all date strings ("yyyy-MM-dd") from start timestamp to end timestamp (inclusive).
     */
    fun getDaysBetween(startMs: Long, endMs: Long): List<String> {
        val dates = mutableListOf<String>()
        val startCal = Calendar.getInstance().apply {
            timeInMillis = startMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCal = Calendar.getInstance().apply {
            timeInMillis = endMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (startCal.after(endCal)) {
            // Just add today as fallback if something is weird
            dates.add(dbDateFormat.format(startCal.time))
            return dates
        }

        while (!startCal.after(endCal)) {
            dates.add(dbDateFormat.format(startCal.time))
            startCal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return dates
    }

    /**
     * Returns the count of full calendar days elapsed between startMs and endMs.
     * E.g. start = July 19, end = August 1 -> 13 days.
     * start = July 19, end = July 19 -> 0 days.
     */
    fun getDaysElapsed(startMs: Long, endMs: Long): Int {
        val startCal = Calendar.getInstance().apply {
            timeInMillis = startMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCal = Calendar.getInstance().apply {
            timeInMillis = endMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (!startCal.before(endCal)) return 0

        var days = 0
        while (startCal.before(endCal)) {
            days++
            startCal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return days
    }

    fun getChangeDate(startMs: Long, durationDays: Int, skippedDaysCount: Int): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = startMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, durationDays + skippedDaysCount)
        }
        return cal.timeInMillis
    }

    fun addTenMonths(timeInMillis: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timeInMillis
        cal.add(Calendar.MONTH, 10)
        return cal.timeInMillis
    }

    fun addOneYear(timeInMillis: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timeInMillis
        cal.add(Calendar.YEAR, 1)
        return cal.timeInMillis
    }

    fun getWeeksPassed(startMs: Long, endMs: Long): Int {
        val diffMs = endMs - startMs
        if (diffMs <= 0) return 0
        val diffDays = diffMs / (1000 * 60 * 60 * 24)
        return (diffDays / 7).toInt()
    }
}
